package edu.upf.taln.textplanning.core;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.corpus.*;
import edu.upf.taln.textplanning.core.discourse.DiscoursePlanner;
import edu.upf.taln.textplanning.core.extraction.*;
import edu.upf.taln.textplanning.core.ranking.MatrixFactory;
import edu.upf.taln.textplanning.core.ranking.Ranker;
import edu.upf.taln.textplanning.core.redundancy.RedundancyRemover;
import edu.upf.taln.textplanning.core.resources.DocumentResourcesFactory;
import edu.upf.taln.textplanning.core.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

public class Planner
{
	private final static Logger log = LogManager.getLogger();

	public static void rankMentions(Corpora.Corpus corpus, boolean use_dependencies, int context_size, Options options)
	{
		corpus.texts.forEach(text -> rankMentions(text, use_dependencies, context_size, options));
	}

	public static void rankMentions(Corpora.Text text, boolean use_dependencies, int context_size, Options options)
	{
		final AdjacencyFunction adjacency = use_dependencies ? new DependencyBasedAdjacencyFunction(text)
				: new TextualOrderAdjacencyFunction(text, context_size);
		final List<Mention> word_mentions = adjacency.getSortedWordMentions();
		final SameMeaningPredicate same_meaning = new SameMeaningPredicate(text);
		final Map<Mention, Candidate> word_meanings = same_meaning.getWordMeanings();

		final Map<Mention, Optional<Double>> words2weights = word_mentions.stream()
				.collect(toMap(m -> m, m -> word_meanings.containsKey(m) ? word_meanings.get(m).getWeight() : Optional.empty()));

		rankMentions(words2weights, (m1, m2) -> adjacency.test(m1, m2) || adjacency.test(m2, m1), same_meaning, options);
	}

	public static void plan(Corpora.Corpus corpus, DocumentResourcesFactory resources, boolean use_dependencies, int context_size, Options options)
	{
		corpus.texts.forEach(text -> plan(text, resources, use_dependencies, context_size, options));
	}

	public static void plan(Corpora.Text text, DocumentResourcesFactory resources, boolean use_dependencies, int context_size, Options options)
	{
		final AdjacencyFunction adjacency = use_dependencies ? new DependencyBasedAdjacencyFunction(text)
				: new TextualOrderAdjacencyFunction(text, context_size);
		Corpora.createGraph(text, adjacency);
		final List<SemanticSubgraph> subgraphs = extractSubgraphs(text.graph, options);

		final SimilarityFunction sim = resources.getSimilarityFunction();
		final List<SemanticSubgraph> selected_subgraphs = removeRedundantSubgraphs(subgraphs, sim, options);
		text.subgraphs.addAll(sortSubgraphs(selected_subgraphs, sim, options));
	}

	/**
	 * 	Ranks mentions according to their initial bias and an adjacency function
	 */
	private static void rankMentions(Map<Mention, Optional<Double>> mentions,
	                                BiPredicate<Mention, Mention> are_connected,
	                                BiPredicate<Mention, Mention> same_meaning, Options o)
	{
		log.info("*Ranking mentions*");
		Stopwatch timer = Stopwatch.createStarted();

		if (mentions.isEmpty())
			return;

		// One variable per mention
		final Map<String, Mention> variables2mentions = mentions.keySet().stream()
				.collect(toMap(Mention::toString, m -> m));

		// Use average bias for mentions without a bias value
		final double avg_bias = mentions.values().stream()
				.flatMap(Optional::stream)
				.mapToDouble(d -> d)
				.average().orElse(0.0);

		final Map<String, Double> variables2bias = mentions.keySet().stream()
				.collect(toMap(Mention::toString, m -> mentions.get(m).orElse(avg_bias)));

		List<String> variables = List.copyOf(variables2mentions.keySet());
		BiFunction<String, String, OptionalDouble> edge_weights =
				(v1, v2) ->
				{
					final Mention m1 = variables2mentions.get(v1);
					final Mention m2 = variables2mentions.get(v2);
					// establish links between pairs with a connection or sharing same meaning
					return OptionalDouble.of(are_connected.test(m1, m2) || same_meaning.test(m1, m2) ? 1.0 : 0.0);
				};

		final double[] ranking = Ranker.rank(variables, variables2bias::get, edge_weights, true,
				(v1, v2) -> true, 0.0, o.damping_variables, o.stopping_threshold);
		final double[] rebased_ranking = MatrixFactory.rebase(ranking);

		IntStream.range(0, variables.size()).boxed()
				.forEach(i -> variables2mentions.get(variables.get(i)).setWeight(rebased_ranking[i]));
		log.info("Ranking completed in " + timer.stop() + "\n");

		// for debugging purposes
		final List<String> labels = variables.stream()
				.map(v ->
				{
					final Mention mention = variables2mentions.get(v);
					return DebugUtils.createLabelForVariable(v, null, List.of(mention));
				})
				.collect(Collectors.toList());
		final double[] rebased_bias = variables.stream().mapToDouble(variables2bias::get).toArray();
		log.debug("Ranking of mentions:\n" + DebugUtils.printRank(rebased_ranking, variables.size(), rebased_bias, labels));

	}

	/**
	 * 	Extract subgraphs from a semantic graph
	 */
	private static List<SemanticSubgraph> extractSubgraphs(SemanticGraph graph, GraphSemantics semantics, Options o)
	{
		log.info("*Extracting subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();

		Explorer explorer = new RequirementsExplorer(semantics, false, Explorer.ExpansionConstraints.Non_core_only);
		Policy start_policy = o.start_policy == Policy.Type.ArgMax ? new ArgMaxPolicy() : new SoftMaxPolicy(o.softmax_temperature);
		Policy expand_policy = o.start_policy == Policy.Type.ArgMax ? new ArgMaxPolicy() : new SoftMaxPolicy(o.softmax_temperature);
		SubgraphExtraction extractor = new SubgraphExtraction(explorer, start_policy, expand_policy, Math.min(o.num_subgraphs_extract, o.extraction_lambda));
		List<SemanticSubgraph> subgraphs = extractor.multipleExtraction(graph, o.num_subgraphs_extract);
		log.info("Extraction done in " + timer.stop() + "\n");

		return subgraphs;
	}

	/**
	 * 	Extract subgraphs from a semantic graph
	 */
	private static List<SemanticSubgraph> extractSubgraphs(SemanticGraph graph, Options o)
	{
		log.info("*Extracting subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();

		Explorer explorer = new SingleVertexExplorer(o.start_from_verbs, o.expansion_constraints);
		Policy start_policy = o.start_policy == Policy.Type.ArgMax ? new ArgMaxPolicy() : new SoftMaxPolicy(o.softmax_temperature);
		Policy expand_policy = o.start_policy == Policy.Type.ArgMax ? new ArgMaxPolicy() : new SoftMaxPolicy(o.softmax_temperature);
		SubgraphExtraction extractor = new SubgraphExtraction(explorer, start_policy, expand_policy, o.extraction_lambda);
		List<SemanticSubgraph> subgraphs = extractor.multipleExtraction(graph, o.num_subgraphs_extract);
		log.info("Extraction done in " + timer.stop() + "\n");

		return subgraphs;
	}

	/**
	 * 	Remove redundant subgraphs
	 */
	private static List<SemanticSubgraph> removeRedundantSubgraphs(List<SemanticSubgraph> subgraphs,
	                                                              BiFunction<String, String, OptionalDouble> similarity,
	                                                              Options o)
	{
		log.info("*Removing redundant subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();
		SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.tree_edit_lambda);
		RedundancyRemover remover = new RedundancyRemover(tsim, o.redundancy_threshold);
		List<SemanticSubgraph> out_subgraphs = remover.filter(subgraphs);
		log.info("Redundancy removal done in " + timer.stop() + "\n");

		return out_subgraphs;
	}

	/**
	 * 	Sort subgraphs
	 */
	private static List<SemanticSubgraph> sortSubgraphs( Collection<SemanticSubgraph> subgraphs,
	                                                    BiFunction<String, String, OptionalDouble> similarity,
	                                                    Options o)
	{
		log.info("*Sorting subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();
		SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.tree_edit_lambda);
		DiscoursePlanner discourse = new DiscoursePlanner(tsim);
		List<SemanticSubgraph> text_plan = discourse.structureSubgraphs(subgraphs);
		log.info("Sorting done in " + timer.stop() + "\n");

		return text_plan;
	}
}
