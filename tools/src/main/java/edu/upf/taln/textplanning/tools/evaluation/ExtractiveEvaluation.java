package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiPredicate;

import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.adverb_pos_tag;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

public class ExtractiveEvaluation
{
	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String gold_suffix = ".gold";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_folder, Path input_folder, Path output_path, InitialResourcesFactory resources_factory) throws Exception
	{
		final Options options = new Options();
		options.excluded_POS_Tags = Set.of(EvaluationTools.other_pos_tag, adverb_pos_tag);
		final Corpus corpus  = EvaluationTools.loadResourcesFromText(input_folder, output_path, resources_factory, language, max_span_size,
				options);
		EvaluationTools.rankMeanings(options, corpus, resources_factory);


		SemanticGraph g = corpus.graph;
		g.vertexSet().forEach(v -> // given a vertex
			g.getMentions(v).forEach(mention -> // given one of its mentions
				g.getSources(v).forEach(source_id -> // get its context id
						corpus.texts.stream() // find the text matching the id
							.filter(text -> source_id.startsWith(text.id))
							.findFirst()
							.ifPresent(text -> text.sentences.stream() // fins the sentence matching the id
									.filter(sentence -> source_id.matches(sentence.id))
									.findFirst()
									.ifPresent(sentence -> sentence.candidates.get(mention).stream() // get max candidate
											.max(comparingDouble(Candidate::getWeight))
											.ifPresent(c -> {
												// assign meaning and weight to the vertex v in the graph
												g.setMeaning(v, c.getMeaning());
												g.setWeight(v, c.getWeight());
											}))))));

		final List<Mention> mentions = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream())
				.flatMap(sentence -> sentence.candidates.keySet().stream())
				.collect(toList());

		final Map<Mention, Double> mentions2weights = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream())
				.flatMap(sentence -> sentence.candidates.entrySet().stream()
						.map(m -> Pair.of(m.getKey(), m.getValue().stream()
									.mapToDouble(Candidate::getWeight)
									.max().orElse(0.0))))
				.collect(toMap(Pair::getLeft, Pair::getRight));

		BiPredicate<Mention, Mention> spans_over = (m1, m2) -> m1.getSpan().getLeft() <= m2.getSpan().getLeft() &&
				m1.getSpan().getRight() >= m2.getSpan().getRight();
		final Map<Mention, List<Mention>> mentions2subsumed = mentions.stream().collect(toMap(m1 -> m1, m1 -> mentions.stream()
				.filter(m2 -> m1 != m2)
				.filter(m2 -> spans_over.test(m1, m2))
				.collect(toList())));
		final Map<Mention, List<Mention>> mentions2subsumers = mentions.stream().collect(toMap(m1 -> m1, m1 -> mentions.stream()
				.filter(m2 -> m1 != m2)
				.filter(m2 -> spans_over.test(m2, m1))
				.collect(toList())));

		BiPredicate<Mention, Mention> weights_more = (m1, m2) -> mentions2weights.get(m1) > mentions2weights.get(m2);
		final List<Mention> subsumers = mentions.stream()
				.filter(m1 -> mentions2subsumed.get(m1).stream().allMatch(m2 -> weights_more.test(m1, m2)))
				.filter(m1 -> mentions2subsumers.get(m1).stream().noneMatch(m2 -> weights_more.test(m2, m1)))
				.collect(toList());
		}


//
//		{
//			log.info("********************************");
//			log.info("Testing random rank");
//			final List<List<Meaning>> system_meanings = randomRank(test_resources);
//			final double map = evaluate(system_meanings, goldMeanings);
//			log.info("MAP = " + DebugUtils.printDouble(map));
//		}
//
//		{
//			log.info("********************************");
//			log.info("Testing context rank");
//			final List<List<Meaning>> system_meanings = contextRank(test_resources, excludedPOSTags);
//			final double map = evaluate(system_meanings, goldMeanings);
//			log.info("MAP = " + DebugUtils.printDouble(map));
//		}
//		{
//			log.info("********************************");
//			log.info("Testing full rank");
//			final List<List<Meaning>> system_meanings = fullRank(options, test_resources, resources_factory, excludedPOSTags);
//			final double map = evaluate(system_meanings, goldMeanings);
//			log.info("MAP = " + DebugUtils.printDouble(map));
//		}

	}

