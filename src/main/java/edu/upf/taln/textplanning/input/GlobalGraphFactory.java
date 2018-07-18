package edu.upf.taln.textplanning.input;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Role;
import edu.upf.taln.textplanning.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.ConnectivityInspector;

import java.util.*;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

// Creates global semantic graphs from a lists of AMR-like semantic graphs
public class GlobalGraphFactory
{
	private final static Logger log = LogManager.getLogger();


	public static GlobalSemanticGraph create(GraphList graphs)
	{
		remove_names(graphs); // <- won't work unless executed before remove_concepts
		remove_concepts(graphs);
		disambiguate_candidates(graphs);
		collapse_multiwords(graphs);
		GlobalSemanticGraph merge = merge(graphs);
		resolve_arguments();

		// log some info
		ConnectivityInspector<String, Role> conn = new ConnectivityInspector<>(merge);
		final List<Set<String>> sets = conn.connectedSets();
		log.info("Merged graph has " + sets.size() + " components");
		log.debug(DebugUtils.printSets(merge, sets));

		return merge;
	}

	private static void remove_concepts(GraphList graphs)
	{
		log.info("Removing concepts");
		final Set<String> concepts = graphs.getGraphs().stream()
				.flatMap(g -> g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRConstants.instance))
						.map(g::getEdgeTarget))
				.collect(toSet());

		graphs.removeVertices(concepts);
	}

	/**
	 * Given:
	 *      (x :name (n /name (:op1 n1 .. :opN nN))) or (x /name (:op1 n1 .. :opN nN))
	 * Removes all but x.
 	 */
	private static void remove_names(GraphList graphs)
	{
		log.info("Removing names");

		// (x :name (n /name (:op1 n1 .. :opN nN)))
		final Set<String> names = graphs.getGraphs().stream()
				.flatMap(g -> g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRConstants.name))
						.map(g::getEdgeTarget) // n
						.map(name -> {
							final Set<String> descendants = g.getDescendants(name); // adds n1..nN
							descendants.add(name); // adds n
							return descendants;
						})
						.flatMap(Set::stream))
				.collect(toSet());

		// (x /name (:op1 n1 .. :opN nN))
		graphs.getGraphs().stream()
				.flatMap(g -> g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRConstants.instance))
						.filter(e -> g.getEdgeTarget(e).equals(AMRConstants.name_concept))
						.map(g::getEdgeSource) // x
						.filter(x -> !names.contains(x))
						.map(g::getDescendants) // adds n1..nN
						.flatMap(Set::stream))
				.forEach(names::add);


		graphs.removeVertices(names);
	}

	private static void disambiguate_candidates(GraphList graphs)
	{
		log.info("Disambiguating candidates");
		// Use vertex weights to choose best candidate for each vertex
		graphs.getGraphs().forEach(g ->
				g.vertexSet().forEach(v ->
						graphs.getCandidates(v).stream()
								.max(comparingDouble(c -> c.getMeaning().getWeight()))
								.ifPresent(c -> graphs.chooseCandidate(v, c))));
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
		log.info("Collapsing multiwords");
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
			final Set<String> subsumed = g.getAlignments().getSpanVertices(c.getMention().getSpan());
			subsumed.remove(v); // do not include v itself!
			double s_max = subsumed.stream()
					.map(graphs::getCandidates)
					.flatMap(Collection::stream)
					.map(Candidate::getMeaning)
					.mapToDouble(Meaning::getWeight)
					.max().orElse(0.0);

			// Is there a descendant with a highest scored meaning?
			if (v_value >= s_max)
			{
				// this also updates meanings and coreference
				graphs.vertexContraction(g, v, subsumed);

				// Update remaining subsumers affected by this contraction
				final List<Pair<SemanticGraph, String>> renamed_multiwords = subsumers.stream()
						.filter(p -> subsumed.contains(p.getRight()))
						.collect(toList());
				renamed_multiwords.forEach(p ->
				{
					subsumers.remove(p);
					subsumers.add(Pair.of(p.getLeft(), v)); // subsumer node has been replaced by v
				});

				log.debug("Accepted multiword \"" + c.getMention().getSurface_form() + "\"\t" + c.getMeaning());
			}
			else
				log.debug("Rejected multiword \"" + c.getMention().getSurface_form() + "\"\t" + c.getMeaning());
		}
	}

	private static GlobalSemanticGraph merge(GraphList graphs)
	{
		log.info("Merging graphs");

		// Create merged (disconnected) graph
		GlobalSemanticGraph merged = new GlobalSemanticGraph();
		graphs.getGraphs().forEach(g -> g.edgeSet().forEach(e ->
		{
			String v1 = g.getEdgeSource(e);
			merged.addVertex(v1);
			String v2 = g.getEdgeTarget(e);
			merged.addVertex(v2);
			merged.addNewEdge(v1, v2, e.getLabel());

			graphs.getMentions(v1).forEach(m -> merged.addMention(v1, m));
			graphs.getMentions(v2).forEach(m -> merged.addMention(v2, m));

			graphs.getCandidates(v1).forEach(c ->
			{
				merged.setMeaning(v1, c.getMeaning());
				merged.addMention(v1, c.getMention());
			});
			graphs.getCandidates(v2).forEach(c ->
			{
				merged.setMeaning(v2, c.getMeaning());
				merged.addMention(v2, c.getMention());
			});
		}));

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
