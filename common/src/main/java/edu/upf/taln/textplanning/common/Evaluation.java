package edu.upf.taln.textplanning.common;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.upf.taln.textplanning.common.FileUtils.deserializeMeanings;
import static java.util.stream.Collectors.*;

public class Evaluation
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
		List<Triple<Double, Double, Double>> stats_list = new ArrayList<>();
		List<Double> average_ranks = new ArrayList<>();

		// Iterate candidate and gold files
		for (int i=0; i < gold_files.size(); ++i)
		{
			log.info("Stats for " + gold_files.get(i).getFileName());

			// Read list of candidate and gold meanings
			final List<Candidate> candidate_set = ranked_candidates.get(i);
			final String gold_set = gold_contents.get(i);
			final List<AlternativeMeanings> gold_meanings = readGoldMeanings(gold_set).stream().flatMap(List::stream).collect(toList());
			final List<Meaning> sorted_candidates = candidate_set.stream()
					.map(Candidate::getMeaning)
					.distinct() // no duplicates!
					.sorted(Comparator.comparingDouble(Meaning::getWeight).reversed()) // decreasing order
					.collect(toList());
			final List<AlternativeMeanings> sorted_meanings = sorted_candidates.stream()
					.map(m -> new AlternativeMeanings(Collections.singletonList(m.getReference()), m.toString()))
					.collect(toList());

			stats_list.add(calculatePrecisionAndRecall(sorted_meanings, gold_meanings));
			average_ranks.add(calculateRanks(sorted_meanings, gold_meanings));
			showRankedCandidatesPerMention(candidate_set, sorted_candidates);
		}

		final double precision = stats_list.stream()
				.mapToDouble(Triple::getLeft)
				.average().orElse(0.0);
		final double recall = stats_list.stream()
				.mapToDouble(Triple::getRight)
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
						final String covered_text = m.group(3);
						meanings.add(new AlternativeMeanings(alternatives, covered_text));
					}

					return meanings;
				})
				.collect(toList());
	}

	private static Triple<Double, Double, Double> calculatePrecisionAndRecall(List<AlternativeMeanings> sorted_system_meanings, List<AlternativeMeanings> gold_meanings)
	{
		// Calculate prec and recall using top system meanings
		final List<AlternativeMeanings> system = sorted_system_meanings.subList(0, Math.min(sorted_system_meanings.size(), gold_meanings.size()));

		final long true_positives = system.stream()
				.filter(s -> gold_meanings.stream().anyMatch(g -> AlternativeMeanings.match(s, g)))
				.count();
		final long false_positives = system.stream()
				.filter(s -> gold_meanings.stream().noneMatch(g -> AlternativeMeanings.match(s, g)))
				.count();
		final long false_negatives = gold_meanings.stream()
				.filter(g -> system.stream().noneMatch(s -> AlternativeMeanings.match(g, s)))
				.count();

		double precision = (double) true_positives / (double)(true_positives + false_positives);
		double recall = (double) true_positives / (double)(true_positives + false_negatives);
		final double fscore = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
		log.info("p = " + DebugUtils.printDouble(precision) + " r = " + DebugUtils.printDouble(recall) +
				" f = " + DebugUtils.printDouble(fscore));

		return Triple.of(precision, recall, fscore);
	}

	private static double calculateRanks(List<AlternativeMeanings> sorted_system_meanings, List<AlternativeMeanings> gold_meanings)
	{
		// Get list of system meanings sorted descendingly by rank
		final List<String> system = sorted_system_meanings.stream()
				.map(c -> c.alternatives.iterator().next())
				.collect(toList());

		final String ranks_string = gold_meanings.stream()
				.map(gold_meaning -> gold_meaning.alternatives.stream()
						.map(a -> {
							final int rank = system.indexOf(a);
							if (rank != -1)
								return sorted_system_meanings.get(rank).text.replaceAll("-[^:]+:EN:", "-") + " " + rank;
							else
								return a + "-" + gold_meaning.text + " " + rank;
						})
						.collect(Collectors.joining(", ")))
				.collect(Collectors.joining("\n\t", "\n\t", "\n"));

		final List<String> max_gold_meanings = gold_meanings.stream()
				.map(am -> am.alternatives.stream()
						.filter(system::contains)
						.min(Comparator.comparingInt(system::indexOf))) // choose alternative appearing first in ranked list
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(toList());
		final double avg_rank = max_gold_meanings.stream()
				.filter(system::contains)
				.mapToInt(system::indexOf)
				.mapToDouble(r -> r / (double)system.size())
				.average().orElse(0.0);
		log.info("Average rank = " + DebugUtils.printDouble(avg_rank) + " (" + + system.size() + " candidates)");
		log.info("Ranks: " + ranks_string);

		return avg_rank;
	}

	private static void showRankedCandidatesPerMention(List<Candidate> candidates, List<Meaning> sorted_meanings)
	{
		final Map<Mention, List<Candidate>> mentions2candidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention));

		log.info(mentions2candidates.keySet().stream()
				.sorted(Comparator.comparing(Mention::getSentenceId).thenComparing(Mention::getSpan))
				.map(mention -> mention.getSurface_form() + ": " + mentions2candidates.get(mention).stream()
							.map(Candidate::getMeaning)
							.sorted(Comparator.comparingDouble(Meaning::getWeight).reversed())
							.map(meaning -> sorted_meanings.indexOf(meaning) + "\t" +
									DebugUtils.printDouble(meaning.getWeight()) + "\t" +
									meaning.toString().replaceAll("-[^:]+:EN:", "-"))
							.collect(Collectors.joining("\n\t", "\n\t", "\n")))
				.collect(Collectors.joining("\n")));
	}
}
