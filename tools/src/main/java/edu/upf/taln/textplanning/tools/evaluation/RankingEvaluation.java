package edu.upf.taln.textplanning.tools.evaluation;

import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.AlternativeMeanings;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus;
import edu.upf.taln.textplanning.tools.evaluation.corpus.EvaluationCorpus.Corpus;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

public class RankingEvaluation
{
	public RankingEvaluation() {}

	private static final int max_span_size = 3;
	private static final boolean rank_together = false;
	private static final POS.Tagset tagset = POS.Tagset.Simple;
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_file, Path xml_file, InitialResourcesFactory resources_factory)
	{
		final Options options = new Options();
		options.min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
		options.min_bias_threshold = 0.8; // minimum bias value below which candidate meanings are ignored
		options.num_first_meanings = 1;
		options.sim_threshold = 0.8; // Pairs of meanings with sim below this value have their score set to 0
		options.damping_meanings = 0.4; // controls balance between bias and similarity: higher value -> more bias

		// Exclude Tag from mention collection
		final Set<POS.Tag> excluded_mention_POS = Set.of(POS.Tag.X);
		// Include these Tag in the ranking of meanings
		options.ranking_POS_Tags = Set.of(POS.Tag.NOUN, POS.Tag.ADJ, POS.Tag.VERB, POS.Tag.ADV); // ranking MUST include all; let threshold params filter out suprious meanings
		// Evaluate these Tag tags only
		Set<POS.Tag> evaluate_POS = Set.of(POS.Tag.NOUN, POS.Tag.ADJ, POS.Tag.VERB, POS.Tag.ADV);
		log.info("***\n" + options + "\n***");


		final Map<String, Set<AlternativeMeanings>> gold = parseGoldFile(gold_file);
		final Corpus corpus = EvaluationCorpus.createFromXML(xml_file);
		EvaluationTools.createResources(corpus, tagset, resources_factory, max_span_size, rank_together, excluded_mention_POS, options);

		corpus.texts.forEach(text -> EvaluationTools.rankMeanings(text, options));
		corpus.texts.forEach(text -> EvaluationTools.disambiguate(text, options));

		log.info("\n********************************");
		{
			final int num_runs = 10;
			final List<Pair<Double, Double>> pairs = IntStream.range(0, num_runs)
					.mapToObj(i ->
					{
						final Map<String, List<Meaning>> system_meanings = randomRank(corpus);
						final Map<String, Pair<Double, Double>> values = evaluate(system_meanings, gold, evaluate_POS);
						final double map_i = values.values().stream()
								.mapToDouble(Pair::getLeft)
								.average()
								.orElse(0.0);
						final double limited_map_i = values.values().stream()
								.mapToDouble(Pair::getRight)
								.average()
								.orElse(0.0);
						return Pair.of(map_i, limited_map_i);

					})
					.collect(toList());
			final double map = pairs.stream()
					.mapToDouble(Pair::getLeft)
					.average().orElse(0.0);
			final double limited_map = pairs.stream()
					.mapToDouble(Pair::getRight)
					.average().orElse(0.0);

			log.info("Random rank (" + num_runs + " runs) MAP = " + DebugUtils.printDouble(map) +
					" limited MAP = " + DebugUtils.printDouble(limited_map));
		}
		{
			final Map<String, List<Meaning>> system_meanings = frequencyRank(corpus);
			final Map<String, Pair<Double, Double>> avg_precisions = evaluate(system_meanings, gold, evaluate_POS);
			final double map = avg_precisions.values().stream().mapToDouble(Pair::getLeft).average().orElse(Double.NaN);
			final double limited_map = avg_precisions.values().stream().mapToDouble(Pair::getRight).average().orElse(Double.NaN);
			log.info("Frequency rank MAP = " + DebugUtils.printDouble(map) + " limited MAP = " + DebugUtils.printDouble(limited_map));
			avg_precisions.keySet().stream().sorted().forEach(text_id -> {
				final Pair<Double, Double> averages = avg_precisions.get(text_id);
				log.info("\t" + text_id + ": " + DebugUtils.printDouble(averages.getLeft()) +
						"\t" + DebugUtils.printDouble(averages.getRight()));
			});
		}
		{
			final Map<String, List<Meaning>> system_meanings = contextRank(corpus);
			final Map<String, Pair<Double, Double>> avg_precisions = evaluate(system_meanings, gold, evaluate_POS);
			final double map = avg_precisions.values().stream().mapToDouble(Pair::getLeft).average().orElse(Double.NaN);
			final double limited_map = avg_precisions.values().stream().mapToDouble(Pair::getRight).average().orElse(Double.NaN);
			log.info("Context rank MAP = " + DebugUtils.printDouble(map) + " limited MAP = " + DebugUtils.printDouble(limited_map));
			avg_precisions.keySet().stream().sorted().forEach(text_id -> {
				final Pair<Double, Double> averages = avg_precisions.get(text_id);
				log.info("\t" + text_id + ": " + DebugUtils.printDouble(averages.getLeft()) +
						"\t" + DebugUtils.printDouble(averages.getRight()));
			});
		}
		{
			final Map<String, List<Meaning>> system_meanings = fullRank(corpus);
			final Map<String, Pair<Double, Double>> avg_precisions = evaluate(system_meanings, gold, evaluate_POS);
			final double map = avg_precisions.values().stream().mapToDouble(Pair::getLeft).average().orElse(Double.NaN);
			final double limited_map = avg_precisions.values().stream().mapToDouble(Pair::getRight).average().orElse(Double.NaN);
			log.info("Full rank MAP = " + DebugUtils.printDouble(map) + " limited MAP = " + DebugUtils.printDouble(limited_map));
			avg_precisions.keySet().stream().sorted().forEach(text_id -> {
				final Pair<Double, Double> averages = avg_precisions.get(text_id);
				log.info("\t" + text_id + ": " + DebugUtils.printDouble(averages.getLeft()) +
						"\t" + DebugUtils.printDouble(averages.getRight()));
			});
		}
		log.info("********************************\n");

		EvaluationTools.printMeaningRankings(corpus, gold, false, evaluate_POS);
	}

	private static Map<String, Set<AlternativeMeanings>> parseGoldFile(Path gold_file)
	{
		log.info("Parsing gold file");
		return Arrays.stream(FileUtils.readTextFile(gold_file).split("\n"))
				.filter(not(String::isEmpty))
				.filter(l -> !l.startsWith("@text"))
				.map(l -> l.split("\t"))
				.peek(a ->
				{ if (a.length != 4) log.error("Can't parse line " + String.join(" ", a)); })
				.filter(a -> a.length == 4)
				.map(a -> Arrays.stream(a).map(String::trim).collect(toList()))
				.map(a -> Pair.of(a.get(0).substring(0, 4), new AlternativeMeanings(Arrays.asList(a.get(2).split("\\|")),
						a.get(3).substring(1, a.get(3).length() - 1), a.get(0), a.get(1))))
				.collect(groupingBy(Pair::getLeft, mapping(Pair::getRight, toSet())));
	}

	private static Map<String, List<Meaning>> randomRank(Corpus corpus)
	{
		final Random random = new Random();
		return corpus.texts.stream()
				.collect(toMap(text -> text.id, text ->
				{
					final List<Meaning> meanings = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream().flatMap(List::stream)
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
					final List<Candidate> candidates = text.sentences.stream()
							.flatMap(sentence -> sentence.candidates.values().stream().flatMap(List::stream))
							.collect(toList());
					Function<Candidate, Long> frequency = c -> candidates.stream().filter(c2 -> c2 == c).count();
					return candidates.stream()
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
							.flatMap(sentence -> sentence.candidates.values().stream().flatMap(List::stream))
							.map(Candidate::getMeaning)
							.collect(toList());

					final BiasFunction bias = corpus.resouces != null ?
							corpus.resouces.getBiasFunction() : text.resources.getBiasFunction();
					Map<Meaning, Double> weights = meanings.stream()
							.collect(groupingBy(m -> m, averagingDouble(m -> bias.apply(m.getReference()))));

					return meanings.stream()
							.distinct()
							.filter(m -> weights.get(m) > 0.0)
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
							.flatMap(sentence -> sentence.candidates.values().stream().flatMap(List::stream))
							.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> c.getWeight().orElse(0.0))));
					final List<Meaning> file_meanings = new ArrayList<>(weights.keySet());
					return file_meanings.stream()
							.filter(m -> weights.get(m) > 0.0)
							.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
							.collect(toList());
				}));
	}

	private static Map<String, Pair<Double, Double>> evaluate(Map<String, List<Meaning>> system, Map<String, Set<AlternativeMeanings>> gold, Set<POS.Tag> eval_POS)
	{
		Map<String, Pair<Double, Double>> avg_precisions = new HashMap<>();
		system.forEach((text_id, meanings) ->
		{
			final List<String> system_set = meanings.stream().map(Meaning::getReference).collect(toList());
			final Set<String> gold_set = gold.get(text_id).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());

			Predicate<String> filter_by_POS = m ->
					(m.endsWith("n") && eval_POS.contains(POS.Tag.NOUN)) ||
					(m.endsWith("v") && eval_POS.contains(POS.Tag.VERB)) ||
					(m.endsWith("a") && eval_POS.contains(POS.Tag.ADJ)) ||
					(m.endsWith("r") && eval_POS.contains(POS.Tag.ADV));

			// Determine the actual subset of the gold to be used in the evaluation
			final Set<String> evaluated_gold = gold_set.stream()
					.filter(filter_by_POS)
					.collect(toSet());
			final long num_evaluated_gold = gold.get(text_id).stream()
					.filter(s -> s.alternatives.stream()
							.anyMatch(filter_by_POS))
					.count();

			// Now determine the subset of the system candidates to be evaluated
			final List<String> evaluated_system = system_set.stream()
					.filter(filter_by_POS)
					.collect(toList());

			int true_positives_partial = 0;
			int false_positives_partial = 0;
			List<Double> rank_precisions = new ArrayList<>();
			List<Double> limited_rank_precisions = new ArrayList<>();

			for (int k = 0; k < evaluated_system.size(); ++k)
			{
				if (evaluated_gold.contains(evaluated_system.get(k)))
				{
					final double p = (double) ++true_positives_partial / (double) (true_positives_partial + false_positives_partial);
					rank_precisions.add(p);
					if (k < num_evaluated_gold)
						limited_rank_precisions.add(p);
				}
				else
					++false_positives_partial;
			}

			final double average = rank_precisions.stream().mapToDouble(d -> d).average().orElse(0.0);
			final double lim_average = limited_rank_precisions.stream().mapToDouble(d -> d).average().orElse(0.0);
			avg_precisions.put(text_id, Pair.of(average, lim_average));
		});

		return avg_precisions;
	}
}
