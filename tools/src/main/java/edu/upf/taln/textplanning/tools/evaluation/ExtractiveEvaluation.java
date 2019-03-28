package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.adverb_pos_tag;

public class ExtractiveEvaluation
{
	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String gold_suffix = ".gold";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_folder, Path input_folder, Path output_path, ResourcesFactory resources_factory) throws Exception
	{
		final Options options = new Options();
		final Set<String> excludedPOSTags = Set.of(EvaluationTools.other_pos_tag, adverb_pos_tag);
		final Corpus corpus  = EvaluationTools.loadResourcesFromText(input_folder, output_path, resources_factory, language, max_span_size,
				excludedPOSTags, options.min_context_freq);
		EvaluationTools.rankMeanings(options, corpus, resources_factory, excludedPOSTags);



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
}
