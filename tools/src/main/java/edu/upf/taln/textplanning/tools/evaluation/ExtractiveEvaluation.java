package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.adverb_pos_tag;
import static java.util.stream.Collectors.joining;

public class ExtractiveEvaluation
{
	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String gold_suffix = ".gold";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_folder, Path input_folder, Path output_path, InitialResourcesFactory resources_factory)
	{
		final Options options = new Options();
		options.excluded_POS_Tags = Set.of(EvaluationTools.other_pos_tag, adverb_pos_tag);

		// load corpus
		final Corpus corpus = EvaluationTools.loadResourcesFromText(input_folder, output_path, resources_factory, language, max_span_size,
				options);

		// rank
		EvaluationTools.rankMeanings(options, corpus, resources_factory);
		EvaluationTools.disambiguate(corpus);
		EvaluationTools.rankMentions(options, corpus);

		// create summary
		final String summary = corpus.graph.vertexSet().stream()
				.sorted(Comparator.comparingDouble(corpus.graph::getWeight).reversed())
				.map(corpus.graph::getMentions)
				.flatMap(Collection::stream)
				.map(Mention::getSurface_form)
				.collect(joining(" "));
		log.info(summary);
	}
}

