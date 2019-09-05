package edu.upf.taln.textplanning.core.utils;

import edu.upf.taln.textplanning.core.structures.*;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public class DebugUtils
{
	public static final int LOGGING_STEP_SIZE = 100000;

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

		public void reset()
		{
			reported.set(false);
		}
	}

	private final static NumberFormat double_format = NumberFormat.getInstance();
	static {
		double_format.setRoundingMode(RoundingMode.UP);
		double_format.setMaximumFractionDigits(4);
		double_format.setMinimumFractionDigits(4);
	}
	private final static NumberFormat int_format = new DecimalFormat("#,###");
	public static String printInteger(int i)
	{
		return int_format.format(i);
	}


	public static String printDouble(double w) { return double_format.format(w); }
	public static String printDouble(double w, int fraction_digits)
	{
		double_format.setRoundingMode(RoundingMode.CEILING);
		final int old = double_format.getMaximumFractionDigits();
		double_format.setMaximumFractionDigits(fraction_digits);
		final String s = DebugUtils.double_format.format(w);
		double_format.setMaximumFractionDigits(old);
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
		final String weight_str = chosen.getWeight().map(DebugUtils::printDouble).orElse("no weight");
		return "Disambiguated \"" + chosen.getMention().getSurfaceForm() +
				"\" to " + chosen.getMeaning() + " " + weight_str +
				"\t\t" + other.stream()
				.filter(c2 -> c2 != chosen)
				.map(Candidate::getMeaning)
				.map(m -> m.toString()  + " " + weight_str)
				.collect(joining(", ")) +
				(accepted_multiword.isEmpty() ? "" : "\n\tDetected multiword " + accepted_multiword) +
				(rejected_multiwords.isEmpty() ? "" : "\n\tDiscarded multiwords " + rejected_multiwords);
	}

	public static String printCandidate(Candidate c)
	{
		return "\"" + c.getMention().getSurfaceForm() + "\"\t\t" + c.getMeaning().toString();
	}

	public static String printCorefMerge(String v, Collection<String> C, SemanticGraph g)
	{
		return "Coreferent vertices in chain " + C + " merged to " +
				DebugUtils.createLabelForVariable(v, g.getMeaning(v).orElse(null), g.getMentions(v));
	}

	public static String printRank(double[] v, int n, double[] bias, List<String> labels)
	{
		assert v.length == labels.size();

		final List<Triple<String, Double, Double>> sorted_items = new ArrayList<>();
		for (int i =0; i < v.length; ++i)
		{
			final String l = labels.get(i);
			final double v_i = v[i];
			final double b_i = bias[i];
			sorted_items.add(Triple.of(l, v_i, b_i));
		}
		sorted_items.sort(Comparator.comparingDouble(Triple<String, Double, Double>::getMiddle).reversed());
		return 	sorted_items.subList(0, n).stream()
				.map(p -> double_format.format(p.getMiddle()) + "\t(" + double_format.format(p.getRight()) + ")\t" + p.getLeft())
				.collect(Collectors.joining("\n"));
	}

	public static String printSets(SemanticGraph g, List<Set<String>> sets)
	{
		AtomicInteger i = new AtomicInteger(1);
		return sets.stream()
				.map(s -> "Connected set " + i.getAndIncrement() + " of semantic graph\n" + s.stream()
						.map(v -> "\t" + DebugUtils.createLabelForVariable(v, g.getMeaning(v).orElse(null), g.getMentions(v)))
						.collect(Collectors.joining("\n")))
				.collect(Collectors.joining("\n"));
	}

	public static String printSubgraphs(List<SemanticSubgraph> subgraphs)
	{
		AtomicInteger i = new AtomicInteger(0);
		return subgraphs.stream()
				.map(SemanticTree::new)
				.map(t -> printTree(i.getAndIncrement(), t))
				.collect(Collectors.joining("\n"));
	}

	public static String printTree(int i, SemanticTree t)
	{
		final SemanticSubgraph s = t.asGraph();
		final SemanticGraph g = s.getBase();
		String stats = s.vertexSet().stream()
				.map(g::getWeight)
				.flatMap(Optional::stream)
				.mapToDouble(d -> d)
				.summaryStatistics().toString();

		return "Subgraph " + i + " value=" + double_format.format(s.getValue()) + " " + stats + "\n\t" +
				"root node " + s.getRoot() + "\n" +	printVertex(t, "root", t.getRoot(), 1);
	}

	private static String printVertex(SemanticTree t, String role, String v, int depth)
	{
		final String tabs = IntStream.range(0, depth)
				.mapToObj(i -> "\t")
				.collect(Collectors.joining());
		String s = tabs + role + " -> " + DebugUtils.createLabelForVariable(v, t.getMeaning(v).orElse(null), t.getMentions(v)) +
				" " + t.getWeight(v).map(double_format::format).orElse("") + "\n";

		return s + t.outgoingEdgesOf(v).stream()
				.map(e -> printVertex(t, e.getLabel(), t.getEdgeTarget(e), depth + 1))
				.collect(Collectors.joining());
	}

	public static String createLabelForVariable(String v, Meaning m, Collection<Mention> mentions)
	{
		final String meaning = m != null ? m.getReference() : "";
		final String surface_forms = mentions.stream()
				.map(Mention::getSurfaceForm)
				.distinct()
				.map(f -> "\"" + f + "\"")
				.collect(Collectors.joining(", "));
		return v + " " + meaning + " " + surface_forms;
	}
}
