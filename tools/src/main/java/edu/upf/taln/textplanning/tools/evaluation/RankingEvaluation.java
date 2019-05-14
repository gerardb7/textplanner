package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.bias.BiasFunction;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
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
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static java.util.stream.Collectors.*;

public class RankingEvaluation
{
	public RankingEvaluation() {}

	private static Set<String> evaluate_POS;
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

	public static void run(Path gold_folder, Path xml_file, Path output_path, InitialResourcesFactory resources_factory)
	{
		final Options options = new Options();

		// Exclude POS from mention collection
		final Set<String> excluded_mention_POS = Set.of(other_pos_tag);
		// Include these POS in the ranking of meanings
		options.ranking_POS_Tags = Set.of(noun_pos_tag, adj_pos_tag, verb_pos_tag, adverb_pos_tag);
		// Evaluate these POS tags only
		evaluate_POS = Set.of(noun_pos_tag, adj_pos_tag, verb_pos_tag, adverb_pos_tag);

		final List<Set<AlternativeMeanings>> goldMeanings = getGoldMeanings(gold_folder);
		final Corpus corpus = EvaluationTools.loadResourcesFromXML(xml_file, output_path,
				resources_factory, language, max_span_size, rank_together, noun_pos_tag, excluded_mention_POS, options);
		assert goldMeanings.size() == corpus.texts.size();

		{
			log.info("********************************");
			log.info("Testing random rank");
			final List<List<Meaning>> system_meanings = randomRank(corpus);
			final double map = evaluate(system_meanings, goldMeanings);
			log.info("MAP = " + DebugUtils.printDouble(map));
		}

		{
			log.info("********************************");
			log.info("Testing context rank");
			final List<List<Meaning>> system_meanings = contextRank(corpus);
			final double map = evaluate(system_meanings, goldMeanings);
			log.info("MAP = " + DebugUtils.printDouble(map));
		}
		{
			log.info("********************************");
			log.info("Testing full rank");
			final List<List<Meaning>> system_meanings = fullRank(options, corpus);
			final double map = evaluate(system_meanings, goldMeanings);
			log.info("MAP = " + DebugUtils.printDouble(map));
		}

	}

	private static List<Set<AlternativeMeanings>> getGoldMeanings(Path gold_folder)
	{
		log.info("Parsing gold files");
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

		final String regex = "(bn:\\d{8}[r|a|v|n](\\|bn:\\d{8}[r|a|v|n])*)-\"([^\"]+)\"";
		final Pattern pattern = Pattern.compile(regex);
		final Predicate<String> is_meanings = pattern.asPredicate();

		final List<Set<AlternativeMeanings>> gold = lines.stream()
				.map(l -> l.stream()
						.filter(is_meanings)
						.map(pattern::matcher)
						.map(m -> {
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

		log.info(gold.stream()
				.flatMap(Set::stream)
				.mapToLong(l -> l.alternatives.size())
				.sum() + " meanings read from gold");

		return gold;
	}

	private static List<List<Meaning>> randomRank(Corpus corpus)
	{
		final Random random = new Random();
		return corpus.texts.stream()
				.map(t -> {
					final List<Meaning> meanings = t.sentences.stream()
							.flatMap(s -> s.candidates.values().stream())
							.flatMap(Collection::stream)
							.map(Candidate::getMeaning)
							.collect(toList());
					Collections.shuffle(meanings, random);
					return meanings;
				})
				.collect(toList());
	}

	private static List<List<Meaning>> contextRank(Corpus corpus)
	{
		return corpus.texts.stream()
				.map(t -> {
					// use same filters as in InitialResourcesFactory::getCandidatesFilter
					Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurface_form(), language);

					final List<Meaning> meanings = t.sentences.stream()
							.flatMap(s -> s.candidates.values().stream())
							.flatMap(Collection::stream)
							.filter(function_words_filter)
							.map(Candidate::getMeaning)
							.collect(toList());

					final BiasFunction bias = corpus.resouces != null ? corpus.resouces.getBiasFunction() : t.resources.getBiasFunction();
					Map<Meaning, Double> weights = meanings.stream()
							.collect(groupingBy(m -> m, averagingDouble(m -> bias.apply(m.getReference()))));

					return meanings.stream()
							.distinct()
							.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
							.collect(toList());
				})
				.collect(toList());
	}

	private static List<List<Meaning>> fullRank(Options options, Corpus corpus)
	{
		EvaluationTools.rankMeanings(options, corpus);

		return corpus.texts.stream()
				.map(t -> {
					// Filtering already done in EvaluationTools.rankMeanings
					Map<Meaning, Double> weights = t.sentences.stream()
							.flatMap(s -> s.candidates.values().stream())
							.flatMap(Collection::stream)
							.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> c.getWeight().orElse(0.0))));
					final List<Meaning> file_meanings = new ArrayList<>(weights.keySet());
					return file_meanings.stream()
							.filter(m -> weights.get(m) > 0.0)
							.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
							.collect(toList());
				})
				.collect(toList());
	}

	private static double evaluate(List<List<Meaning>> systemMeanings, List<Set<AlternativeMeanings>> goldMeanings)
	{
		List<Double> avg_precisions = new ArrayList<>();
		for (int i = 0; i < goldMeanings.size(); ++i)
		{
			final List<String> system_set = systemMeanings.get(i).stream().map(Meaning::getReference).collect(toList());
			final Set<String> gold_set = goldMeanings.get(i).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());

			int true_positives_partial = 0;
			int false_positives_partial = 0;
			Map<Integer, Double> rank_precisions = new HashMap<>();
			for (int k = 0; k < system_set.size(); ++k)
			{
				if (gold_set.contains(system_set.get(k)))
				{
					final double p = (double) ++true_positives_partial / (double) (true_positives_partial + false_positives_partial);
					rank_precisions.put(k, p);
				}
				else
					++false_positives_partial;
			}

			rank_precisions.values().stream().mapToDouble(d -> d).average().ifPresent(avg_precisions::add);

		}

		return avg_precisions.stream().mapToDouble(d -> d).average().orElse(0.0);
	}

}
