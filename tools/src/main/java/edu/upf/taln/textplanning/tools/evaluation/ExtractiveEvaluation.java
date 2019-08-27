package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import com.rxnlp.tools.rouge.ROUGECalculator;
import edu.upf.taln.textplanning.common.*;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.corpus.Corpora;
import edu.upf.taln.textplanning.core.corpus.Corpora.Corpus;
import edu.upf.taln.textplanning.core.corpus.Corpora.Token;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.summarization.Summarizer;
import edu.upf.taln.textplanning.core.summarization.Summarizer.*;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static edu.upf.taln.textplanning.core.summarization.Summarizer.*;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public class ExtractiveEvaluation
{
	private static final int max_span_size = 3;
	private static final int context_size = 1;
	private static final double default_weight = 0.1;
	private static final boolean use_dependencies = true;
	private static final ULocale language = ULocale.ENGLISH;
	private static final POS.Tagset tagset = POS.Tagset.Simple;
	private static final String summary_extension = ".summ";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path project_folder, Path properties_file) throws Exception
	{
		Stopwatch gtimer = Stopwatch.createStarted();
		log.info("Summarization options: max_span_size = " + max_span_size + " context_size = " + context_size + " default_weight = " + default_weight + " use_dependencies = " + use_dependencies);

		// Exclude Tag from mention collection
		final Set<POS.Tag> excluded_mention_POS = Set.of(POS.Tag.X);

		// load input corpus and gold
		Path input = project_folder.resolve("corpus.bin");
		if (!input.toFile().exists())
			input = project_folder.resolve("corpus.xml");
		if (!input.toFile().exists())
			input = project_folder.resolve("texts");
		if (!input.toFile().exists())
		{
			log.error("Cannot find a suitable input in " + project_folder);
			System.exit(-1);
		}

		Path reference = project_folder.resolve("reference");
		Path system = project_folder.resolve("system");
		Path tmp = project_folder.resolve("tmp");
		Path corpus_file = project_folder.resolve("corpus.bin");
		Path results_file = project_folder.resolve("results.csv");

		final Corpus corpus;
		boolean is_ranked = false;
		if (Files.isRegularFile(input) && FilenameUtils.getExtension(input.getFileName().toString()).equals("xml"))
		{
			corpus = Corpora.createFromXML(input);
		}
		else if (Files.isRegularFile(input) && FilenameUtils.getExtension(input.getFileName().toString()).equals("bin"))
		{
			corpus = (Corpus) Serializer.deserialize(input);
			is_ranked = true;
			log.info("Deserialized corpus from " + input.toAbsolutePath().toString());
		}
		else if (Files.isDirectory(input))
		{
			corpus = EvaluationTools.loadResourcesFromRawText(input, tmp);
		}
		else
			corpus = null;

		final Map<String, List<List<String>>> gold = parseGold(reference);
//		if (corpus != null)
//			corpus.texts = corpus.texts.stream().filter(text -> text.id.equalsIgnoreCase("d001")).collect(toList());
//			corpus.texts = corpus.texts.subList(49, corpus.texts.size());

		// create summaries
		if (corpus != null)
		{
			if (!is_ranked)
			{
				log.info("\n*****Processing texts*****");
				final Options options = new Options();
				log.info(options + "\n");
				Stopwatch process_timer = Stopwatch.createStarted();

				PlanningProperties properties = new PlanningProperties(properties_file);
				InitialResourcesFactory initial_resources = new InitialResourcesFactory(language, properties);

				corpus.texts.forEach(text ->
				{
					Stopwatch timer = Stopwatch.createStarted();
					final String basename = FilenameUtils.removeExtension(text.filename);
					log.info("\n***Text " + text.id + " (" + basename + ")***");

					final DocumentResourcesFactory resources = EvaluationTools.createResources(text, tagset, initial_resources, max_span_size, excluded_mention_POS, true, options);
					assert resources != null;
					EvaluationTools.rankMeanings(text, resources, options);
					EvaluationTools.disambiguate(text, options);
					EvaluationTools.rankMentions(text, use_dependencies, context_size, options);
					EvaluationTools.plan(text, resources, use_dependencies, context_size, options);
					log.info("Text processed in " + timer.toString());
				});
				log.info("Corpus processed in " + process_timer.stop());
				Serializer.serialize(corpus, corpus_file);
				log.info("Corpus serialized to " + corpus_file.toAbsolutePath().toString());
				if (properties.getUpdateCache())
					initial_resources.serializeCache();
			}

			log.info("\n*****Creating summaries*****");
			corpus.texts.forEach(text ->
			{
				final String basename = FilenameUtils.removeExtension(text.filename);
				final String id = FilenameUtils.removeExtension(text.filename).substring(basename.indexOf('_') + 1);
				log.info("\n***Text " + text.id + " (" + basename + ")***");

				final List<List<String>> gold_tokens = gold.keySet().stream()
						.filter(k -> k.contains(id))
						.findFirst().map(gold::get).orElse(null);
				if (gold_tokens != null)
				{
					final int num_sentences = gold_tokens.size(); // to make summaries match num of sentences of gold summary
					final int num_content_words = (int) gold_tokens.stream() // to make extractive summaries match num of tokens of gold summary
							.flatMap(l -> l.stream()
									.filter(t -> FunctionWordsFilter.test(t, language)))
							.count();
					final int num_tokens = gold_tokens.stream() // to make extractive summaries match num of tokens of gold summary
							.mapToInt(List::size)
							.sum();

					log.info("*Printing summaries*");
					final String gold_summary = gold_tokens.stream()
							.map(s -> String.join(" ", s))
							.collect(joining("\n\t"));
					log.info("\nGold summary:\n\t" + gold_summary + "\n");
					log.info("\t" + num_sentences + " sentences, " + num_tokens + " words, " + gold_summary.length() + " characters.\n");

					// Collect all required weights
					final List<Token> tokens = text.sentences.stream()
							.flatMap(s -> s.tokens.stream())
							.collect(toList());
					final Map<Token, Optional<Double>> tokens2MeaningWeights = text.sentences.stream()
							.flatMap(s -> s.disambiguated_meanings.values().stream())
							.flatMap(candidate ->
									IntStream.range(candidate.getMention().getSpan().getLeft(), candidate.getMention().getSpan().getRight())
											.mapToObj(tokens::get)
											.map(t -> Pair.of(t, candidate.getWeight())))
							.collect(toMap(Pair::getLeft, Pair::getRight, (o1, o2) -> o1.flatMap(w1 -> o2.map(w2 -> Math.max(w1, w2))))); // if token part of two mentions, choose max weight
					tokens.stream()
							.filter(not(tokens2MeaningWeights::containsKey))
							.forEach(t -> tokens2MeaningWeights.put(t, Optional.empty()));
					final Map<Token, Optional<Double>> tokens2MentionWeights = text.sentences.stream()
							.flatMap(s -> s.ranked_words.stream())
							.collect(toMap(m -> tokens.get(m.getSpan().getLeft()), Mention::getWeight));
					tokens.stream()
							.filter(not(tokens2MentionWeights::containsKey))
							.forEach(t -> tokens2MentionWeights.put(t, Optional.empty()));

					Summarizer.printDebugInfo(text, num_tokens, tokens2MeaningWeights::get, tokens2MentionWeights::get, default_weight);

					{
						log.info("\nLeading sentences summary");
						final String summary = getExtractiveSummary(text, ItemType.Sentence, num_sentences, WeightCriterion.Leading, null, default_weight);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_leadSentences" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}
					{
						log.info("Leading words summary");
						final String summary = getExtractiveSummary(text, ItemType.Word, num_tokens,  WeightCriterion.Leading, null, default_weight);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_leadWords" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						log.info("BOM summary");
						final String summary = getBoMSummary(text, num_content_words);
						log.info("\n\t" + summary + "\n");
						final Path summary_path = system.resolve(basename + "_bom" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						log.info("BOW summary");
						final String summary = getBoWSummary(text, num_content_words);
						log.info("\n\t" + summary + "\n");
						final Path summary_path = system.resolve(basename + "_bow" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						log.info("Meaning, sentence-based summary");
						final String summary = getExtractiveSummary(text, ItemType.Sentence, num_sentences, WeightCriterion.Average, tokens2MeaningWeights::get, default_weight);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_meaningSentences" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}
					{
						log.info("Meaning, word-based summary");
						final String summary = getExtractiveSummary(text, ItemType.Word, num_tokens, WeightCriterion.Average, tokens2MeaningWeights::get, default_weight);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_meaningWords" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}
					{
						log.info("Mention, sentence-based summary");
						final String summary = getExtractiveSummary(text, ItemType.Sentence, num_sentences, WeightCriterion.Average, tokens2MentionWeights::get, default_weight);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_mentionSentences" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}
					{
						log.info("Mention, word-based summary");
						final String summary = getExtractiveSummary(text, ItemType.Word, num_tokens, WeightCriterion.Average, tokens2MentionWeights::get, default_weight);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_mentionWords" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}
					{
						log.info("Extractive plan-based summary");
						final String summary = getExtractivePlanSummary(text, num_tokens);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_planExtractive" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						log.info("Plan-based summary");
						final String summary = getPlanSummary(text, num_tokens);
						log.info(summary + "\n");
						final Path summary_path = system.resolve(basename + "_plan" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

				}
			});
		}

		// Run ROUGE evaluation
		ROUGECalculator rouge = new ROUGECalculator(results_file);
		rouge.run(project_folder);

		log.info("Task finished in " + gtimer.stop());
		log.info("*****\n");
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

