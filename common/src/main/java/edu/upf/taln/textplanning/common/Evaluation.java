package edu.upf.taln.textplanning.common;

import edu.upf.taln.textplanning.core.TextPlanner;
import edu.upf.taln.textplanning.core.similarity.VectorsCosineSimilarity;
import edu.upf.taln.textplanning.core.similarity.vectors.SIFVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.weighting.Context;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.common.FileUtils.getFilesInFolder;
import static edu.upf.taln.textplanning.common.FileUtils.readTextFile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Evaluation
{
	private static class AlternativeMeanings
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
			return Collections.disjoint(a1.alternatives, a2.alternatives);
		}
	}

	private final static Logger log = LogManager.getLogger();


	private static List<List<List<Map<Candidate, Double>>>> deserializeCandidates(File[] files)
	{
		List<List<List<Map<Candidate, Double>>>> candidates = new ArrayList<>();
		Arrays.stream(files).forEach(f ->
		{
			try
			{
				@SuppressWarnings("unchecked")
				List<List<Map<Candidate, Double>>> c = (List<List<Map<Candidate, Double>>>)Serializer.deserialize(f.toPath());
				candidates.add(c);
			}
			catch (Exception e)
			{
				log.error("Cannot store candidates for file " + f);
			}
		});

		return candidates;
	}

	private static List<List<List<Set<Pair<String, String>>>>> readGoldMeanings(File[] gold_files)
	{
		return Arrays.stream(gold_files)
				.map(File::toPath)
				.map(FileUtils::readTextFile)
				.map(text ->
				{
					final String regex = "(bn:\\d+[r|a|v|n](\\|bn:\\d+[r|a|v|n])*)-\"([^\"]+)\"";
					final Pattern pattern = Pattern.compile(regex);
					final Predicate<String> is_meanings = Pattern.compile("^@bn:.*").asPredicate();

					return Pattern.compile("\n+").splitAsStream(text)
							.filter(is_meanings)
							.map(pattern::matcher)
							.map(m ->
							{
								final List<Set<Pair<String, String>>> meanings = new ArrayList<>();
								while (m.find())
								{
									final String meanings_string = m.group(1);
									final String[] meanings_parts = meanings_string.split("\\|");
									final Set<String> alternatives = Arrays.stream(meanings_parts)
											.map(p -> p.startsWith("\"") && p.endsWith("\"") ? p.substring(1, p.length() - 1) : p)
											.collect(toSet());
									final String covered_text = m.group(3);
									meanings.add(alternatives.stream()
											.map(a -> Pair.of(a, covered_text))
											.collect(toSet()));
								}

								return meanings;
							})
							.collect(toList());
				})
				.collect(toList());
	}

	public static void evaluate(List<List<Pair<String, String>>> system, List<String> gold)
	{
		final List<List<AlternativeMeanings>> gold_meanings = gold.stream()
				.map(Evaluation::readGoldMeanings)
				.flatMap(List::stream)
				.collect(toList());
		final List<List<AlternativeMeanings>> system_meanings = system.stream()
				.map(l -> l.stream()
						.map(p -> new AlternativeMeanings(Collections.singletonList(p.getKey()), p.getValue()))
						.collect(toList()))
				.collect(toList());
		{
			final List<AlternativeMeanings> one_set_gold = gold_meanings.stream()
					.flatMap(Collection::stream)
					.collect(toList());
			final List<AlternativeMeanings> one_set_system = system_meanings.stream()
					.flatMap(Collection::stream)
					.collect(toList());

			final Pair<Double, Double> prec_recall = calculatePrecisionAndRecall(one_set_gold, one_set_system);
			final double precision = prec_recall.getLeft();
			final double recall = prec_recall.getRight();
			final double fscore = 2 * precision * recall / (precision + recall);
			log.info("One set per summary evaluation: p = " + DebugUtils.printDouble(precision) +
					" r = " + DebugUtils.printDouble(recall) + " f = " + DebugUtils.printDouble(fscore));
		}

		{
			final List<Pair<Double, Double>> prec_recall_list = IntStream.range(0, gold_meanings.size())
					.mapToObj(i -> Pair.of(gold_meanings.get(i), system_meanings.get(i)))
					.map(p -> calculatePrecisionAndRecall(p.getLeft(), p.getRight()))
					.collect(toList());
			final double precision = prec_recall_list.stream()
					.mapToDouble(Pair::getLeft)
					.average().orElse(0.0);
			final double recall = prec_recall_list.stream()
					.mapToDouble(Pair::getRight)
					.average().orElse(0.0);
			final double fscore = 2 * precision * recall / (precision + recall);
			log.info("One set per sentence evaluation: p = " + DebugUtils.printDouble(precision) +
					" r = " + DebugUtils.printDouble(recall) + " f = " + DebugUtils.printDouble(fscore));
		}
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
						meanings.add(new AlternativeMeanings(alternatives, covered_text));
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
