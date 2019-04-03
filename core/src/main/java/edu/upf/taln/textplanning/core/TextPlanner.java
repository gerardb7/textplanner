package edu.upf.taln.textplanning.core;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.discourse.DiscoursePlanner;
import edu.upf.taln.textplanning.core.extraction.*;
import edu.upf.taln.textplanning.core.io.GraphSemantics;
import edu.upf.taln.textplanning.core.ranking.Ranker;
import edu.upf.taln.textplanning.core.redundancy.RedundancyRemover;
import edu.upf.taln.textplanning.core.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;


/**
 * Text planner class. Given a set of references to entities and a set of documents, generates a text plan
 * containing contents in the documents relevant to the references.
 * Immutable class.
 */
public final class TextPlanner
{
	private final static Logger log = LogManager.getLogger();

	/**
	 * Generates a text plan from a weighted semantic graph
	 */
	public static List<SemanticSubgraph> plan(SemanticGraph graph, GraphSemantics semantics,
	                                          BiFunction<String, String, OptionalDouble> similarity, int num_graphs, Options o)
	{
		try
		{
			log.info("*Planning started*");
			Stopwatch timer = Stopwatch.createStarted();

			// 1- Extract subgraphs from graph
			Collection<SemanticSubgraph> subgraphs = extractSubgraphs(graph, semantics, num_graphs, o);

			// 2- Remove redundant subgraphs
			subgraphs = removeRedundantSubgraphs(subgraphs, num_graphs, similarity, o);

			// 3- Sort the trees into a discourse-optimized list
			final List<SemanticSubgraph> text_plan = sortSubgraphs(subgraphs, similarity, o);

			log.info("Planning took " + timer.stop());
			return text_plan;
		}
		catch (Exception e)
		{
			log.error("Planning failed");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Ranks set of candidate meanings
	 */
	public static void rankMeanings(List<Candidate> candidates, Predicate<Candidate> candidates_filter,
	                                BiPredicate<String, String> meanings_filter, Function<String, Double> weighting,
	                                BiFunction<String, String, OptionalDouble> similarity, Options o)
	{
		log.info("*Ranking meanings*");
		Stopwatch timer = Stopwatch.createStarted();

		final List<Candidate> filtered_candidates = candidates.stream()
				.filter(candidates_filter)
				.collect(toList());
		final List<String> references = filtered_candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());
		final List<String> labels = filtered_candidates.stream() // for debugging purposes
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.distinct()
				.collect(toList());

		if (references.isEmpty())
			return;

		double[] ranking = Ranker.rank(references, labels, weighting, similarity, true, meanings_filter, o.sim_threshold, o.damping_meanings);

		// Assign ranking values to meanings
		filtered_candidates.forEach(m ->
		{
			int i = references.indexOf(m.getMeaning().getReference());
			m.setWeight(ranking[i]);
		});

		log.info("Ranking completed in " + timer.stop());
	}


	/**
	 * 	Ranks mentions according to their initial weights and an adjacency function
	 */
	public static void rankMentions(List<Candidate> candidates, BiPredicate<Mention, Mention> adjacency_function,
	                                Options o)
	{
		log.info("*Ranking mentions*");
		Stopwatch timer = Stopwatch.createStarted();

		if (!candidates.isEmpty())
		{
			final Map<Mention, Candidate> mentions2meanings = candidates.stream()
				.collect(toMap(Candidate::getMention, c -> c, (c1, c2) ->
				{
					log.error("Duplicate candidates for mention " + c1.getMention().getId());
					return c1;
				}));

			// One variable per mention
			final Map<String, Mention> variables2mentions = mentions2meanings.keySet().stream()
					.collect(toMap(Mention::getId, m -> m));
			final Map<String, Double> variables2weights = mentions2meanings.keySet().stream()
					.collect(toMap(Mention::getId, m -> mentions2meanings.get(m).getWeight()));
			List<String> variables = List.copyOf(variables2mentions.keySet());

			final List<String> labels = variables.stream() // for debugging purposes
					.map(v ->
					{
						final Mention mention = variables2mentions.get(v);
						final Meaning meaning = mentions2meanings.get(mention).getMeaning();
						return DebugUtils.createLabelForVariable(v, meaning, List.of(mention));
					})
					.collect(Collectors.toList());


			BiFunction<String, String, OptionalDouble> similarity =
					(v1, v2) ->
					{
						final Mention m1 = variables2mentions.get(v1);
						final Mention m2 = variables2mentions.get(v2);
						return OptionalDouble.of(adjacency_function.test(m1, m2) || adjacency_function.test(m2, m1) ? 1.0 : 0.0);
					};
			double[] ranking = Ranker.rank(variables, labels, variables2weights::get, similarity, false,
					(v1, v2) -> true, 0.0, o.damping_variables);

			IntStream.range(0, variables.size()).boxed()
					.forEach(i -> mentions2meanings.get(variables2mentions.get(variables.get(i))).setWeight(ranking[i]));
		}

		log.info("Ranking completed in " + timer.stop());
	}

	/**
	 * 	Extract subgraphs from a semantic graph
	 */
	public static Collection<SemanticSubgraph> extractSubgraphs(SemanticGraph graph, GraphSemantics semantics,
	                                                            int num_graphs, Options o)
	{
		log.info("*Extracting subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();

		Explorer e = new RequirementsExplorer(semantics, true, Explorer.ExpansionPolicy.Non_core_only);
		Policy p = new SoftMaxPolicy();
		SubgraphExtraction extractor = new SubgraphExtraction(e, p, Math.min(num_graphs, o.extraction_lambda));
		Collection<SemanticSubgraph> subgraphs = extractor.multipleExtraction(graph, o.num_subgraphs);
		log.info("Extraction done in " + timer.stop());

		return subgraphs;
	}

	/**
	 * 	Remove redundant subgraphs
	 */
	public static Collection<SemanticSubgraph> removeRedundantSubgraphs(Collection<SemanticSubgraph> subgraphs, int num_graphs,
	                                                                    BiFunction<String, String, OptionalDouble> similarity,
	                                                                    Options o)
	{
		log.info("*Removing redundant subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();
		SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.tree_edit_lambda);
		RedundancyRemover remover = new RedundancyRemover(tsim);
		Collection<SemanticSubgraph> out_subgraphs = remover.filter(subgraphs, num_graphs);
		log.info("Redundancy removal done in " + timer.stop());

		return out_subgraphs;
	}

	/**
	 * 	Sort subgraphs
	 */
	public static List<SemanticSubgraph> sortSubgraphs( Collection<SemanticSubgraph> subgraphs,
	                                                    BiFunction<String, String, OptionalDouble> similarity,
	                                                    Options o)
	{
		log.info("*Sorting subgraphs*");
		Stopwatch timer = Stopwatch.createStarted();
		SemanticTreeSimilarity tsim = new SemanticTreeSimilarity(similarity, o.tree_edit_lambda);
		DiscoursePlanner discourse = new DiscoursePlanner(tsim);
		List<SemanticSubgraph> text_plan = discourse.structureSubgraphs(subgraphs);
		log.info("Sorting done in " + timer.stop());

		return text_plan;
	}
}
