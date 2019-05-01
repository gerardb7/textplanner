package edu.upf.taln.textplanning.tools.evaluation;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.core.Options;
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


	private static final int max_span_size = 3;
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
		final List<Set<AlternativeMeanings>> goldMeanings = getGoldMeanings(gold_folder);
		options.excluded_POS_Tags = new HashSet<>(Arrays.asList(other_pos_tag, adverb_pos_tag)); 
		//options.excluded_POS_Tags = Collections.unmodifiableSet(options.excluded_POS_Tags); 
		final Corpus corpus = EvaluationTools.loadResourcesFromXML(xml_file, output_path,
				resources_factory, language, max_span_size, noun_pos_tag, options);
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
			final List<List<Meaning>> system_meanings = contextRank(corpus, options.excluded_POS_Tags);
			final double map = evaluate(system_meanings, goldMeanings);
			log.info("MAP = " + DebugUtils.printDouble(map));
		}
		{
			log.info("********************************");
			log.info("Testing full rank");
			final List<List<Meaning>> system_meanings = fullRank(options, corpus, resources_factory);
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

	private static List<List<Meaning>> contextRank(Corpus corpus, Set<String> excludedPOSTags)
	{
		return corpus.texts.stream()
				.map(t -> {
					// use same filters as in InitialResourcesFactory::getCandidatesFilter
					Predicate<Candidate> function_words_filter = (c) -> FunctionWordsFilter.test(c.getMention().getSurface_form(), language);
					final Predicate<Candidate> pos_filter =	c ->  !excludedPOSTags.contains(c.getMention().getPOS());

					final List<Meaning> meanings = t.sentences.stream()
							.flatMap(s -> s.candidates.values().stream())
							.flatMap(Collection::stream)
							.filter(function_words_filter.and(pos_filter))
							.map(Candidate::getMeaning)
							.collect(toList());

					Map<Meaning, Double> weights = meanings.stream()
							.collect(groupingBy(m -> m, averagingDouble(m -> t.resources.getMeaningsWeighter().apply(m.getReference()))));

					return meanings.stream()
							.distinct()
							.sorted(Comparator.<Meaning>comparingDouble(weights::get).reversed())
							.collect(toList());
				})
				.collect(toList());
	}

	private static List<List<Meaning>> fullRank(Options options, Corpus corpus,
	                                            InitialResourcesFactory resources_factory)
	{
		EvaluationTools.rankMeanings(options, corpus, resources_factory.getMeaningsSimilarity());

		return corpus.texts.stream()
				.map(t -> {
					// Filtering already done in EvaluationTools.rankMeanings
					Map<Meaning, Double> weights = t.sentences.stream()
							.flatMap(s -> s.candidates.values().stream())
							.flatMap(Collection::stream)
							.collect(groupingBy(Candidate::getMeaning, averagingDouble(Candidate::getWeight)));
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
