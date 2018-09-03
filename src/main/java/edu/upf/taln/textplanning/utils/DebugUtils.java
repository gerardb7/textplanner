package edu.upf.taln.textplanning.utils;

import Jama.Matrix;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.structures.*;
import org.apache.commons.lang3.tuple.Pair;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

public class DebugUtils
{
	private final static NumberFormat format = NumberFormat.getInstance();
	static {
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(4);
		format.setMinimumFractionDigits(4);
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
		return "Disambiguated \"" + chosen.getMention().getSurface_form() + "\" to " + chosen.getMeaning() +
				"\t\t" + other.stream()
				.filter(c2 -> c2 != chosen)
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.collect(joining(", ")) +
				(accepted_multiword.isEmpty() ? "" : "\n\tAccepted multiword " + accepted_multiword) +
				(rejected_multiwords.isEmpty() ? "" : "\n\tRejected multiwords " + rejected_multiwords);
	}

	public static String printCandidate(Candidate c)
	{
		return "\"" + c.getMention().getSurface_form() + "\"\t\t" + c.getMeaning().toString();
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

	public static String printSets(GlobalSemanticGraph g, List<Set<String>> sets)
	{
		AtomicInteger i = new AtomicInteger(1);
		return sets.stream()
				.map(s -> "Global g connected set " + i.getAndIncrement() + "\n" + s.stream()
						.map(v -> "\t" + DebugUtils.createLabelForVariable(v, g.getMeaning(v), g.getMentions(v)))
						.collect(Collectors.joining("\n")))
				.collect(Collectors.joining("\n"));
	}

	public static String printSubgraphs(GlobalSemanticGraph g, List<SemanticSubgraph> subgraphs)
	{
		AtomicInteger i = new AtomicInteger(1);
		return subgraphs.stream()
				.sorted(Comparator.comparingDouble(SemanticSubgraph::getAverageWeight).reversed())
				.map(s ->
				{
					String stats = s.vertexSet().stream()
							.mapToDouble(g::getWeight)
							.summaryStatistics().toString();

					SemanticTree t = new SemanticTree(s);
					return "Subgraph " + i.getAndIncrement() + " " + stats + "\n\t" +
							"center " + s.getCenter() + "\n\t" + s + "\n" +
							printVertex(t, "root", t.getRoot(), 1);
				})
				.collect(Collectors.joining("\n"));
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
