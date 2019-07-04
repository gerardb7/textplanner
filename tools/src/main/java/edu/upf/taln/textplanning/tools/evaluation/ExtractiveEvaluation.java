package edu.upf.taln.textplanning.tools.evaluation;

import com.google.common.base.Stopwatch;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus.Corpus;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus.Sentence;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

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

	public static void run(Path input, Path gold_folder, Path output_path, Path tmp_folder, InitialResourcesFactory resources_factory)
	{
		Stopwatch gtimer = Stopwatch.createStarted();
		final Options options = new Options();
		options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		options.min_bias_threshold = 0.7; // minimum bias value below which candidate meanings are ignored
		options.num_first_meanings = 1;
		options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		options.damping_meanings = 0.4; // controls balance between bias and similarity: higher value -> more bias
		options.damping_variables = 0.2; // controls bias towards meanings rank when ranking variables

		// Exclude Tag from mention collection
		final Set<POS.Tag> excluded_mention_POS = Set.of(POS.Tag.X);
		// Include these Tag in the ranking of meanings
		options.ranking_POS_Tags = Set.of(POS.Tag.NOUN); //, adj_pos_tag, verb_pos_tag, adverb_pos_tag);
		log.info(options + "\n");

		// load input corpus and gold
		final Corpus corpus;
		if (Files.isRegularFile(input))
		{
			corpus = EvaluationCorpus.createFromXML(input);
		}
		else if (Files.isDirectory(input))
		{
			corpus = EvaluationTools.loadResourcesFromRawText(input, tmp_folder);
		}
		else
			corpus = null;

		final Map<String, List<List<String>>> gold = parseGold(gold_folder);

		// create summaries (without a graph!)
		if (corpus != null)
		{
			corpus.texts.forEach(text ->
			{
				Stopwatch timer = Stopwatch.createStarted();
				final String basename = FilenameUtils.removeExtension(text.filename);
				final String id = FilenameUtils.removeExtension(text.filename).substring(basename.indexOf('_') + 1);
				log.info("\n***Text " + text.id + " (" + basename + ")***");

				EvaluationTools.createResources(text, tagset, resources_factory, max_span_size, excluded_mention_POS, true, options);
				EvaluationTools.rankMeanings(text, options);
				EvaluationTools.disambiguate(text, options);
				EvaluationTools.rankMentions(text, options);
				EvaluationTools.plan(text, options);
				text.resources = null; // free memory!

				// Print summaries and logging info
				Function<Mention, String> print_context = m -> text.sentences.stream()
						.filter(s -> s.id.equals(m.getSourceId()))
						.findFirst()
						.map(s -> s.tokens.stream()
								.map(t -> m.getContextId().contains(t.id) ? "*" + t.wf + "*" : t.wf)
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
						final Path summary_path = output_path.resolve(basename + "_lead" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getBoMSummary(text, num_words, print_context);
						final Path summary_path = output_path.resolve(basename + "_bom" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getBoWSummary(text, num_words, print_context);
						final Path summary_path = output_path.resolve(basename + "_bow" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getExtractiveMeaningSummary(text, num_sentences);
						final Path summary_path = output_path.resolve(basename + "_extrmeaning" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getExtractiveMentionSummary(text, num_sentences);
						final Path summary_path = output_path.resolve(basename + "_extrmention" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}

					{
						final String summary = getExtractivePlanSummary(text, num_words);
						final Path summary_path = output_path.resolve(basename + "_extrplan" + summary_extension);
						FileUtils.writeTextToFile(summary_path, summary);
					}
				}

				log.info("Text finished in " + timer.stop());
			});
		}

		log.info("Corpus finished in " + gtimer.stop());
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

	private static String getBoMSummary(EvaluationCorpus.Text text, long num_words, Function<Mention, String> print_context)
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
				.map(c -> c.getMention().toString() + "\t" + print_context.apply(c.getMention()))
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

		final String summary = sorted_candidates.stream()
				.map(Candidate::getMention)
				.map(Mention::getLemma)
				.distinct()
				.limit(num_words)
				.collect(joining(" "));

		final String candidates_str = sorted_candidates.stream()
				.limit(num_words)
				.map(c -> c.getMention().toString() + "\t" + print_context.apply(c.getMention()))
				.collect(joining("\n\t"));
		log.info("BoW summary: " + summary + "\n");
		log.debug("BoW summary:\n\t" + candidates_str + "\n");

		return summary;
	}

	private static String getExtractiveMeaningSummary(EvaluationCorpus.Text text, int num_sentences)
	{
		final Map<Sentence, List<Candidate>> sentences2meanings = text.sentences.stream()
				.collect(toMap(s -> s, s -> s.disambiguated.values().stream()
						.filter(c -> c.getWeight().isPresent())
						.collect(toList())));

		// Extractive meaning summary
		final Map<Sentence, String> sentences2weightlists = sentences2meanings.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.map(c -> c.getMention().getSurfaceForm() + " " +
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

		final List<String> summary_sentences = sorted_sentences.stream()
				.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
				.limit(num_sentences)
				.collect(toList());

		log.debug("Extractive meaning summary:" + debug);
		log.info("Extractive meaning summary:\n\t" + String.join("\n\t", summary_sentences) + "\n");

		return String.join("\n", summary_sentences);

	}

	private static String getExtractiveMentionSummary(EvaluationCorpus.Text text, int num_sentences)
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
						.map(m -> m.getSurfaceForm() + " " + DebugUtils.printDouble(m.getWeight().get()))
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

		final List<String> summary_sentences = sorted_sentences.stream()
				.map(sentence -> sentence.tokens.stream().map(t -> t.wf).collect(joining(" ")))
				.limit(num_sentences)
				.collect(toList());

		log.debug("Extractive mention summary:" + debug);
		log.info("Extractive mention summary:\n\t" + String.join("\n\t", summary_sentences) + "\n");

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

