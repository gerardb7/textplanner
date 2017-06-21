package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pattern extraction strategy based on finding dense subgraphs (trees) in a semantic graph.
 * Determination of heaviest subtrees approximated using local beam algorithm + expansions that preserve predicate-argument
 * structure of semantic graph.
 */
public class PatternExtraction
{
	/**
	 * Extracts tree-like patterns from a content graph
	 * @param g a content graph
	 * @param numPatterns number of patterns to extract
	 * @return a list of patterns sorted by relevance
	 */
	public static List<SemanticTree> extract(SemanticGraph g, int numPatterns, double lambda)
	{
		// All jgrapht graphs are tested for equality by comparing their vertex and edge sets.
		// Nodes are equal if they share the same id.
		// Edges are equal if they're have the same role, both are args, have the same weight, and source and target
		// nodes are also equal.

		// Work out average node weight, which will be used as weight for edges
		double avgWeight = g.vertexSet().stream().mapToDouble(Node::getWeight).average().orElse(1.0);

		// Collect verbal roots and select top ranked
		List<Node> roots = g.vertexSet().stream()
				.filter(n -> isInflectedVerb(g, n)) // is a predicate
				.filter(n -> g.inDegreeOf(n) == 0) // is root
				.filter(n -> g.outDegreeOf(n) > 0) // is connected
				.sorted((r1, r2) -> Double.compare(r2.getWeight(), r1.getWeight()))
				.limit(numPatterns)
				.collect(Collectors.toList());

		if (roots.isEmpty())
			return null;

		// Expand to initial trees
		List<Pair<SemanticTree, Double>> initialTrees = roots.stream()
				.map(SemanticTree::new)
				.map(t ->
				{
					PatternExtraction.addArguments(g, t);
					return t;
				})
				.map(t -> Pair.of(t, calculateWeight(g, t, avgWeight, lambda)))
				.sorted((s1, s2) -> Double.compare(s2.getRight(), s1.getRight()))
				.distinct()
				.collect(Collectors.toList());

		// Find best expansion for each tree
		Set<Pair<SemanticTree, Double>> topTrees = new HashSet<>();
		for (Pair<SemanticTree, Double> t : initialTrees)
		{
			boolean stop = false;
			Pair<SemanticTree, Double> currentTree = t;
			while (!stop)
			{
				Optional<Pair<SemanticTree, Double>> bestExtension = calculateExtensions(g, currentTree.getLeft()).stream()
						.map(ext -> Pair.of(ext, calculateWeight(g, ext, avgWeight, lambda)))
						.max(Comparator.comparing(Pair::getValue)); // greedy search

				double currentValue = currentTree.getRight();
				double newValue = bestExtension.isPresent() ? bestExtension.get().getRight() : currentValue;

				if (newValue > currentValue)
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
	private static double calculateWeight(SemanticGraph g, SemanticTree t, double edgeWeight, double lambda)
	{
		double ws = t.vertexSet().stream()
				.mapToDouble(Node::getWeight)
				.sum();
		double ds = t.edgeSet().size() * edgeWeight; // cost of edges in tree
		double dv = g.edgeSet().size() * edgeWeight; // cost of all edges in graph, used to keep weighting function nonnegative
		return lambda*ws - ds + dv;
	}

	/**
	 * Returns the set of expansions of a subtree relative to the graph it is a subgraph of.
	 * @return a set of expanded subtrees
	 */
	private static Set<SemanticTree> calculateExtensions(SemanticGraph g, SemanticTree s)
	{
		Set<SemanticTree> expansions = getLeafExpansions(g, s);
		expansions.forEach(si -> addArguments(g, si));

		return expansions;
	}

	/**
	 * Given a semantic graph g and a subtree t, return all subtrees resulting from adding to t an edge in g
	 * indicating a non-arg relation where the governor is a node in t.
	 * @return expanded set of subtrees
	 */
	private static Set<SemanticTree> getLeafExpansions(SemanticGraph g, SemanticTree t)
	{
		List<Triple<Node, Node, Edge>> edges = t.vertexSet().stream()
				.filter(g::containsVertex) // filters out replicated nodes
				.flatMap(v -> g.outgoingEdgesOf(v).stream()
						.filter(e -> !e.isArg()) // Only edges which point to non-argumental relations
						.map(e -> Triple.of(v, g.getEdgeTarget(e), e))
						.filter(e -> !PatternExtraction.containsEdge(t, e))) // edge must not be in t
				.collect(Collectors.toList());

		return edges.stream()
				.map(e ->
				{
					SemanticTree s2 = new SemanticTree(t);
					s2.expand(e.getLeft(),
							replicateNode(t, e.getMiddle()),
							new Edge(e.getRight().getRole(), e.getRight().isArg()));
					return s2;
				})
				.collect(Collectors.toSet());
	}

	/**
	 * Given a semantic graph g and a subtree t, for each node in t recursively add all its arguments in g.
	 */
	private static void addArguments(SemanticGraph g, SemanticTree t)
	{
		List<Triple<Node, Node, Edge>> edges;
		do
		{
			edges = t.vertexSet().stream()
					.filter(g::containsVertex) // filters out replicated nodes
					.flatMap(v -> g.outgoingEdgesOf(v).stream()  // collect edges to new args in g AND not in t
							.filter(Edge::isArg)
							.map(e -> Triple.of(v, g.getEdgeTarget(e), e))
							.filter(e -> !PatternExtraction.containsEdge(t, e))) // edge must not be in t
					.collect(Collectors.toList());

			edges.forEach(e -> t.expand(e.getLeft(),
										replicateNode(t, e.getMiddle()),
										new Edge(e.getRight().getRole(), e.getRight().isArg())));
		}
		while (!edges.isEmpty());
	}

	private static Node replicateNode(SemanticTree t, Node n)
	{
		int i = 0;
		Node r = n;
		while (t.containsVertex(r))
		{
			// replicate node to preserve tree structure
			String newId = n.getId() + "_" + ++i;
			r = new Node(newId, n.getEntity(), n.getWeight(), n.getId()); // create coreferent node
		}

		return r;
	}

	/**
	 * @return true if t contains an edge equal or equivalent to e (equivalent means source or target are correferent)
	 */
	private static boolean containsEdge(SemanticTree t, Triple<Node, Node, Edge> e)
	{
		Set<Node> sources_tree = t.vertexSet().stream()
				.filter(n -> n.equals(e.getLeft()) || n.corefers(e.getLeft()))
				.collect(Collectors.toSet());
		Set<Node> targets_tree = t.vertexSet().stream()
				.filter(n -> n.equals(e.getMiddle()) || n.corefers(e.getMiddle()))
				.collect(Collectors.toSet());
		return sources_tree.stream()
				.map(t::outgoingEdgesOf)
				.flatMap(Set::stream)
				.filter(e2 -> e.getRight().getRole().equals(e2.getRole()) && e.getRight().isArg() == e2.isArg())
				.map(t::getEdgeTarget)
				.anyMatch(targets_tree::contains);
	}

	private static boolean isInflectedVerb(SemanticGraph g, Node n)
	{
		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
		String pos = ((AnnotatedEntity) n.getEntity()).getAnnotation().getPOS();
		return g.isPredicate(n) && Arrays.stream(inflectedVerbs).anyMatch(pos::equals);
	}
}
