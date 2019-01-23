package edu.upf.taln.textplanning.core.utils;

import Jama.Matrix;
import edu.upf.taln.textplanning.core.structures.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public class DebugUtils
{
	public static class ThreadReporter
	{
		private final AtomicBoolean reported = new AtomicBoolean(false);
		private final Logger log;

		public ThreadReporter(Logger log)
		{
			this.log = log;
		}

		public void report()
		{
			if (!reported.getAndSet(true))
				log.info("Number of threads: " + Thread.activeCount());
		}
	}

	private final static NumberFormat format = NumberFormat.getInstance();
	static {
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(4);
		format.setMinimumFractionDigits(4);
	}

	public static String printDouble(double w) { return format.format(w); }
	public static String printDouble(double w, int fraction_digits)
	{
		final int old = format.getMaximumFractionDigits();
		format.setMaximumFractionDigits(fraction_digits);
		final String s = DebugUtils.format.format(w);
		format.setMaximumFractionDigits(old);
		return s;
	}

	public static String printDisambiguation(Candidate chosen, Collection<Candidate> other)
	{
		String accepted_multiword = "";
		if (chosen.getMention().isMultiWord())
			accepted_multiword = printCandidate(chosen);
		final String rejected_multiwords = other.stream()
				.filter(c -> c.getMention().isMultiWord())
				.filter(c -> !c.getMention().equals(chosen.getMention()))
				.map(DebugUtils::printCandidate)
				.collect(joining(", "));
		return "Disambiguated \"" + chosen.getMention().getSurface_form() +
				"\" to " + chosen.getMeaning() + " " + printDouble(chosen.getMeaning().getWeight()) +
				"\t\t" + other.stream()
				.filter(c2 -> c2 != chosen)
				.map(Candidate::getMeaning)
				.map(m -> m.toString()  + " " + printDouble(chosen.getMeaning().getWeight()))
				.collect(joining(", ")) +
				(accepted_multiword.isEmpty() ? "" : "\n\tDetected multiword " + accepted_multiword) +
				(rejected_multiwords.isEmpty() ? "" : "\n\tDiscarded multiwords " + rejected_multiwords);
	}

	public static String printCandidate(Candidate c)
	{
		return "\"" + c.getMention().getSurface_form() + "\"\t\t" + c.getMeaning().toString();
	}

	public static String printCorefMerge(String v, Collection<String> C, SemanticGraph g)
	{
		return "Coreferent vertices in chain " + C + " merged to " +
				DebugUtils.createLabelForVariable(v, g.getMeaning(v), g.getMentions(v));
	}

	public static String printRank(Matrix v, int n, List<String> labels)
	{
		final List<Pair<String, Double>> sorted_items = new ArrayList<>();
		for (int i =0; i < n; ++i)
		{
			final String l = labels.get(i);
			final double v_i = v.getColumnPackedCopy()[i];
			sorted_items.add(Pair.of(l, v_i));
		}
		sorted_items.sort(Comparator.comparingDouble(Pair<String, Double>::getRight).reversed());
		return 	sorted_items.subList(0, Math.min(n, 100)).stream()
				.map(p -> format.format(p.getRight()) + "\t" + p.getLeft())
				.collect(Collectors.joining("\n"));
	}

	public static String printSets(SemanticGraph g, List<Set<String>> sets)
	{
		AtomicInteger i = new AtomicInteger(1);
		return sets.stream()
				.map(s -> "Connected set " + i.getAndIncrement() + " of semantic graph\n" + s.stream()
						.map(v -> "\t" + DebugUtils.createLabelForVariable(v, g.getMeaning(v), g.getMentions(v)))
						.collect(Collectors.joining("\n")))
				.collect(Collectors.joining("\n"));
	}

	public static String printSubgraphs(List<SemanticSubgraph> subgraphs)
	{
		AtomicInteger i = new AtomicInteger(1);
		return subgraphs.stream()
				.sorted(Comparator.comparingDouble(SemanticSubgraph::getAverageWeight).reversed())
				.map(SemanticTree::new)
				.map(t -> printTree(i.getAndIncrement(), t))
				.collect(Collectors.joining("\n"));
	}

	public static String printTree(int i, SemanticTree t)
	{
		final SemanticSubgraph s = t.asGraph();
		final SemanticGraph g = s.getBase();
		String stats = s.vertexSet().stream()
				.mapToDouble(g::getWeight)
				.summaryStatistics().toString();

		return "Subgraph " + i + " value=" + format.format(s.getValue()) + " " + stats + "\n\t" +
				"root node " + s.getRoot() + "\n\t" + s + "\n" +	printVertex(t, "root", t.getRoot(), 1);
	}

	private static String printVertex(SemanticTree t, String role, String v, int depth)
	{
		final String tabs = IntStream.range(0, depth)
				.mapToObj(i -> "\t")
				.collect(Collectors.joining());
		String s = tabs + role + " -> " + DebugUtils.createLabelForVariable(v, t.getMeaning(v), t.getMentions(v)) +
				" " + format.format(t.getWeight(v)) + "\n";

		return s + t.outgoingEdgesOf(v).stream()
				.map(e -> printVertex(t, e.getLabel(), t.getEdgeTarget(e), depth + 1))
				.collect(Collectors.joining());
	}

	public static String createLabelForVariable(String v, Optional<Meaning> m, Collection<Mention> mentions)
	{
		final String meaning = m.map(Meaning::toString).orElse("");
		final String surface_forms = mentions.stream()
				.map(Mention::getSurface_form)
				.distinct()
				.map(f -> "\"" + f + "\"")
				.collect(Collectors.joining(", "));
		return v + " " + meaning + " " + surface_forms;
	}
}
