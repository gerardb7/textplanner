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
		options.damping_meanings = 0.99; // controls balance between bias and similarity: higher value -> more bias

		// Exclude Tag from mention collection
		final Set<POS.Tag> excluded_mention_POS = Set.of(POS.Tag.X);
		// Include these Tag in the ranking of meanings
		options.ranking_POS_Tags = Set.of(POS.Tag.NOUN); //, POS.Tag.ADJ); //, POS.Tag.VERB, POS.Tag.ADV);
		// Evaluate these Tag tags only
		Set<POS.Tag> evaluate_POS = options.ranking_POS_Tags; // Only evaluate ranked POS to avoid bias against full rank versus frequency and context

		final Map<String, Set<AlternativeMeanings>> gold = parseGoldFile(gold_file);
		final Corpus corpus = EvaluationCorpus.createFromXML(xml_file);
		EvaluationTools.createResources(corpus, tagset, resources_factory, max_span_size, rank_together, excluded_mention_POS, options);

		corpus.texts.forEach(text -> EvaluationTools.rankMeanings(text, options));
		corpus.texts.forEach(text -> EvaluationTools.disambiguate(text, options));

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
			log.info("Frequency rank MAP = " + DebugUtils.printDouble(map));
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

	private static double evaluate(Map<String, List<Meaning>> system, Map<String, Set<AlternativeMeanings>> gold, Set<POS.Tag> eval_POS)
	{
		List<Double> avg_precisions = new ArrayList<>();
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

			final OptionalDouble average = rank_precisions.values().stream().mapToDouble(d -> d).average();
			if (average.isEmpty())
				log.error("Cannot calculate map for " + text_id);
			else
			{
				avg_precisions.add(average.getAsDouble());
				log.info("\t" + text_id + ": " + DebugUtils.printDouble(average.getAsDouble()));
			}
		});

		return avg_precisions.stream().mapToDouble(d -> d).average().orElse(0.0);
	}
}
