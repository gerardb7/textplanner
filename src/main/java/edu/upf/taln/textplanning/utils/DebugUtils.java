package edu.upf.taln.textplanning.utils;

import Jama.Matrix;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import org.apache.commons.lang3.tuple.Pair;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

	public static String printSets(GlobalSemanticGraph graph, List<Set<String>> sets)
	{
		AtomicInteger i = new AtomicInteger(1);
		return sets.stream()
			.map(s -> "Global graph connected set " + i.getAndIncrement() + "\n" + s.stream()
					.map(v -> "\t" + DebugUtils.createLabelForVariable(graph, v))
					.collect(Collectors.joining("\n")))
			.collect(Collectors.joining("\n"));
	}

	public static String printSubgraphs(GlobalSemanticGraph graph, List<SemanticSubgraph> subgraphs)
	{
		AtomicInteger i = new AtomicInteger(1);
		return subgraphs.stream()
			.map(subgraph ->
			{
				String stats = subgraph.vertexSet().stream()
						.mapToDouble(graph::getWeight)
						.summaryStatistics().toString();

				return "Subgraph " + i.getAndIncrement() + " " + stats + "\n\t" + subgraph + "\n" +
						subgraph.vertexSet().stream()
								.filter(v -> graph.getMeaning(v).isPresent())
								.map(v -> "\t" + DebugUtils.createLabelForVariable(graph, v) + "\t" + format.format(graph.getWeight(v)))
								.collect(Collectors.joining("\n"));
			})
			.collect(Collectors.joining("\n"));
	}

	public static String createLabelForVariable(GlobalSemanticGraph graph, String v)
	{
		final String meaning = graph.getMeaning(v).map(Meaning::toString).orElse("");
		final String surface_forms = graph.getMentions(v).stream()
				.map(Mention::getSurface_form)
				.distinct()
				.map(f -> "\"" + f + "\"")
				.collect(Collectors.joining(", "));
		return v + "\t" + meaning + "\t" + surface_forms;
	}
}
