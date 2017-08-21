package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.structures.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pattern extraction strategy based on finding dense subgraphs (trees) in a semantic graph.
 * Determination of heaviest subtrees approximated using local beam algorithm + expansions that preserve predicate-argument
 * structure of semantic graph.
 */
public class PatternExtraction
{
	private final static Logger log = LoggerFactory.getLogger(PatternExtraction.class);

	/**
	 * Extracts tree-like patterns from a content graph
	 * @param g a content graph
	 * @param numPatterns number of patterns to extract
	 * @return a list of patterns sorted by relevance
	 */
	public static List<ContentPattern> extract(ContentGraph g, int numPatterns, double lambda)
	{
		// All jgrapht graphs are tested for equality by comparing their vertex and edge sets.
		// Nodes are equal if they share the same id.
		// Edges are equal if they're have the same role, both are args, have the same weight, and source and target
		// nodes are also equal.

		// Work out average node weight, which will be used as weight for edges
		double avgWeight = g.vertexSet().stream()
				.mapToDouble(Entity::getWeight)
				.average().orElse(1.0);

		// Collect verbal roots and select top ranked
		List<Entity> roots = g.vertexSet().stream()
				.filter(n -> isInflectedVerb(g, n)) // is a predicate
				.filter(n -> g.inDegreeOf(n) == 0) // is root
				.filter(n -> g.outDegreeOf(n) > 0) // is connected
				.sorted((r1, r2) -> Double.compare(r2.getWeight(), r1.getWeight()))
				.limit(numPatterns)
				.collect(Collectors.toList());

		if (roots.isEmpty())
			return null;

		// Expand to initial trees
		List<Pair<ContentPattern, Double>> initialTrees = roots.stream()
				.map(r -> new ContentPattern(g, r))
				.peek(t -> PatternExtraction.addArguments(g, t))
				.map(t -> Pair.of(t, calculateWeight(g, t, avgWeight, lambda)))
				.sorted((s1, s2) -> Double.compare(s2.getRight(), s1.getRight()))
				.distinct()
				.collect(Collectors.toList());

		// Find best expansion for each tree
		Set<Pair<ContentPattern, Double>> topTrees = new HashSet<>();
		for (Pair<ContentPattern, Double> t : initialTrees)
		{
			boolean stop = false;
			Pair<ContentPattern, Double> currentTree = t;
			while (!stop)
			{
				Optional<Pair<ContentPattern, Double>> bestExtension = calculateExtensions(g, currentTree.getLeft()).stream()
						.map(ext -> Pair.of(ext, calculateWeight(g, ext, avgWeight, lambda)))
						.max(Comparator.comparing(Pair::getValue)); // greedy search

				double currentValue = currentTree.getRight();
				double newValue = bestExtension.map(Pair::getRight).orElse(currentValue);

				if (bestExtension.isPresent() && newValue > currentValue)
				{
					 currentTree = bestExtension.get();
				}
				else
				{
					topTrees.add(currentTree);
					stop = true;
				}
			}
		}

		// sort expanded trees and return
		return topTrees.stream()
				.sorted((s1, s2) -> Double.compare(s2.getRight(), s1.getRight()))
				.map(Pair::getLeft)
				.collect(Collectors.toList());
	}

	/**
	 * Calculates the cost of a tree as a combination of node weights and edge distances
	 * @param g graph containing the tree
	 * @param t tree to weight
	 * @param edgeWeight fixed weight assigned to each edge
	 * @return cost of the tree
	 */
	private static double calculateWeight(ContentGraph g, ContentPattern t, double edgeWeight, double lambda)
	{
		double ws = t.vertexSet().stream()
				.mapToDouble(Entity::getWeight)
				.sum();
		double ds = t.edgeSet().size() * edgeWeight; // cost of edges in tree
		double dv = g.edgeSet().size() * edgeWeight; // cost of all edges in graph, used to keep weighting function nonnegative
		return lambda*ws - ds + dv;
	}

	/**
	 * Returns the set of expansions of a subtree relative to the graph it is a subgraph of.
	 * @return a set of expanded subtrees
	 */
	private static Set<ContentPattern> calculateExtensions(ContentGraph g, ContentPattern s)
	{
		Set<ContentPattern> expansions = getLeafExpansions(g, s);
		expansions.forEach(si -> addArguments(g, si));

		return expansions;
	}

	/**
	 * Given a semantic graph g and a subtree t, return all subtrees resulting from adding to t an edge in g
	 * indicating a non-arg relation where the governor is a node in t.
	 * @return expanded set of subtrees
	 */
	private static Set<ContentPattern> getLeafExpansions(ContentGraph g, ContentPattern t)
	{
		List<Triple<Entity, Entity, Role>> edges = t.vertexSet().stream()
				.filter(g::containsVertex) // filters out replicated nodes
				.flatMap(v -> g.outgoingEdgesOf(v).stream()
						.filter(e -> !e.isCore()) // Only edges which point to non-argumental relations
						.map(e -> Triple.of(v, g.getEdgeTarget(e), e))
						.filter(e -> !PatternExtraction.containsEdge(t, e))) // edge must not be in t
				.collect(Collectors.toList());

		return edges.stream()
				.map(e ->
				{
					ContentPattern s2 = new ContentPattern(t);
					s2.addVertex(e.getMiddle());
					s2.addEdge(e.getLeft(), e.getMiddle(), e.getRight()); //new Role(e.getRight().getRole(), e.getRight().isCore()));
					return s2;
				})
				.collect(Collectors.toSet());
	}

	/**
	 * Given a semantic graph g and a subtree t, for each node in t recursively add all its arguments in g.
	 */
	private static void addArguments(ContentGraph g, ContentPattern t)
	{
		List<Triple<Entity, Entity, Role>> edges;
		do
		{
			edges = t.vertexSet().stream()
					.filter(g::containsVertex) // filters out replicated nodes
					.flatMap(v -> g.outgoingEdgesOf(v).stream()  // collect edges to new args in g AND not in t
							.filter(Role::isCore)
							.map(e -> Triple.of(v, g.getEdgeTarget(e), e))
							.filter(e -> !PatternExtraction.containsEdge(t, e))) // edge must not be in t
					.collect(Collectors.toList());

			edges.forEach(e -> {
				try
				{
					t.addVertex(e.getMiddle());
					t.addEdge(e.getLeft(), e.getMiddle(), e.getRight()); //new Role(e.getRight().getRole(), e.getRight().isCore()));
				}
				catch (Exception ex)
				{
					log.error("Cannot add edge " + e + " to pattern subgraph: " + ex);
				}
			});
		}
		while (!edges.isEmpty());
	}

	/**
	 * @return true if t contains an edge equivalent to e
	 */
	private static boolean containsEdge(ContentPattern t, Triple<Entity, Entity, Role> e)
	{
		Set<Entity> sources_tree = t.vertexSet().stream()
				.filter(n -> n.equals(e.getLeft()))
				.collect(Collectors.toSet());
		Set<Entity> targets_tree = t.vertexSet().stream()
				.filter(n -> n.equals(e.getMiddle()))
				.collect(Collectors.toSet());
		return sources_tree.stream()
				.map(t::outgoingEdgesOf)
				.flatMap(Set::stream)
				.filter(e2 -> e.getRight().getRole().equals(e2.getRole()) && e.getRight().isCore() == e2.isCore())
				.map(t::getEdgeTarget)
				.anyMatch(targets_tree::contains);
	}

	private static boolean isInflectedVerb(ContentGraph g, Entity e)
	{
		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};

		// Find an anchor for the entity
		AnnotatedWord anchor = g.getAnchors(e).get(0).getHead();
		String pos = anchor.getPOS();

		// Check if the anchor is an inflected verb with arguments in its linguistic structure
		return anchor.getStructure().isPredicate(anchor) && Arrays.stream(inflectedVerbs).anyMatch(pos::equals);
	}
}
