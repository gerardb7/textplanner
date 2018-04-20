package edu.upf.taln.textplanning.input;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.ranking.GraphRanking;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

import static java.util.Comparator.comparingDouble;

public class GlobalGraphFactory
{
	public static GlobalSemanticGraph create(GraphList graphs, GraphRanking ranker)
	{
		disambiguate_candidates(graphs, ranker);
		collapse_multiwords(graphs);
		GlobalSemanticGraph merge = merge(graphs);
		resolve_arguments();

		return merge;
	}

	private static void disambiguate_candidates(GraphList graphs, GraphRanking ranker)
	{
		// Rank meanings
		ranker.rankMeanings(graphs);

		// Use rankings to choose best candidate for each vertex
		graphs.forEach(g ->
				g.vertexSet().forEach(v ->
						graphs.getCandidates(v).stream()
								.max(comparingDouble(c -> c.getMeaning().getWeight()))
								.ifPresent(c -> graphs.chooseCandidate(v, c))));
	}

	private static void collapse_multiwords(GraphList graphs)
	{
		graphs.forEach(g ->
				g.vertexSet().stream()
						.filter(v -> !graphs.getCandidates(v).isEmpty())
						.forEach(v ->
						{
							Candidate c = graphs.getCandidates().iterator().next(); // there should be just one...
							if (c.getMention().isMultiWord())
							{
								// v_value = value of highest scored meaning of v
								double v_value = c.getMeaning().getWeight();

								// d_max = value of highest scored meaning of any descendant of v
								Set<String> descendants = g.getDescendants(v);
								double d_max = descendants.stream()
										.map(graphs::getCandidates)
										.flatMap(Collection::stream)
										.map(Candidate::getMeaning)
										.mapToDouble(Meaning::getWeight)
										.max().orElse(0.0);

								if (v_value >= d_max)
									graphs.vertexContraction(g, v, descendants); // this also updates meanings and coreference
							}
						}));
	}

	private static GlobalSemanticGraph merge(GraphList graphs)
	{
		// Create merged (disconnected) graph
		GlobalSemanticGraph merged = new GlobalSemanticGraph(graphs);

		// Merge all coreferent vertices
		graphs.getChains().stream()
				.filter(c -> c.getSize() > 1)
				.forEach(c ->
				{
					Collection<String> C = c.getVertices();
					// v node, whose meaning is kept, corresponds to that with the highest scored meaning
					String v = C.stream()
							.max(comparingDouble(n -> merged.getMeaning(n).getWeight())).orElse(null);
					C.remove(v);
					merged.vertexContraction(v, C);
				});

		// Merge all vertices referring to the same NE
		Multimap<String, String> vertices_to_NEs = HashMultimap.create();
		merged.vertexSet().stream()
				.flatMap(v -> merged.getGraphs().getCandidates(v).stream()
						.map(Candidate::getMeaning)
						.filter(Meaning::isNE)
						.map(Meaning::getReference)
						.map(r -> Pair.of(r, v)))
				.forEach(p -> vertices_to_NEs.put(p.getKey(), p.getValue()));

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
