package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import com.rxnlp.tools.rouge.ROUGECalculator;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.PlanningProperties;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus.Corpus;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus.Sentence;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math.stat.descriptive.rank.Median;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static java.util.stream.Collectors.*;

public class ExtractiveEvaluation
{
	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private static final POS.Tagset tagset = POS.Tagset.Simple;
	private static final String summary_extension = ".summ";

//	private static final Set<String> excludedPOS = Set.of("CC","DT","EX","IN","LS","MD","PDT","Tag","PRP","PRP$","RB","RBR","RBS","RP","TO","UH","WDT","WP","WP$","WRB");
	private final static Logger log = LogManager.getLogger();

	public static void run(Path project_folder, Path properties_file) throws Exception
	{
		Stopwatch gtimer = Stopwatch.createStarted();

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
			corpus = EvaluationCorpus.createFromXML(input);
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
				InitialResourcesFactory resources_factory = new InitialResourcesFactory(language, properties);

				corpus.texts.forEach(text ->
				{
					Stopwatch timer = Stopwatch.createStarted();
					final String basename = FilenameUtils.removeExtension(text.filename);
					log.info("\n***Text " + text.id + " (" + basename + ")***");

					EvaluationTools.createResources(text, tagset, resources_factory, max_span_size, excluded_mention_POS, true, options);
					EvaluationTools.rankMeanings(text, options);
					EvaluationTools.disambiguate(text, options);
					EvaluationTools.rankMentions(text, options);
					EvaluationTools.plan(text, options);
					text.resources = null; // free memory!
					log.info("Text processed in " + timer.toString());

				});
				log.info("Corpus processed in " + process_timer.stop());
				Serializer.serialize(corpus, corpus_file);
				log.info("Corpus serialized to " + corpus_file.toAbsolutePath().toString());
				if (properties.getUpdateCache())
					resources_factory.serializeCache();
			}

			log.info("\n*****Creating summaries*****");
			corpus.texts.forEach(text ->
			{
				final String basename = FilenameUtils.removeExtension(text.filename);
				final String id = FilenameUtils.removeExtension(text.filename).substring(basename.indexOf('_') + 1);
				log.info("\n***Text " + text.id + " (" + basename + ")***");

				// Print summaries and logging info
				Function<Mention, String> print_context = m -> text.sentences.stream()
						.filter(s -> s.id.equals(m.getContextId()))
						.findFirst()
						.map(s -> s.tokens.stream()
								.map(t -> m.getId().contains(t.id) ? "*" + t.wf + "*" : t.wf)
								.collect(joining(" ")))
						.orElse("");

				final List<List<String>> gold_tokens = gold.keySet().stream()
						.filter(k -> k.contains(id))
						.findFirst().map(gold::get).orElse(null);
				if (gold_tokens != null)
				{
					final int num_sentences = gold_tokens.size(); // make extractive summaries match num of sentences of gold summary
					final long num_words = gold_tokens.stream() // make bow summaries match num of content words of gold summary
							.mapToLong(s -> s.stream()
									.filter(t -> FunctionWordsFilter.test(t, language))
									.count())
							.sum();

					log.info("*Printing summaries*");
					final String gold_summary = gold_tokens.stream()
							.map(s -> String.join(" ", s))
							.collect(joining("\n\t"));
					log.info(num_sentences + " sentences, " + num_words + " content words, " + gold_summary.length() + " characters.");
					log.info("Gold summary:\n\t" + gold_summary + "\n");

					{
						final String summary = getLeadSummary(text, num_sentences);
						final Path summary_path = system.resolve(basename + "_lead" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getBoMSummary(text, num_words);
						final Path summary_path = system.resolve(basename + "_bom" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getBoWSummary(text, num_words, print_context);
						final Path summary_path = system.resolve(basename + "_bow" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						log.info("Extractive meaning-based summary");
						final String summary = getExtractiveSummary(text, num_sentences, Candidate::getWeight);
						final Path summary_path = system.resolve(basename + "_extrmeaning" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						log.info("Extractive mention-based summary");
						final String summary = getExtractiveSummary(text, num_sentences, c -> c.getMention().getWeight());
						final Path summary_path = system.resolve(basename + "_extrmention" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getExtractivePlanSummary(text, num_words);
						final Path summary_path = system.resolve(basename + "_extrplan" + summary_extension);
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

	private static String getLeadSummary(EvaluationCorpus.Text text, long num_sentences)
	{
		final String summary = text.sentences.stream()
				.limit(num_sentences)
				.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
				.limit(num_sentences)
				.collect(joining("\n\t"));
		log.info("Lead summary:\n\t" + summary + "\n");

		return summary;
	}

	private static String getBoMSummary(EvaluationCorpus.Text text, long num_words)
	{
		final List<Candidate> disambiguated_candidates = text.sentences.stream()
				.flatMap(sentence -> sentence.disambiguated.values().stream())
				.sorted(Comparator.<Candidate>comparingDouble(c -> c.getWeight().orElse(0.0)).reversed())
				.collect(toList());

		final Map<Meaning, List<Candidate>> meanings2candidates = disambiguated_candidates.stream()
				.collect(groupingBy(Candidate::getMeaning));
		final Map<Meaning, Double> meanings2weights = meanings2candidates.keySet().stream()
				.collect(toMap(m -> m, m -> meanings2candidates.get(m).stream()
						.map(Candidate::getWeight)
						.flatMap(Optional::stream)
						.mapToDouble(d -> d)
						.average().orElse(0.0)));
		final List<Meaning> sorted_meanings = meanings2weights.keySet().stream()
				.sorted(Comparator.<Meaning>comparingDouble(meanings2weights::get).reversed())
				.collect(toList());

		// BoM summary
		AtomicInteger accum_words = new AtomicInteger();
		final String summary = sorted_meanings.stream()
				.map(Meaning::getLabel)
				.takeWhile(l -> accum_words.addAndGet(l.split(" ").length) < num_words)
				.collect(joining(" "));

		accum_words.set(0);
		final String candidates_str = sorted_meanings.stream()
				.takeWhile(m -> accum_words.addAndGet(m.getLabel().split(" ").length) < num_words)
				.map(m -> m.toString() + "\t" + DebugUtils.printDouble(meanings2weights.get(m)))
				.collect(joining("\n\t"));
		log.info("BoM summary: " + summary + "\n");
		log.debug("BoM summary:\n\t" + candidates_str + "\n");

		return summary;
	}

	private static String getBoWSummary(EvaluationCorpus.Text text, long num_words, Function<Mention, String> print_context)
	{
		// BoW summary
		final List<Candidate> sorted_candidates= text.sentences.stream()
				.flatMap(sentence -> sentence.disambiguated.values().stream())
				.filter(c -> c.getMention().getWeight().isPresent())
				.sorted(Comparator.<Candidate>comparingDouble(c -> c.getMention().getWeight().get()).reversed())
				.collect(toList());

		AtomicInteger accum_words = new AtomicInteger();
		final String summary = sorted_candidates.stream()
				.map(Candidate::getMention)
				.map(Mention::getSurfaceForm)
				.distinct() // no two identical lists of tokens
				.takeWhile(l -> accum_words.addAndGet(l.split(" ").length) < num_words)
				.collect(joining(" "));

		accum_words.set(0);
		final String candidates_str = sorted_candidates.stream()
				.takeWhile(c -> accum_words.addAndGet(c.getMention().getSurfaceForm().split(" ").length) < num_words)
				.map(c -> c.getMention().toString() + "\t" + c.getWeight().map(DebugUtils::printDouble).orElse("-") + "\t" + print_context.apply(c.getMention()))
				.collect(joining("\n\t"));
		log.info("BoW summary: " + summary + "\n");
		log.debug("BoW summary:\n\t" + candidates_str + "\n");

		return summary;
	}

	private static String getExtractiveSummary(EvaluationCorpus.Text text, int num_sentences, Function<Candidate, Optional<Double>> weight_function)
	{
		// Calculate sentence boundaries in token offsets
		final Map<Sentence, Pair<Integer, Integer>> sentence2spans = new HashMap<>();
		sentence2spans.put(text.sentences.get(0), Pair.of(0, text.sentences.get(0).tokens.size()));

		for (int i=1; i < text.sentences.size(); ++i)
		{
			final int offset = sentence2spans.get(text.sentences.get(i - 1)).getRight();
			final int num_tokens = text.sentences.get(i - 1).tokens.size();
			sentence2spans.put(text.sentences.get(i), Pair.of(offset, offset + num_tokens));
		}

		// Get all document tokens and their offsets
		final double[] weights = text.sentences.stream()
				.flatMap(s -> s.disambiguated.values().stream())
				.map(weight_function)
				.mapToDouble(d -> d.orElse(0.0))
				.toArray();

		final double average_weight = new Median().evaluate(weights);
//		log.debug("Average weight is " + DebugUtils.printDouble(average_weight));

		final List<EvaluationCorpus.Token> tokens = text.sentences.stream()
				.flatMap(s -> s.tokens.stream())
				.collect(toList());

		final Map<EvaluationCorpus.Token, Double> tokens2weights = text.sentences.stream()
				.flatMap(s -> s.disambiguated.values().stream())
				.flatMap(candidate ->
						IntStream.range(candidate.getMention().getSpan().getLeft(), candidate.getMention().getSpan().getRight())
								.mapToObj(tokens::get)
								.map(t -> Pair.of(t, weight_function.apply(candidate).orElse(average_weight))))
				.collect(toMap(Pair::getLeft, Pair::getRight, Math::max));

		// Calculate weights for each sentence
		final Map<Sentence, String> sentences2weightlists = new HashMap<>();
		final Map<Sentence, Double> sentences2weights = sentence2spans.keySet().stream()
				.collect(toMap(s -> s, s -> {
					final StringBuffer b = new StringBuffer();
					final double sentence_weight = s.tokens.stream()
							.mapToDouble(t -> {
								final double w = tokens2weights.getOrDefault(t, average_weight);
								b.append(t.wf).append(" ");
								if (w > 0.0)
									b.append(DebugUtils.printDouble(w)).append(" ");
								return w - 0.1;
							})
							.sum();
					sentences2weightlists.put(s, b.toString());

					return sentence_weight;
				}));

		// sort sentences and compose summary
		final List<Sentence> sorted_sentences = sentences2weights.entrySet().stream()
				.sorted(Comparator.<Map.Entry<Sentence, Double>>comparingDouble(Map.Entry::getValue).reversed())
				.map(Map.Entry::getKey)
				.collect(toList());

		final String debug = sorted_sentences.stream()
				.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")) +
						"\t" + DebugUtils.printDouble(sentences2weights.get(sentence)) + "\n\t\t" + sentences2weightlists.get(sentence))
				//.limit(num_sentences)
				.collect(joining("\n\t", "\n\t", "\n"));

		final List<String> summary_sentences = sorted_sentences.stream()
				.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
				.limit(num_sentences)
				.collect(toList());

		log.debug("Extractive summary:" + debug);
		log.info("Extractive summary:\n\t" + String.join("\n\t", summary_sentences) + "\n");

		return String.join("\n", summary_sentences);
	}

	private static String getExtractivePlanSummary(EvaluationCorpus.Text text, long num_words)
	{
		final Map<SemanticSubgraph, List<Mention>> subgraphs2mentions = text.subgraphs.stream()
				.collect(toMap(subgraph -> subgraph, subgraph -> subgraph.vertexSet().stream()
						.map(v -> subgraph.getBase().getMentions(v))
						.flatMap(Collection::stream)
						.sorted(Comparator.comparingInt(m -> m.getSpan().getLeft()))
						.collect(toList())));

		final Map<SemanticSubgraph, String> subgraphs2weightlists = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.filter(m -> m.getWeight().isPresent())
						.map(m -> m.getSurfaceForm() + " " + DebugUtils.printDouble(m.getWeight().get()))
						.collect(joining(", "))));

		final Map<SemanticSubgraph, OptionalDouble> subgraphs2weights = subgraphs2mentions.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.map(Mention::getWeight)
						.flatMap(Optional::stream)
						.mapToDouble(w -> w)
						.average()));

		AtomicInteger num_tokens = new AtomicInteger();

		final Map<SemanticSubgraph, String> subgraphs2texts = subgraphs2mentions.entrySet().stream()
				.peek(e ->
				{
					final int subgraph_tokens = e.getValue().stream()
							.mapToInt(Mention::numTokens)
							.sum();
					num_tokens.accumulateAndGet(subgraph_tokens, Integer::sum);
				})
				.takeWhile(mentions -> num_tokens.get() < num_words)
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.map(Mention::getSurfaceForm)
						.collect(joining(" "))));


		final String debug = subgraphs2texts.keySet().stream()
				.map(s -> subgraphs2texts.get(s) + ".\t" + DebugUtils.printDouble(subgraphs2weights.get(s).orElse(0.9)) + "\t-\t" + subgraphs2weightlists.get(s))
				.collect(joining("\n\t", "\n\t", "\n"));
		log.debug("Extractive plan summary:" + debug);
		log.info("Extractive plan summary:\n\t" + String.join("\n\t", subgraphs2texts.values()) + "\n");

		return String.join("\n", subgraphs2texts.values());
	}
}

