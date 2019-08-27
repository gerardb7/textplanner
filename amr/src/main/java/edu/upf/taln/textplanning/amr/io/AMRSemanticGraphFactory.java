package edu.upf.taln.textplanning.amr.io;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.amr.structures.AMRGraph;
import edu.upf.taln.textplanning.amr.structures.AMRGraphList;
import edu.upf.taln.textplanning.amr.structures.CoreferenceChain;
import edu.upf.taln.textplanning.core.Options;
import edu.upf.taln.textplanning.core.io.SemanticGraphFactory;
import edu.upf.taln.textplanning.core.ranking.Disambiguation;
import edu.upf.taln.textplanning.core.structures.*;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.ConnectivityInspector;

import java.util.*;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

// Creates a single semantic graph from a lists of AMR-like semantic graphs
public class AMRSemanticGraphFactory implements SemanticGraphFactory<AMRGraphList>
{
	private final Options options;
	private final static Logger log = LogManager.getLogger();

	public AMRSemanticGraphFactory(Options options)
	{
		this.options = options;
	}

	public SemanticGraph create(AMRGraphList graphs)
	{
		Stopwatch timer = Stopwatch.createStarted();
		log.info("*Creating semantic graph*");

		// 1- pre-process AMR graphs
		remove_names(graphs); // <- won't work unless executed before remove_concepts
		Map<String, String> concepts = remove_concepts(graphs);

		// 2- disambiguate candidates
		Disambiguation disambiguation = new Disambiguation(options.disambiguation_lambda);
		final Map<Mention, Candidate> selected_candidates = disambiguation.disambiguate(List.copyOf(graphs.getCandidates()));

		// 3- create merged graph and assign to it disambiguated meanings
		SemanticGraph graph = mergeAMRGraphs(graphs, selected_candidates, concepts);

		// 4- post-process semantic graph
		mergeCorefenceChains(graph, graphs.getChains());
		mergeNEs(graph);
		resolve_arguments();

		// log some info
		ConnectivityInspector<String, Role> conn = new ConnectivityInspector<>(graph);
		final List<Set<String>> sets = conn.connectedSets();
		log.info("Merged graph has " + sets.size() + " components");
		log.debug(DebugUtils.printSets(graph, sets));

		log.info("Semantic graph created in " + timer.stop());
		return graph;
	}

	/**
	 * Given:
	 *      (x :name (n /name (:op1 n1 .. :opN nN))) or (x /name (:op1 n1 .. :opN nN))
	 * Removes all but x.
	 */
	private static void remove_names(AMRGraphList graphs)
	{
		log.info("Removing names");

		// (x :name (n /name (:op1 n1 .. :opN nN))) -> remove n, n1, .. ,nN
		// (x /name (:op1 n1 .. :opN nN)) -> remove n1, .., nN
		final Set<String> names = graphs.getGraphs().stream()
				.flatMap(g -> g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRSemantics.name))
						.map(g::getEdgeTarget) // n
						.map(n -> {
							final Set<String> n_names = getOpVertices(n, g); // adds n1..nN
							final Set<Role> edges = g.outgoingEdgesOf(n);

							// Remove n if it has no outgoing edges other than /name and :op
							if (edges.stream()
									.map(Role::getLabel)
									.noneMatch(r -> !r.equals(AMRSemantics.instance) && !AMRSemantics.isOpRole(r)))
								n_names.add(n); // adds n
							return n_names;
						})
						.flatMap(Set::stream))
				.collect(toSet());

		graphs.removeVertices(names);
	}

	private static Set<String> getOpVertices(String v, AMRGraph g)
	{
		return g.outgoingEdgesOf(v).stream()
				.filter(e -> AMRSemantics.isOpRole(e.getLabel()))
				.map(g::getEdgeTarget)
				.collect(toSet());

	}

	private static Map<String, String> remove_concepts(AMRGraphList graphs)
	{
		log.info("Removing concepts");
		final Map<String, String> concepts = graphs.getGraphs().stream()
				.flatMap(g -> g.edgeSet().stream()
						.filter(e -> e.getLabel().equals(AMRSemantics.instance))
						.map(e -> Pair.of(g.getEdgeSource(e), g.getEdgeTarget(e))))
				.collect(toMap(Pair::getLeft, Pair::getRight));

		graphs.removeVertices(concepts.values());
		return concepts;
	}

	private static SemanticGraph mergeAMRGraphs(AMRGraphList graphs, Map<Mention, Candidate> candidates,
	                                            Map<String, String> concepts)
	{
		log.info("Merging graphs");

		// Create merged (disconnected) graph
		SemanticGraph merged = new SemanticGraph();
		graphs.getGraphs().forEach(g -> g.edgeSet().forEach(e ->
		{
			String v1 = g.getEdgeSource(e);
			merged.addVertex(v1);
			String v2 = g.getEdgeTarget(e);
			merged.addVertex(v2);
			merged.addNewEdge(v1, v2, e.getLabel());

			graphs.getMentions(v1).forEach(m -> merged.addMention(v1, m));
			graphs.getMentions(v2).forEach(m -> merged.addMention(v2, m));

			// There should be just one mention per vertex!
			merged.vertexSet().forEach(v ->
					merged.getMentions(v).stream()
							.map(candidates::get)
							.forEach(c -> {
								merged.setMeaning(v, c.getMeaning());
								merged.setWeight(v, c.getWeight().orElse(0.0));
							}));

			if (concepts.containsKey(v1))
				merged.addType(v1, concepts.get(v1));
			if (concepts.containsKey(v2))
				merged.addType(v2, concepts.get(v2));

		}));

		return merged;
	}

	private static void mergeCorefenceChains(SemanticGraph graph, List<CoreferenceChain> chains)
	{
		// Merge all coreferent vertices
		chains.stream()
				.filter(c -> c.getSize() > 1)
				.forEach(c ->
				{
					Collection<String> C = c.getVertices();
					// v node, whose meaning is kept, corresponds to that with the highest scored meaning
					String v = C.stream()
							.max(comparingDouble(v_i -> graph.getWeight(v_i).orElse(0.0))).orElse(null);
					C.remove(v);
					log.debug(DebugUtils.printCorefMerge(v, C, graph));

					graph.vertexContraction(v, C);
				});
	}

	private static void mergeNEs(SemanticGraph graph)
	{
		// Merge all vertices referring to the same NE
		Multimap<String, String> vertices_to_NEs = HashMultimap.create();
		graph.vertexSet().forEach(v -> graph.getMeaning(v)
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
					graph.vertexContraction(v, C); // keeps v's meaning, which is ok as all in C share same meaning
				});
	}

	private static void resolve_arguments() {}
}
