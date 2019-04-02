package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.adverb_pos_tag;
import static java.util.stream.Collectors.*;

public class ExtractiveEvaluation
{
	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String gold_suffix = ".gold";
	private static final String other_relation = "other";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_folder, Path input_folder, Path output_path, InitialResourcesFactory resources_factory) throws Exception
	{
		final Options options = new Options();
		options.excluded_POS_Tags = Set.of(EvaluationTools.other_pos_tag, adverb_pos_tag);

		// load corpus
		final Corpus corpus = EvaluationTools.loadResourcesFromText(input_folder, output_path, resources_factory, language, max_span_size,
				options);

		// rank meanings
		EvaluationTools.rankMeanings(options, corpus, resources_factory);

		// disambiguate
		final List<Candidate> candidates = corpus.texts.stream()
				.flatMap(text -> text.sentences.stream())
				.flatMap(sentence -> sentence.candidates.values().stream().flatMap(List::stream))
				.collect(toList());
		Disambiguation.disambiguate(corpus.graph, candidates);

		// rank mentions
		TextPlanner.rankVertices(corpus.graph, options);

		final String summary = corpus.graph.vertexSet().stream()
				.sorted(Comparator.comparingDouble(corpus.graph::getWeight).reversed())
				.map(corpus.graph::getMentions)
				.flatMap(Collection::stream)
				.map(Mention::getSurface_form)
				.collect(joining(" "));
		log.info(summary);
	}
}

