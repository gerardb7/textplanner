package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import edu.upf.taln.textplanning.uima.io.TextParser;
import edu.upf.taln.textplanning.uima.io.UIMAWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;

public class ExtractiveEvaluation
{
	public static class DeepMindSourceParser extends TextParser
	{
		@Override
		protected String parse(String file_contents)
		{
			final String[] parts = file_contents.split("@highlight");
			if (parts.length == 0)
				return "";
			return Arrays.stream(parts[0].split("[\\r\\n]+"))
					.filter(not(String::isEmpty))
					.collect(joining(System.getProperty("line.separator")));
		}
	}

	public static class DeepMindSoummaryParser extends TextParser
	{
		@Override
		protected String parse(String file_contents)
		{
			final String[] parts = file_contents.split("^\\s*@highlight\\s*$");
			if (parts.length < 2)
				return "";

			return IntStream.range(1, parts.length)
					.mapToObj(i -> parts[i])
					.filter(not(String::isEmpty))
					.map(t -> Arrays.stream(t.split("[\\r\\n]+"))
							.filter(not(String::isEmpty))
							.collect(joining(System.getProperty("line.separator"))))
					.collect(joining(System.getProperty("line.separator")));
		}
	}

	private static final int max_span_size = 3;
	private static final boolean rank_together = true;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String suffix = ".story";
	private static final String noun_pos_prefix = "N";
	private static final Set<String> excludedPOS = Set.of("CC","DT","EX","IN","LS","MD","PDT","POS","PRP","PRP$","RB","RBR","RBS","RP","TO","UH","WDT","WP","WP$","WRB");
	private final static Logger log = LogManager.getLogger();

	public static void preprocess(Path input_folder, Path output_folder)
	{
		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createParsingPipeline(language);
		if (pipeline != null)
		{
			UIMAWrapper.processAndSerialize(input_folder, output_folder, suffix, DeepMindSourceParser.class, pipeline);
		}
	}

	public static void run(Path input_folder, Path gold_folder, Path output_path, InitialResourcesFactory resources_factory)
	{
		final Options options = new Options();
		//options.excluded_ranking_POS_Tags = excludedPOS;

		// load corpus
		final Corpus corpus = EvaluationTools.loadResourcesFromXMI(input_folder, output_path, resources_factory,
				language, max_span_size, rank_together, noun_pos_prefix, excludedPOS, options);

		// rank
		EvaluationTools.rankMeanings(options, corpus);
		EvaluationTools.rankMentions(options, corpus);

		// create summaries (without a graph!)
		corpus.texts.forEach(text ->
		{
			final String summary = text.sentences.stream()
					.map(sentence -> sentence.candidates.keySet())
					.flatMap(Set::stream)
					.sorted(Comparator.comparingDouble(Mention::getWeight).reversed())
					.map(Mention::getSurface_form)
					.distinct()
					.collect(joining(" "));
			log.info("Summary: " + summary);
		});

		EvaluationTools.plan(options, corpus);
	}
}

