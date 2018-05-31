package edu.upf.taln.textplanning.input;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.AbstractGraph;

import java.util.*;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

// Creates global semantic graphs from a lists of AMR-like semantic graphs
public class GlobalGraphFactory
{
	public static GlobalSemanticGraph create(GraphList graphs)
	{
		remove_concepts(graphs);
		remove_names(graphs);
		disambiguate_candidates(graphs);
		collapse_multiwords(graphs);
		GlobalSemanticGraph merge = merge(graphs);
		resolve_arguments();

		return merge;
	}

	private static void remove_concepts(GraphList graphs)
	{
		final Map<SemanticGraph, Set<String>> concepts = graphs.getGraphs().stream()
				.map(g -> Pair.of(g, g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRConstants.instance))
						.map(g::getEdgeTarget)
						.collect(toSet())))
				.collect(toMap(Pair::getLeft, Pair::getRight));

		concepts.forEach(AbstractGraph::removeAllVertices);
	}

	/**
	 * Given:
	 *      x -name-> n -op*-> strings
	 * Removes all but x.
 	 */
	private static void remove_names(GraphList graphs)
	{
		final Map<SemanticGraph, Set<String>> names = graphs.getGraphs().stream()
				.map(g -> Pair.of(g, g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRConstants.name))
						.map(g::getEdgeTarget)
						.map(name -> {
							final Set<String> descendants = g.getDescendants(name); // this adds names
							descendants.add(name);
							return descendants;
						})
						.flatMap(Set::stream)
						.collect(toSet())))
				.collect(toMap(Pair::getLeft, Pair::getRight));

		names.forEach(AbstractGraph::removeAllVertices);
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

	/**
	 * Subsumers: vertices with a multiword meaning and at least one descendant
	 * Remember:    - vertices have 1 or 2 spans, one for the aligned token and one covering all descendants
	 * 	              - candidates are assigned to vertices matching the span of their mentions
	 * 	              - after disambiguation each vertex has a single candidate
	 * 	              - multiword candidates subsume all descendants of the vertex they're associated with
	 */
	private static void collapse_multiwords(GraphList graphs)
	{
		LinkedList<Pair<SemanticGraph, String>> subsumers = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> !graphs.getCandidates(v).isEmpty())
						.filter(v -> graphs.getCandidates(v).iterator().next().getMention().isMultiWord())
						.filter( v -> !g.getDescendants(v).isEmpty())
						.map(v -> Pair.of(g, v)))
				.collect(toCollection(LinkedList::new));

		while(!subsumers.isEmpty())
		{
			// Pop a subsumer from the queue
			final Pair<SemanticGraph, String> subsumer = subsumers.pop();
			final SemanticGraph g = subsumer.getLeft();
			final String v = subsumer.getRight();

			Candidate c = graphs.getCandidates(v).iterator().next();
			// v_value = value of highest scored meaning of v
			double v_value = c.getMeaning().getWeight();

			// s_max -> value of highest scored subsumed meaning
			final Set<String> descendants = g.getDescendants(v);
			double s_max = descendants.stream()
					.map(graphs::getCandidates)
					.flatMap(Collection::stream)
					.map(Candidate::getMeaning)
					.mapToDouble(Meaning::getWeight)
					.max().orElse(0.0);

			// Is there a descendant with a highest scored meaning?
			if (v_value >= s_max)
			{
				// this also updates meanings and coreference
				graphs.vertexContraction(g, v, descendants);

				// Update remaining subsumers affected by this contraction
				final List<Pair<SemanticGraph, String>> renamed_multiwords = subsumers.stream()
						.filter(p -> descendants.contains(p.getRight()))
						.collect(toList());
				renamed_multiwords.forEach(p ->
				{
					subsumers.remove(p);
					subsumers.add(Pair.of(p.getLeft(), v)); // subsumer node has been replaced by v
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
			merged.addNewEdge(v1, v2, e.getLabel());
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
