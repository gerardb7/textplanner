package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.AlternativeMeanings;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static java.util.stream.Collectors.*;

public class RankingEvaluation
{
	public RankingEvaluation() {}

	private static final int max_span_size = 3;
	private static final boolean rank_together = false;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String noun_pos_tag = "N";
	private static final String adj_pos_tag = "J";
	private static final String verb_pos_tag = "V";
	private static final String adverb_pos_tag = "R";
	private static final String other_pos_tag = "X";
	private static final String gold_suffix = ".gold";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_folder, Path xml_file, InitialResourcesFactory resources_factory)
	{
		final Options options = new Options();
		options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		options.min_bias_threshold = 0.8; // minimum bias value below which candidate meanings are ignored
		options.num_first_meanings = 1;
		options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		options.damping_meanings = 0.5; // controls balance between bias and similarity: higher value -> more bias

		// Exclude POS from mention collection
		final Set<String> excluded_mention_POS = Set.of(other_pos_tag);
		// Include these POS in the ranking of meanings
		options.ranking_POS_Tags = Set.of(noun_pos_tag); //, adj_pos_tag, verb_pos_tag, adverb_pos_tag);
		// Evaluate these POS tags only
		Set<String> evaluate_POS = Set.of(noun_pos_tag); //, adj_pos_tag, verb_pos_tag, adverb_pos_tag);

		final Map<String, Set<AlternativeMeanings>> gold = parseGoldMeanings(gold_folder);
		final Corpus corpus = EvaluationTools.loadResourcesFromXML(xml_file, resources_factory, language, max_span_size, rank_together, noun_pos_tag, excluded_mention_POS, options);
		assert gold.size() == corpus.texts.size();

		EvaluationTools.rankMeanings(options, corpus);
		EvaluationTools.disambiguate(corpus);

//		final Path disamb_file = resources_path.resolve("meanings_" + language.toLanguageTag() + ".txt");
//		EvaluationTools.writeDisambiguatedResultsToFile(corpus, disamb_file);

		log.info("\n********************************");
		{
			final int num_runs = 10;
			final double map = IntStream.range(0, num_runs)
					.mapToDouble(i ->
							{
								final Map<String, List<Meaning>> system_meanings = randomRank(corpus);
								return evaluate(system_meanings, gold, evaluate_POS);
							})
					.average().orElse(0.0);
				log.info("Random rank (" + num_runs + " runs) MAP = " + DebugUtils.printDouble(map));
		}
		{
			final Map<String, List<Meaning>> system_meanings = frequencyRank(corpus);
			final double map = evaluate(system_meanings, gold, evaluate_POS);
			log.info("First sense + frequency rank MAP = " + DebugUtils.printDouble(map));
		}
		{
			final Map<String, List<Meaning>> system_meanings = contextRank(corpus);
			final double map = evaluate(system_meanings, gold, evaluate_POS);
			log.info("Context rank MAP = " + DebugUtils.printDouble(map));
		}
		{
			final Map<String, List<Meaning>> system_meanings = fullRank(corpus);
			final double map = evaluate(system_meanings, gold, evaluate_POS);
			log.info("Full rank MAP = " + DebugUtils.printDouble(map));
		}
		log.info("********************************\n");

		final Map<String, Set<String>> gold_meanings = gold.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.flatMap(a -> a.alternatives.stream())
						.collect(toSet())));
		EvaluationTools.printMeaningRankings(corpus, gold_meanings, false, evaluate_POS);
	}

	private static Map<String, Set<AlternativeMeanings>> parseGoldMeanings(Path gold_folder)
	{
		log.info("Parsing gold_meanings files");
		final File[] gold_files = getFilesInFolder(gold_folder, gold_suffix);
		assert gold_files != null;

		final List<List<String>> lines = Arrays.stream(gold_files)
				.sorted(Comparator.comparing(File::getName))
				.map(File::toPath)
				.map(FileUtils::readTextFile)
				.map(text ->
						Pattern.compile("\n+").splitAsStream(text)
								.filter(l -> !l.isEmpty())
								.collect(toList()))
				.collect(toList());

//		final Predicate<String> is_meta = Pattern.compile("^@").asPredicate();
//		final List<String> summaries = lines.stream()
//				.map(f -> f.stream()
//						.filter(not(is_meta))
//						.collect(joining(" .\n")))
//				.collect(toList());
		final String doc_regex = "@text\\s+(d\\d{3})";
		final Pattern doc_pattern = Pattern.compile(doc_regex);
		final Predicate<String> is_doc = doc_pattern.asPredicate();

		final List<String> doc_ids = lines.stream()
				.map(doc_lines -> doc_lines.stream()
						.filter(is_doc)
						.findFirst()
						.map(doc_pattern::matcher)
						.filter(Matcher::matches)
						.map(m -> m.group(1))
						.orElseThrow())
				.collect(toList());

		final String regex = "(bn:\\d{8}[r|a|v|n](\\|bn:\\d{8}[r|a|v|n])*)-\"([^\"]+)\"";
		final Pattern pattern = Pattern.compile(regex);
		final Predicate<String> is_meanings = pattern.asPredicate();

		final List<Set<AlternativeMeanings>> gold_meanings = lines.stream()
				.map(doc_lines -> doc_lines.stream()
						.filter(is_meanings)
						.map(pattern::matcher)
						.map(m ->
						{
							final List<AlternativeMeanings> meanings = new ArrayList<>();
							while (m.find())
							{
								final String meanings_string = m.group(1);
								final String[] meanings_parts = meanings_string.split("\\|");
								final Set<String> alternatives = Arrays.stream(meanings_parts)
										.map(p -> p.startsWith("\"") && p.endsWith("\"") ? p.substring(1, p.length() - 1) : p)
										.collect(toSet());
								final String covered_text = m.group(3);
								meanings.add(new AlternativeMeanings(alternatives, covered_text));
							}

							return meanings;
						})
						.flatMap(List::stream)
						.collect(toSet()))
				.collect(toList());

		log.info(gold_meanings.stream()
				.flatMap(Set::stream)
				.mapToLong(l -> l.alternatives.size())
				.sum() + " meanings read from gold_meanings");

		return IntStream.range(0, doc_ids.size())
				.boxed()
				.collect(toMap(doc_ids::get, gold_meanings::get));
	}

	private static Map<String, List<Meaning>> randomRank(Corpus corpus)
	{
		final Random random = new Random();
		return corpus.texts.stream()
				.collect(toMap(text -> text.id, text ->
				{
					final List<Meaning> meanings = text.sentences.stream()
							.flatMap(sentence -> sentence.disambiguated.values().stream()
								.map(Candidate::getMeaning))
							.collect(toList());
					Collections.shuffle(meanings, random);
					return meanings;
				}));
	}

	// Frequency-based ranking
	private static Map<String, List<Meaning>> frequencyRank(Corpus corpus)
	{
		return corpus.texts.stream()
				.collect(toMap(text -> text.id, text ->
				{
					final List<Candidate> disambiguated = text.sentences.stream()
							.flatMap(sentence -> sentence.disambiguated.values().stream())
							.collect(toList());
					Function<Candidate, Long> frequency = c -> disambiguated.stream().filter(c2 -> c2 == c).count();
					return disambiguated.stream()
							.sorted(Comparator.comparingLong(frequency::apply).reversed())
							.map(Candidate::getMeaning)
							.collect(toList());
				}));
	}

	private static Map<String, List<Meaning>> contextRank(Corpus corpus)
	{
		return corpus.texts.stream()
				.collect(toMap(text -> text.id, text ->
				{
					final List<Meaning> meanings = text.sentences.stream()
							.flatMap(sentence -> sentence.disambiguated.values().stream())
							.map(Candidate::getMeaning)
							.collect(toList());

					final BiasFunction bias = corpus.resouces != null ?
							corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();
					Map<Meaning, Double> weights = meanings.stream()
							.collect(groupingBy(m -> m, averagingDouble(m -> bias.apply(m.getReference()))));

					return meanings.stream()
							.distinct()
							.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
							.collect(toList());
				}));
	}

	private static Map<String, List<Meaning>> fullRank(Corpus corpus)
	{
		return corpus.texts.stream()
				.collect(toMap(text -> text.id, text ->
				{
					Map<Meaning, Double> weights = text.sentences.stream()
							.flatMap(sentence -> sentence.disambiguated.values().stream())
							.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> c.getWeight().orElse(0.0))));
					final List<Meaning> file_meanings = new ArrayList<>(weights.keySet());
					return file_meanings.stream()
							.filter(m -> weights.get(m) > 0.0)
							.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
							.collect(toList());
				}));
	}

	private static double evaluate(Map<String, List<Meaning>> system, Map<String, Set<AlternativeMeanings>> gold, Set<String> POS)
	{
		List<Double> avg_precisions = new ArrayList<>();
		system.forEach((text_id, meanings) ->
		{
			final List<String> system_set = meanings.stream().map(Meaning::getReference).collect(toList());
			final Set<String> gold_set = gold.get(text_id).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());

			Predicate<String> filter_by_POS = m ->
					(m.endsWith("n") && POS.contains(noun_pos_tag)) ||
					(m.endsWith("v") && POS.contains(verb_pos_tag)) ||
					(m.endsWith("a") && POS.contains(adj_pos_tag)) ||
					(m.endsWith("r") && POS.contains(adverb_pos_tag));

			// Determine the actual subset of the gold to be used in the evaluation
			final Set<String> evaluated_gold = gold_set.stream()
					.filter(filter_by_POS)
					.collect(toSet());

			// Now determine the subset of the system candidates to be evaluated
			final List<String> evaluated_system = system_set.stream()
					.filter(filter_by_POS)
					.collect(toList());

			int true_positives_partial = 0;
			int false_positives_partial = 0;
			Map<Integer, Double> rank_precisions = new HashMap<>();
			for (int k = 0; k < evaluated_system.size(); ++k)
			{
				if (evaluated_gold.contains(evaluated_system.get(k)))
				{
					final double p = (double) ++true_positives_partial / (double) (true_positives_partial + false_positives_partial);
					rank_precisions.put(k, p);
				}
				else
					++false_positives_partial;
			}

			rank_precisions.values().stream().mapToDouble(d -> d).average().ifPresent(avg_precisions::add);
		});

		return avg_precisions.stream().mapToDouble(d -> d).average().orElse(0.0);
	}
}
