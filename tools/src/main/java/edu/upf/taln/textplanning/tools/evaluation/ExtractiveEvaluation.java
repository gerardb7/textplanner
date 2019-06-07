package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Sentence;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static java.util.stream.Collectors.*;

public class ExtractiveEvaluation
{


	private static final int max_span_size = 3;
	private static final boolean rank_together = false;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String noun_pos_tag = "N";
	private static final String adj_pos_tag = "J";
	private static final String verb_pos_tag = "V";
	private static final String adverb_pos_tag = "R";
	private static final String other_pos_tag = "X";
	private static final String summary_extension = ".summ";

//	private static final Set<String> excludedPOS = Set.of("CC","DT","EX","IN","LS","MD","PDT","POS","PRP","PRP$","RB","RBR","RBS","RP","TO","UH","WDT","WP","WP$","WRB");
	private final static Logger log = LogManager.getLogger();

//	public static void preprocess(Path input_folder, Path output_folder)
//	{
//		final UIMAWrapper.Pipeline pipeline = UIMAWrapper.createParsingPipeline(language);
//		if (pipeline != null)
//		{
//			UIMAWrapper.processAndSerialize(input_folder, output_folder, document_extension, DeepMindSourceParser.class, pipeline);
//		}
//	}



	public static void run(Path input, Path gold_folder, Path output_path, Path tmp_folder, InitialResourcesFactory resources_factory)
	{
		final Options options = new Options();
		options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		options.min_bias_threshold = 0.8; // minimum bias value below which candidate meanings are ignored
		options.num_first_meanings = 1;
		options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		options.damping_meanings = 0.5; // controls balance between bias and similarity: higher value -> more bias
		options.damping_variables = 0.8; // controls bias towards meanings rank when ranking variables

		// Exclude POS from mention collection
		final Set<String> excluded_mention_POS = Set.of(other_pos_tag);
		// Include these POS in the ranking of meanings
		options.ranking_POS_Tags = Set.of(noun_pos_tag); //, adj_pos_tag, verb_pos_tag, adverb_pos_tag);

		// load input corpus and gold
		Corpus corpus = null;
		if (Files.isRegularFile(input))
		{
			corpus = EvaluationTools.loadResourcesFromXML(input, resources_factory,
					language, max_span_size, rank_together, noun_pos_tag, excluded_mention_POS, options);
		}
		else if (Files.isDirectory(input))
		{
			corpus = EvaluationTools.loadResourcesFromRawText(input, tmp_folder, resources_factory,
				language, max_span_size, rank_together, noun_pos_tag, excluded_mention_POS, options);
		}

		final Map<String, List<List<String>>> gold = parseGold(gold_folder);

		// rank
		EvaluationTools.rankMeanings(options, corpus);
//		EvaluationTools.printDisambiguationResults(corpus, m -> Set.of(), false, evaluate_POS);
		EvaluationTools.disambiguate(corpus);
		EvaluationTools.rankMentions(options, corpus);


		// create summaries (without a graph!)
		corpus.texts.forEach(text ->
		{
			Function<Mention, String> printContext = m -> text.sentences.stream()
					.filter(s -> s.id.equals(m.getSourceId()))
					.findFirst()
					.map(s -> s.tokens.stream()
							.map(t -> m.getContextId().contains(t.id) ? "*" + t.wf + "*" : t.wf)
							.collect(joining(" ")))
					.orElse("");

			final String basename = FilenameUtils.removeExtension(text.filename);
			final String id = FilenameUtils.removeExtension(text.filename).substring(basename.indexOf('_')+ 1);

			log.info("Text " + text.id + " (" + basename + ")");
			final List<List<String>> gold_tokens = gold.keySet().stream()
					.filter(k -> k.contains(id))
					.findFirst().map(gold::get).orElse(null);
			final int num_sentences = gold_tokens.size(); // make extractive summaries match num of sentences of gold summary
//			log.info(gold_tokens.stream() // make bow summaries match num of content words of gold summary
//					.map(s -> s.stream()
//							.filter(t -> StopWordsFilter.test(t, language))
//							.collect(toList()))
//					.collect(toList()));


			final long num_words = gold_tokens.stream() // make bow summaries match num of content words of gold summary
					.mapToLong(s -> s.stream()
							.filter(t -> FunctionWordsFilter.test(t, language))
							.count())
					.sum();
			final String gold_summary = gold_tokens.stream()
					.map(s -> String.join(" ", s))
					.collect(joining("\n\t"));
			log.info(num_sentences + " sentences, " + num_words + " content words, " + gold_summary.length() + " characters.");
			log.info("Gold summary:\n\t" + gold_summary + "\n");

			{
				// Lead summary
				final String summary = text.sentences.stream()
						.limit(num_sentences)
						.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
						.limit(num_sentences)
						.collect(joining("\n"));
				log.info("Lead summary:" + summary);
				{
					final Path summary_path = output_path.resolve(basename + "_lead" + summary_extension);
					FileUtils.writeTextToFile(summary_path, summary);
				}
			}

			{
				// BoM summary
				final List<Candidate> sorted_candidates = text.sentences.stream()
						.flatMap(sentence -> sentence.disambiguated.values().stream())
						.sorted(Comparator.<Candidate>comparingDouble(c -> c.getWeight().orElse(0.0)).reversed())
						.collect(toList());

				final String summary = sorted_candidates.stream()
						.map(Candidate::getMention)
						.map(Mention::getLemma)
						.distinct()
						.limit(num_words)
						.collect(joining(" "));

				final String candidates_str = sorted_candidates.stream()
						.limit(num_words)
						.map(c -> c.getMention().toString() + "\t" + printContext.apply(c.getMention()))
						.collect(joining("\n\t"));

				log.info("BoM summary: " + summary + "\n\t" + candidates_str + "\n");
				{
					final Path summary_path = output_path.resolve(basename + "_bom" + summary_extension);
					FileUtils.writeTextToFile(summary_path, summary);
				}
			}


			{
				// BoW summary
				final List<Candidate> sorted_candidates= text.sentences.stream()
						.flatMap(sentence -> sentence.disambiguated.values().stream())
						.filter(c -> c.getMention().getWeight().isPresent())
						.sorted(Comparator.<Candidate>comparingDouble(c -> c.getMention().getWeight().get()).reversed())
						.collect(toList());

				final String summary = sorted_candidates.stream()
						.map(Candidate::getMention)
						.map(Mention::getLemma)
						.distinct()
						.limit(num_words)
						.collect(joining(" "));

				final String candidates_str = sorted_candidates.stream()
						.limit(num_words)
						.map(c -> c.getMention().toString() + "\t" + printContext.apply(c.getMention()))
						.collect(joining("\n\t"));

				log.info("BoW summary: " + summary + "\n\t" + candidates_str + "\n");
				{
					final Path summary_path = output_path.resolve(basename + "_bow" + summary_extension);
					FileUtils.writeTextToFile(summary_path, summary);
				}
			}

			{
				final Map<Sentence, List<Candidate>> sentences2meanings = text.sentences.stream()
						.collect(toMap(s -> s, s -> s.disambiguated.values().stream()
								.filter(c -> c.getWeight().isPresent())
								.collect(toList())));

				// Extractive meaning summary
				final Map<Sentence, String> sentences2weightlists = sentences2meanings.entrySet().stream()
						.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
								.map(c -> c.getMention().getSurface_form() + " " +
										DebugUtils.printDouble(c.getWeight().orElseThrow()))
								.collect(joining(", "))));

				final Map<Sentence, Double> sentences2weights = sentences2meanings.entrySet().stream()
						.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
								.map(Candidate::getWeight)
								.flatMap(Optional::stream)
								.mapToDouble(d -> d)
								.average()
								.orElse(0.0)));

				final List<Sentence> sorted_sentences = sentences2weights.entrySet().stream()
						.sorted(Comparator.<Map.Entry<Sentence, Double>>comparingDouble(Map.Entry::getValue).reversed())
						.map(Map.Entry::getKey)
						.collect(toList());

				final String debug = sorted_sentences.stream()
						.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")) +
								"\t" + DebugUtils.printDouble(sentences2weights.get(sentence)) + "\t" + sentences2weightlists.get(sentence))
						.limit(num_sentences)
						.collect(joining("\n\t", "\n\t", "\n"));

				final String summary = sorted_sentences.stream()
						.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
						.limit(num_sentences)
						.collect(joining("\n"));
				log.info("Extractive meaning summary:" + debug);
				{
					final Path summary_path = output_path.resolve(basename + "_extrmeaning" + summary_extension);
					FileUtils.writeTextToFile(summary_path, summary);
				}
			}

			{
				final Map<Sentence, List<Mention>> sentences2mentions = text.sentences.stream()
						.collect(toMap(s -> s, s -> s.mentions.stream()
								.filter(c -> c.getWeight().isPresent())
								.peek(m -> {
									if(m.getWeight().get() == 0.0)
										log.error("Mention has zero-valued weight: " + m.toString());
								})
								.collect(toList())));

				final Map<Sentence, String> sentences2weightlists = sentences2mentions.entrySet().stream()
						.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
								.filter(m -> m.getWeight().isPresent())
								.map(m -> m.getSurface_form() + " " + DebugUtils.printDouble(m.getWeight().get()))
								.collect(joining(", "))));

				final Map<Sentence, Double> sentences2weights = sentences2mentions.entrySet().stream()
						.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
								.flatMap(m -> m.getWeight().stream())
								.mapToDouble(d -> d)
								.average()
								.orElse(0.0)));

				final List<Sentence> sorted_sentences = sentences2weights.entrySet().stream()
						.sorted(Comparator.<Map.Entry<Sentence, Double>>comparingDouble(Map.Entry::getValue).reversed())
						.map(Map.Entry::getKey)
						.collect(toList());

				final String debug = sorted_sentences.stream()
						.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")) +
								"\t" + DebugUtils.printDouble(sentences2weights.get(sentence)) + "\t" + sentences2weightlists.get(sentence))
						.limit(num_sentences)
						.collect(joining("\n\t", "\n\t", "\n"));

				final String summary = sorted_sentences.stream()
						.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
						.limit(num_sentences)
						.collect(joining("\n"));

				log.info("Extractive mention summary:" + debug);
				{
					final Path summary_path = output_path.resolve(basename + "_extrmention" + summary_extension);
					FileUtils.writeTextToFile(summary_path, summary);
				}
			}
		});
	}


	private static Map<String, List<List<String>>> parseGold(Path gold_folder)
	{
		log.info("Parsing gold summaries files");
		final File[] gold_files = getFilesInFolder(gold_folder, summary_extension);
		assert gold_files != null;

		return Arrays.stream(gold_files)
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.collect(toMap(
						path -> FilenameUtils.removeExtension(path.getFileName().toString()),
						path ->
						{
							String text = FileUtils.readTextFile(path);
							return Pattern.compile("\n+").splitAsStream(text)
									.filter(l -> !l.isEmpty())
									.map(g -> {
										final StringTokenizer stringTokenizer = new StringTokenizer(g, " \t\n\r\f,.:;?!{}()[]'");
										return Collections.list(stringTokenizer).stream().map(Object::toString).collect(toList());
									})
									.collect(toList());
						}));
	}


}

