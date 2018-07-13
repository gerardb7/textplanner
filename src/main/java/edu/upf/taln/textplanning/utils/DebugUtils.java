package edu.upf.taln.textplanning.utils;

import Jama.Matrix;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.SemanticSubgraph;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

public class DebugUtils
{
	private final static NumberFormat format = NumberFormat.getInstance();
	static {
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(4);
		format.setMinimumFractionDigits(4);
	}
	private final static Logger log = LogManager.getLogger();

	public static String printDisambiguation(Candidate chosen, Collection<Candidate> other)
	{
		return chosen.getMention().getSurface_form() + "\t" + chosen.getMeaning() +
				"\t" + other.stream()
				.filter(c2 -> c2 != chosen)
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.collect(joining(", "));
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
			.map(s -> "Set " + i.getAndIncrement() + s.stream()
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
				.collect(Collectors.joining(","));
		return v + "\t" + meaning + "\t" + surface_forms;
	}

	public static void reportStats(GraphList graphs, Map<String, List<Candidate>> candidates)
	{
		int numVertices = graphs.getGraphs().stream()
				.map(SemanticGraph::vertexSet)
				.mapToInt(Set::size)
				.sum();
		Set<String> alignedVertices = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> g.getAlignments().getAlignment(v).isPresent()))
				.collect(toSet());
		Set<String> nominalVertices = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> g.getAlignments().getAlignment(v).isPresent())
						.filter(v -> g.getAlignments().getPOS(g.getAlignments().getAlignment(v).get()).startsWith("N")))
				.collect(toSet());

		long numForms = alignedVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.map(Mention::getSurface_form)
				.distinct()
				.count();
		long numNominalForms = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.map(Mention::getSurface_form)
				.distinct()
				.count();
		long numMentions = candidates.values().stream()
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.distinct()
				.count();
		long numNominalMentions = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.distinct()
				.count();
		long numCandidates = candidates.values().stream()
				.mapToLong(List::size)
				.sum();
		long numNominalCandidates = nominalVertices.stream()
				.map(candidates::get)
				.mapToLong(List::size)
				.sum();
		long numMeanings = candidates.values().stream()
				.flatMap(List::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.count();
		long numNominalMeanings = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.count();
		double candidatesPerNode = candidates.values().stream()
				.mapToLong(List::size)
				.average()
				.orElse(0.0);
		double candidatesPerNominalNode = nominalVertices.stream()
				.map(candidates::get)
				.mapToLong(List::size)
				.average()
				.orElse(0.0);

//		double avgMentionsPerOtherLabel = avgMentionsPerLabel - avgMentionsPerNominalLabel;

		// Set up formatting
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(2);
		f.setMinimumFractionDigits(2);

		log.info(numVertices + " vertices, " + alignedVertices.size() + " aligned (" + nominalVertices.size() + " nominal)" );
		log.info(numForms + " forms (" + numNominalForms + " nominal) ");
		log.info(numMentions + " mentions (" + numNominalMentions + " nominal) ");
		log.info(numCandidates + " candidates (" + numNominalCandidates + " nominal) ");
		log.info(numMeanings + " meanings (" + numNominalMeanings + " nominal) ");
		log.info(f.format(candidatesPerNode) + " candidates/node (" + f.format(candidatesPerNominalNode) + " nominal) ");
	}
}
