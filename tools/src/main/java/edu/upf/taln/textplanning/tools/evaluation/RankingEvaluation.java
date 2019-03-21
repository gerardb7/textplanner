package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.ResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.ranking.StopWordsFilter;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static edu.upf.taln.textplanning.tools.evaluation.EvaluationTools.adverb_pos_tag;
import static java.util.stream.Collectors.*;

public class RankingEvaluation
{
	public static class AlternativeMeanings
	{
		private final Set<String> alternatives = new HashSet<>();
		private final String text; // covered text or label

		private AlternativeMeanings(Collection<String> meanings, String text)
		{
			alternatives.addAll(meanings);
			this.text = text;
		}

		private static boolean match(AlternativeMeanings a1, AlternativeMeanings a2)
		{
			return !Collections.disjoint(a1.alternatives, a2.alternatives);
		}
	}

	private static final int max_span_size = 3;
	private static final ULocale language = ULocale.ENGLISH;
	private static final String gold_suffix = ".gold";
	private final static Logger log = LogManager.getLogger();

	public static void run(Path gold_folder, Path xml_file, Path output_path, ResourcesFactory resources_factory) throws Exception
	{
		final List<Set<AlternativeMeanings>> goldMeanings = getGoldMeanings(gold_folder);
		final Set<String> excludedPOSTags = Set.of(EvaluationTools.other_pos_tag, adverb_pos_tag);
		final Resources test_resources = EvaluationTools.loadResources(xml_file, output_path,
				resources_factory, language, max_span_size, excludedPOSTags);
		assert goldMeanings.size() == test_resources.candidates.size();

		{
			log.info("********************************");
			log.info("Testing random rank");
			final List<List<Meaning>> system_meanings = randomRank(test_resources);
			final double map = evaluate(system_meanings, goldMeanings);
			log.info("MAP = " + DebugUtils.printDouble(map));
		}

		{
			log.info("********************************");
			log.info("Testing context rank");
			final List<List<Meaning>> system_meanings = contextRank(test_resources, excludedPOSTags);
			final double map = evaluate(system_meanings, goldMeanings);
			log.info("MAP = " + DebugUtils.printDouble(map));
		}
		{
			log.info("********************************");
			final Options options = new Options();
			log.info("Testing full rank: " + options);
			final List<List<Meaning>> system_meanings = fullRank(options, test_resources, resources_factory, excludedPOSTags);
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

	private static List<List<Meaning>> randomRank(Resources test_resources)
	{
		Random r = new Random();
		List<List<Meaning>> meanings = new ArrayList<>();
		for (int i = 0; i < test_resources.corpus.texts.size(); ++i)
		{
			final List<Meaning> text_candidates = test_resources.candidates.get(i).stream()
					.flatMap(l -> l.stream()
							.flatMap(Collection::stream))
					.map(Candidate::getMeaning)
					.collect(toList());
			Collections.shuffle(text_candidates, new Random());
			meanings.add(text_candidates);
		}

		return meanings;
	}

	private static List<List<Meaning>> contextRank(Resources test_resources, Set<String> excludedPOSTags)
	{

		List<List<Meaning>> meanings = new ArrayList<>();
		for (int i = 0; i < test_resources.corpus.texts.size(); ++i)
		{
			final StopWordsFilter stop_filter = new StopWordsFilter(language);
			final Predicate<Candidate> pos_filter =	c ->  !excludedPOSTags.contains(c.getMention().getPOS());
			final List<Candidate> text_candidates = test_resources.candidates.get(i).stream()
					.flatMap(l -> l.stream()
							.flatMap(Collection::stream))
					.filter(stop_filter.and(pos_filter))
					.collect(toList());

			final Function<String, Double> weighter = test_resources.weighters.get(i);
			Map<Meaning, Double> weights = text_candidates.stream()
					.collect(groupingBy(Candidate::getMeaning, averagingDouble(c -> weighter.apply(c.getMeaning().getReference()))));

			final List<Meaning> ranked_meanings = text_candidates.stream()
					.map(Candidate::getMeaning)
					.distinct()
					.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
					.collect(toList());
			meanings.add(ranked_meanings);
		}

		return meanings;
	}

	private static List<List<Meaning>> fullRank(Options options, Resources test_resources,
	                                            ResourcesFactory resources_factory, Set<String> excludedPOSTags)
	{
		EvaluationTools.full_rank(options, test_resources.candidates, test_resources.weighters, resources_factory,
				excludedPOSTags);

		List<List<Meaning>> meanings = new ArrayList<>();
		for (List<List<List<Candidate>>> text_candidates : test_resources.candidates)
		{
			Map<Meaning, Double> weights = text_candidates.stream()
					.flatMap(l -> l.stream()
							.flatMap(Collection::stream))
					.collect(groupingBy(Candidate::getMeaning, averagingDouble(Candidate::getWeight)));
			final List<Meaning> file_meanings = new ArrayList<>(weights.keySet());
			final List<Meaning> ranked_meanings = file_meanings.stream()
					.filter(m -> weights.get(m) > 0.0)
					.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
					.collect(toList());
			meanings.add(ranked_meanings);
		}

		return meanings;
	}

	private static double evaluate(List<List<Meaning>> systemMeanings, List<Set<AlternativeMeanings>> goldMeanings)
	{
		List<Double> avg_precisions = new ArrayList<>();
		for (int i = 0; i < goldMeanings.size(); ++i)
		{
			final List<Meaning> system_set = systemMeanings.get(i);
			final Set<String> gold_set = goldMeanings.get(i).stream()
					.flatMap(a -> a.alternatives.stream())
					.collect(toSet());

			int true_positives = 0;
			int false_positives = 0;
			Map<Integer, Double> rank_precisions = new HashMap<>();
			for (int k = 0; k < system_set.size(); ++k)
			{
				if (gold_set.contains(system_set.get(k).getReference()))
				{
					final double p = (double) ++true_positives / (double) (true_positives + false_positives);
					rank_precisions.put(k, p);
				}
				else
					++false_positives;
			}
			rank_precisions.values().stream().mapToDouble(d -> d).average().ifPresent(avg_precisions::add);
		}

		return avg_precisions.stream().mapToDouble(d -> d).average().orElse(0.0);
	}
}
