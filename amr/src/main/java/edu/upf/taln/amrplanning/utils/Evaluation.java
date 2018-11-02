package edu.upf.taln.amrplanning.utils;

import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Evaluation
{
	private static class AlternativeMeanings
	{
		final Set<String> alternatives = new HashSet<>();
		AlternativeMeanings(Collection<String> meanings) { alternatives.addAll(meanings); }
		AlternativeMeanings(String meaning) { alternatives.add(meaning); }
		static boolean match(AlternativeMeanings a1, AlternativeMeanings a2)
		{
			return Collections.disjoint(a1.alternatives, a2.alternatives);
		}
	}

	private static class GraphMeanings
	{
		final Set<AlternativeMeanings> meanings = new HashSet<>();
		GraphMeanings(Collection<AlternativeMeanings> meanings) { this.meanings.addAll(meanings); }
	}

	private final static Logger log = LogManager.getLogger();

	private static List<GraphMeanings> readGoldMeanings(String text)
	{
		final String regex = "(\"[^\"]+\"|bn:\\d+[r|a|v|n](\\|bn:\\d+[r|a|v|n])*)-\"([^\"]+)\"";
		final Pattern pattern = Pattern.compile(regex);
		final Predicate<String> is_meanings = pattern.asPredicate();

		return Pattern.compile("\n+").splitAsStream(text)
				.filter(is_meanings::test)
				.map(pattern::matcher)
				.map(m ->
				{
					final List<AlternativeMeanings> meanings = new ArrayList<>();
					while (m.find())
					{
						final String meanings_string = m.group(1);
						final String[] meanings_parts = meanings_string.split("|");
						final Set<String> alternatives = Arrays.stream(meanings_parts)
								.map(p -> p.startsWith("\"") && p.endsWith("\"") ? p.substring(1, p.length() - 1) : p)
								.collect(toSet());
						meanings.add(new AlternativeMeanings(alternatives));
					}

					return meanings;
				})
				.map(GraphMeanings::new)
				.collect(toList());
	}

	private static Pair<Double, Double> calculatePrecisionAndRecall(Set<AlternativeMeanings> gold, Set<AlternativeMeanings> system)
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

	public static void evaluate(List<SemanticSubgraph> plan, String gold)
	{
		final List<GraphMeanings> gold_meanings = readGoldMeanings(gold);
		final List<GraphMeanings> system_meanings = plan.stream()
				.map(s -> s.vertexSet().stream()
						.map(v -> s.getBase().getMeaning(v))
						.filter(Optional::isPresent)
						.map(Optional::get)
						.map(Meaning::getReference)
						.map(AlternativeMeanings::new)
						.collect(toSet()))
				.map(GraphMeanings::new)
				.collect(toList());
		{
			final Set<AlternativeMeanings> one_set_gold = gold_meanings.stream()
					.map(g -> g.meanings)
					.flatMap(Set::stream)
					.collect(toSet());
			final Set<AlternativeMeanings> one_set_system = system_meanings.stream()
					.map(g -> g.meanings)
					.flatMap(Set::stream)
					.collect(toSet());

			final Pair<Double, Double> prec_recall = calculatePrecisionAndRecall(one_set_gold, one_set_system);
			final double precision = prec_recall.getLeft();
			final double recall = prec_recall.getRight();
			final double fscore = 2 * precision * recall / (precision + recall);
			log.info("One set per summary evaluation: p = " + DebugUtils.printDouble(precision) +
					" r = " + DebugUtils.printDouble(recall) + " f = " + DebugUtils.printDouble(fscore));
		}

		{
			final List<Pair<Double, Double>> prec_recall_list = IntStream.range(0, gold_meanings.size())
					.mapToObj(i -> Pair.of(gold_meanings.get(i).meanings, system_meanings.get(i).meanings))
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
}
