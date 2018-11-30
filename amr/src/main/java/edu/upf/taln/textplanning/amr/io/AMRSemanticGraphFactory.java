package edu.upf.taln.textplanning.amr.io;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.core.io.SemanticGraphFactory;
import edu.upf.taln.textplanning.amr.structures.AMRGraphList;
import edu.upf.taln.textplanning.amr.structures.AMRGraph;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Role;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.ConnectivityInspector;

import java.util.*;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

// Creates global semantic graphs from a lists of AMR-like semantic graphs
public class AMRSemanticGraphFactory implements SemanticGraphFactory<AMRGraphList>
{
	private final static Logger log = LogManager.getLogger();

	public SemanticGraph create(AMRGraphList graphs)
	{
		Stopwatch timer = Stopwatch.createStarted();
		log.info("*Creating global graphs*");

		remove_names(graphs); // <- won't work unless executed before remove_concepts
		Map<String, String> concepts = remove_concepts(graphs);
		disambiguate_candidates(graphs);
		collapse_multiwords(graphs);
		SemanticGraph merge = merge(graphs, concepts);
		resolve_arguments();

		// log some info
		ConnectivityInspector<String, Role> conn = new ConnectivityInspector<>(merge);
		final List<Set<String>> sets = conn.connectedSets();
		log.info("Merged graph has " + sets.size() + " components");
		log.debug(DebugUtils.printSets(merge, sets));

		log.info("Global graphs created in " + timer.stop());
		return merge;
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

	private static void disambiguate_candidates(AMRGraphList graphs)
	{
		log.info("Disambiguating candidates");
		// Use vertex weights to choose best candidate for each vertex
		graphs.getGraphs().forEach(g ->
				g.vertexSet().forEach(v ->
				{
					final List<Candidate> candidates = graphs.getCandidates(v).stream()
							.sorted(comparingDouble((Candidate c) -> c.getMeaning().getWeight()).reversed())
							.collect(toList());
					if (!candidates.isEmpty())
					{
						final Candidate max = candidates.get(0);
						final Optional<Candidate> max_multiword = candidates.stream()
								.filter(c -> c.getMention().isMultiWord())
								.findFirst();

						// Multiwords get priority if within highest scored candidates
						Candidate selected = max;
						if (max_multiword.isPresent())
						{
							final double max_multiword_weigth = max_multiword.get().getMeaning().getWeight();
							DescriptiveStatistics stats = new DescriptiveStatistics(candidates.stream()
									.map(Candidate::getMeaning)
									.mapToDouble(Meaning::getWeight)
									.toArray());

							if (max_multiword_weigth >= stats.getMean())
								selected = max_multiword.get();
						}
						graphs.chooseCandidate(v, selected);
					}
				}));
	}

	/**
	 * Subsumers: vertices with a multiword meaning and at least one descendant
	 * Remember:    - vertices have 1 or 2 spans, one for the aligned token and one covering all descendants
	 * 	              - candidates are assigned to vertices matching the span of their mentions
	 * 	              - after disambiguation each vertex has a single candidate
	 * 	              - multiword candidates subsume all descendants of the vertex they're associated with
	 */
	private static void collapse_multiwords(AMRGraphList graphs)
	{
		log.info("Collapsing multiwords");
		LinkedList<Pair<AMRGraph, String>> subsumers = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> !graphs.getCandidates(v).isEmpty())
						.filter(v -> graphs.getCandidates(v).iterator().next().getMention().isMultiWord())
						.filter( v -> !g.getDescendants(v).isEmpty())
						.map(v -> Pair.of(g, v)))
				.collect(toCollection(LinkedList::new));

		while(!subsumers.isEmpty())
		{
			// Pop a subsumer from the queue
			final Pair<AMRGraph, String> subsumer = subsumers.pop();
			final AMRGraph g = subsumer.getLeft();
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
				final List<Pair<AMRGraph, String>> renamed_multiwords = subsumers.stream()
						.filter(p -> subsumed.contains(p.getRight()))
						.collect(toList());
				renamed_multiwords.forEach(p ->
				{
					subsumers.remove(p);
					subsumers.add(Pair.of(p.getLeft(), v)); // subsumer node has been replaced by v
				});

				log.debug("Collapsed multiword \"" + c.getMention().getSurface_form() + "\"\t" + c.getMeaning());
			}
			else
				log.debug("Kept separate words \"" + c.getMention().getSurface_form() + "\"\t" + c.getMeaning());
		}
	}

	private static SemanticGraph merge(AMRGraphList graphs, Map<String, String> concepts)
	{
		log.info("Merging graphs");

		// Create merged (disconnected) graph
		SemanticGraph merged = new SemanticGraph();
		graphs.getGraphs().forEach(g -> g.edgeSet().forEach(e ->
		{
			String v1 = g.getEdgeSource(e);
			merged.addVertex(v1);
			merged.addSource(v1, g.getSource());
			String v2 = g.getEdgeTarget(e);
			merged.addVertex(v2);
			merged.addSource(v2, g.getSource());
			merged.addNewEdge(v1, v2, e.getLabel());

			graphs.getMentions(v1).forEach(m -> merged.addMention(v1, m));
			graphs.getMentions(v2).forEach(m -> merged.addMention(v2, m));

			// There should be just one candidate, but anyway...
			graphs.getCandidates(v1).forEach(c -> merged.setMeaning(v1, c.getMeaning()));
			graphs.getCandidates(v2).forEach(c -> merged.setMeaning(v2, c.getMeaning()));

			if (concepts.containsKey(v1))
				merged.addType(v1, concepts.get(v1));
			if (concepts.containsKey(v2))
				merged.addType(v2, concepts.get(v2));

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
					log.debug(DebugUtils.printCorefMerge(v, C, merged));

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
