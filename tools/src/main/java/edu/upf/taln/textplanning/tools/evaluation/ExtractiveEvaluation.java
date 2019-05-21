package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Sentence;
import edu.upf.taln.textplanning.uima.io.TextParser;
import edu.upf.taln.textplanning.uima.io.UIMAWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
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

	public static class DeepMindSummaryParser extends TextParser
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
	private static final boolean rank_together = false;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String noun_pos_tag = "N";
	private static final String adj_pos_tag = "J";
	private static final String verb_pos_tag = "V";
	private static final String adverb_pos_tag = "R";
	private static final String other_pos_tag = "X";
	private static final String suffix = ".story";
//	private static final Set<String> excludedPOS = Set.of("CC","DT","EX","IN","LS","MD","PDT","POS","PRP","PRP$","RB","RBR","RBS","RP","TO","UH","WDT","WP","WP$","WRB");
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
		options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		options.min_bias_threshold = 0.8; // minimum bias value below which candidate meanings are ignored
		options.num_first_meanings = 1;
		options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		options.damping_meanings = 0.5; // controls balance between bias and similarity: higher value -> more bias
		options.damping_variables = 0.2; // controls bias towards meanings rank when ranking variables

		// Exclude POS from mention collection
		final Set<String> excluded_mention_POS = Set.of(other_pos_tag);
		// Include these POS in the ranking of meanings
		options.ranking_POS_Tags = Set.of(noun_pos_tag); //, adj_pos_tag, verb_pos_tag, adverb_pos_tag);
		// Evaluate these POS tags only
		Set<String> evaluate_POS = Set.of(noun_pos_tag); //, adj_pos_tag, verb_pos_tag, adverb_pos_tag);

		// load corpus
		final Corpus corpus = EvaluationTools.loadResourcesFromXML(input_folder, output_path, resources_factory,
				language, max_span_size, rank_together, noun_pos_tag, excluded_mention_POS, options);

		// rank
		EvaluationTools.rankMeanings(options, corpus);
//		EvaluationTools.printDisambiguationResults(corpus, m -> Set.of(), false, evaluate_POS);
		EvaluationTools.disambiguate(corpus);
		EvaluationTools.rankMentions(options, corpus);

		// create summaries (without a graph!)
		corpus.texts.forEach(text ->
		{
			log.info("Text " + text.id);
			final String bow_summary = text.sentences.stream()
					.map(sentence -> sentence.disambiguated.keySet())
					.flatMap(Set::stream)
					.sorted(Comparator.comparingDouble(Mention::getWeight).reversed())
					.map(Mention::getSurface_form)
					.distinct()
					.map(s -> s.contains(" ") ? "\"" + s + "\"" : s)
					.limit(50)
					.collect(joining(" "));
			log.info("BoW summary:\n\t" + bow_summary + "\n");

			final String summary = text.sentences.stream()
					.sorted(Comparator.<Sentence>comparingDouble(s -> s.disambiguated.keySet().stream().mapToDouble(Mention::getWeight).average().orElse(0.0)).reversed())
					.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
					.limit(10)
					.collect(joining("\n\t ", "\n\t", "\n"));
			log.info("Extractive summary: " + summary);
		});

		//EvaluationTools.plan(options, corpus);
	}
}

