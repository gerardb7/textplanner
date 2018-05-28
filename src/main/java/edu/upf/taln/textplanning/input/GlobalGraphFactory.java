package edu.upf.taln.textplanning.input;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

// Creates global semantic graphs from a lists of AMR-like semantic graphs
public class GlobalGraphFactory
{
	public static GlobalSemanticGraph create(GraphList graphs)
	{
		disambiguate_candidates(graphs);
		collapse_multiwords(graphs);
		GlobalSemanticGraph merge = merge(graphs);
		resolve_arguments();

		return merge;
	}

	private static void disambiguate_candidates(GraphList graphs)
	{
		// Use vertex weights to choose best candidate for each vertex
		graphs.getGraphs().forEach(g ->
				g.vertexSet().forEach(v ->
						graphs.getCandidates(v).stream()
								.max(comparingDouble(c -> c.getMeaning().getWeight()))
								.ifPresent(graphs::chooseCandidate)));
	}

	private static void collapse_multiwords(GraphList graphs)
	{
		LinkedList<Pair<SemanticGraph, String>> multiwords = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> !graphs.getCandidates(v).isEmpty())
						.filter(v ->
						{
							Candidate c = graphs.getCandidates(v).iterator().next(); // there should be just one...
							return c.getMention().isMultiWord();
						})
						.map(v -> Pair.of(g, v)))
				.collect(toCollection(LinkedList::new));

		while(!multiwords.isEmpty())
		{
			// Pop a multiword from the queue
			final Pair<SemanticGraph, String> multiword = multiwords.pop();
			final SemanticGraph g = multiword.getLeft();
			final String v = multiword.getRight();

			Candidate c = graphs.getCandidates().iterator().next();
			// v_value = value of highest scored meaning of v
			double v_value = c.getMeaning().getWeight();

			// d_max -> value of highest scored descendant meaning
			Set<String> descendants = g.getDescendants(v);
			double d_max = descendants.stream()
					.map(graphs::getCandidates)
					.flatMap(Collection::stream)
					.map(Candidate::getMeaning)
					.mapToDouble(Meaning::getWeight)
					.max().orElse(0.0);

			if (v_value >= d_max)
			{
				// this also updates meanings and coreference
				graphs.vertexContraction(g, v, descendants);

				// Update remaining multiwords affected by this contraction
				final List<Pair<SemanticGraph, String>> renamed_multiwords = multiwords.stream()
						.filter(p -> descendants.contains(p.getRight()))
						.collect(toList());
				renamed_multiwords.forEach(p ->
				{
					multiwords.remove(p);
					multiwords.add(Pair.of(p.getLeft(), v)); // multiword node has been replaced by v
				});
			}
		}
	}

	private static GlobalSemanticGraph merge(GraphList graphs)
	{
		// Create merged (disconnected) graph
		GlobalSemanticGraph merged = new GlobalSemanticGraph();
		graphs.getGraphs().forEach(g -> g.edgeSet().forEach(e ->
		{
			String v1 = g.getEdgeSource(e);
			merged.addVertex(v1);
			String v2 = g.getEdgeTarget(e);
			merged.addVertex(v2);
			merged.addEdge(v1, v2, e);
		}));

		graphs.getCandidates().forEach(c ->
		{
			merged.setMeaning(c.getVertex(), c.getMeaning());
			merged.addMention(c.getVertex(), c.getMention());
		});

		// Merge all coreferent vertices
		graphs.getChains().stream()
				.filter(c -> c.getSize() > 1)
				.forEach(c ->
				{
					Collection<String> C = c.getVertices();
					// v node, whose meaning is kept, corresponds to that with the highest scored meaning
					String v = C.stream()
							.max(comparingDouble(n -> merged.getMeaning(n).map(Meaning::getWeight).orElse(0.0))).orElse(null);
					C.remove(v);
					merged.vertexContraction(v, C);
				});

		// Merge all vertices referring to the same NE
		Multimap<String, String> vertices_to_NEs = HashMultimap.create();
		merged.vertexSet().forEach(v -> merged.getMeaning(v)
				.filter(Meaning::isNE)
				.map(Meaning::getReference)
				.ifPresent(r -> vertices_to_NEs.put(r, v)));

		vertices_to_NEs.keySet().stream()
				.map(vertices_to_NEs::get)
				.filter(c -> c.size() > 1)
				.forEach(c ->
				{
					Iterator<String> it = c.iterator();
					String v = it.next();
					List<String> C = new ArrayList<>();
					it.forEachRemaining(C::add);
					merged.vertexContraction(v, C); // keeps v's meaning, which is ok as all in C share same meaning
				});

		return merged;
	}

	private static void resolve_arguments() {}
}
