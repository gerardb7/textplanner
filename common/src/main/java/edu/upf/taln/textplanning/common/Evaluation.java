package edu.upf.taln.textplanning.common;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.upf.taln.textplanning.common.FileUtils.deserializeMeanings;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Evaluation
{
	public static class AlternativeMeanings
	{
		private final Set<String> alternatives = new HashSet<>();
		private final String text; // covered text or label
		private final double rank;

		private AlternativeMeanings(Collection<String> meanings, String text, double rank)
		{
			alternatives.addAll(meanings);
			this.text = text;
			this.rank = rank;
		}

		private static boolean match(AlternativeMeanings a1, AlternativeMeanings a2)
		{
			return !Collections.disjoint(a1.alternatives, a2.alternatives);
		}
	}

	private final static Logger log = LogManager.getLogger();


	public static void evaluate(List<Path> ranked_candidate_files, List<Path> gold_files)
	{
		final List<List<Candidate>> ranked_candidates = ranked_candidate_files.stream()
				.map(system_file -> deserializeMeanings(system_file).stream()
						.flatMap(l -> l.stream()
								.flatMap(Set::stream))
						.collect(toList()))
				.collect(toList());
		final List<String> gold_contents = gold_files.stream()
				.map(FileUtils::readTextFile)
				.collect(toList());

		// Store evaluation results here
		List<Pair<Double, Double>> prec_recall_list = new ArrayList<>();
		List<Double> average_ranks = new ArrayList<>();

		// Iterate candidate and gold files
		for (int i=0; i < gold_files.size(); ++i)
		{
			// Read list of candidate and gold meanings
			final List<Candidate> candidate_set = ranked_candidates.get(i);
			final String gold_set = gold_contents.get(i);
			final List<AlternativeMeanings> gold_meanings = readGoldMeanings(gold_set).stream().flatMap(List::stream).collect(toList());
			final List<AlternativeMeanings> sorted_candidates = candidate_set.stream()
					.map(Candidate::getMeaning)
					.distinct() // no duplicates!
					.sorted(Comparator.comparingDouble(Meaning::getWeight).reversed()) // decreasing order
					.map(m -> new AlternativeMeanings(Collections.singletonList(m.getReference()), m.toString(), m.getWeight()))
					.collect(toList());

			// Calculate prec and recall using top system meanings
			final List<AlternativeMeanings> system_meanings = sorted_candidates.subList(0, Math.min(sorted_candidates.size(), gold_meanings.size()));
			final Pair<Double, Double> prec_recall = calculatePrecisionAndRecall(gold_meanings, system_meanings);
			final double precision = prec_recall.getLeft();
			final double recall = prec_recall.getRight();
			final double fscore = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
			log.info("Stats for " + gold_files.get(i).getFileName()+ ": p = " + DebugUtils.printDouble(precision) +
					" r = " + DebugUtils.printDouble(recall) + " f = " + DebugUtils.printDouble(fscore));
			prec_recall_list.add(prec_recall);

			// Get list of system meanings sorted descendingly by rank
			final List<String> sorted_system_meanings = sorted_candidates.stream()
					.map(c -> c.alternatives.iterator().next())
					.collect(toList());

			final String ranks_string = gold_meanings.stream()
					.map(gold_meaning -> gold_meaning.alternatives.stream()
							.map(a -> a + " " + sorted_system_meanings.indexOf(a))
							.collect(Collectors.joining("|", "[", "]")))
					.collect(Collectors.joining(", "));

			final List<String> max_gold_meanings = gold_meanings.stream()
					.map(am -> am.alternatives.stream()
							.filter(sorted_system_meanings::contains)
							.min(Comparator.comparingInt(sorted_system_meanings::indexOf))) // choose alternative appearing first in ranked list
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(toList());
			final double avg_rank = max_gold_meanings.stream()
					.filter(sorted_system_meanings::contains)
					.mapToInt(sorted_system_meanings::indexOf)
					.mapToDouble(r -> r / (double)sorted_system_meanings.size())
					.average().orElse(0.0);
			log.info("Average rank = " + DebugUtils.printDouble(avg_rank) + ". Ranks = " + ranks_string + " out of " + sorted_candidates.size());
			average_ranks.add(avg_rank);
		}

		final double precision = prec_recall_list.stream()
				.mapToDouble(Pair::getLeft)
				.average().orElse(0.0);
		final double recall = prec_recall_list.stream()
				.mapToDouble(Pair::getRight)
				.average().orElse(0.0);
		final double fscore = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
		log.info("Average stats: p = " + DebugUtils.printDouble(precision) +
				" r = " + DebugUtils.printDouble(recall) + " f = " + DebugUtils.printDouble(fscore));
		log.info("Total average rank = " + DebugUtils.printDouble(average_ranks.stream().mapToDouble(d -> d).average().orElse(0.0)));
	}

	private static List<List<AlternativeMeanings>> readGoldMeanings(String text)
	{
		final String regex = "(bn:\\d+[r|a|v|n](\\|bn:\\d+[r|a|v|n])*)-\"([^\"]+)\"";
		final Pattern pattern = Pattern.compile(regex);
		final Predicate<String> is_meanings = pattern.asPredicate();

		return Pattern.compile("\n+").splitAsStream(text)
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
						final String covered_text = m.group(2);
						meanings.add(new AlternativeMeanings(alternatives, covered_text, 0.0));
					}

					return meanings;
				})
				.collect(toList());
	}

	private static Pair<Double, Double> calculatePrecisionAndRecall(List<AlternativeMeanings> gold, List<AlternativeMeanings> system)
	{
		final long true_positives = system.stream()
				.filter(s -> gold.stream().anyMatch(g -> AlternativeMeanings.match(s, g)))
				.count();
		final long false_positives = system.stream()
				.filter(s -> gold.stream().noneMatch(g -> AlternativeMeanings.match(s, g)))
				.count();
		final long false_negatives = gold.stream()
				.filter(g -> system.stream().noneMatch(s -> AlternativeMeanings.match(g, s)))
				.count();

		double precision = (double) true_positives / (double)(true_positives + false_positives);
		double recall = (double) true_positives / (double)(true_positives + false_negatives);
		return Pair.of(precision, recall);
	}
}
