package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.min;

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
	 * @return the set of extracted patterns
	 */
	public static Set<SemanticTree> extract(SemanticGraph g, int numPatterns, double lambda)
	{
		// Work out average node weight, which will be used as weight for edges
		double avgWeight = g.vertexSet().stream().mapToDouble(Node::getWeight).average().orElse(1.0);

		// Extract patterns
		Set<SemanticTree> patterns = new HashSet<>();
		boolean stop = patterns.size() == numPatterns;
		while(!stop)
		{
			SemanticTree heavySubtree = extractHeavySubtree(1, g, avgWeight, lambda);
			if (heavySubtree != null)
			{
				patterns.add(heavySubtree);

				// Remove edges in subtree from base graph
				heavySubtree.edgeSet().forEach(g::removeEdge);

				stop = patterns.size() == numPatterns;
			}
			else
				stop = true;
		}

		return patterns;
	}

	/**
	 * Local beam search for suboptimal heavy tree.
	 * @param k size of the beam
	 * @param g content graph
	 * @param edgeWeight cost of edges in subtree, is substracted from node weights
	 * @param lambda balancing factor between node weight and edge cost
	 * @return a heavy subtree or null if no graph was found
	 */
	private static SemanticTree extractHeavySubtree(int k, SemanticGraph g, double edgeWeight, double lambda)
	{
		// All jgrapht graphs are tested for equality by comparing their vertex and edge sets.
		// Nodes are equal if they share the same id.
		// Edges are equal if they're have the same role, both are args, have the same weight, and source and target
		// nodes are also equal.

		// Start off from k top ranked verbal predicates
		List<Node> topNodes = g.vertexSet().stream()
				.filter(n -> isInflectedVerb(g, n)) // is a predicate
				.filter(n -> g.inDegreeOf(n) == 0) // is root
				.filter(n -> g.outDegreeOf(n) > 0) // is connected
				.sorted((v1, v2) -> Double.compare(v2.getWeight(), v1.getWeight()))// swapped v1 and v2 to obtain descending order
				.limit(k)
				.collect(Collectors.toList());

		if (topNodes.isEmpty())
			return null;

		// Create beam of open states: patterns corresponding to top predicates with their (recursive) arguments)
		PriorityQueue<Pair<SemanticTree, Double>> beam =
			new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight())); // swapped s1 and s2 to obtain descending order
		topNodes.stream()
				.map(SemanticTree::new)
				.map(s -> { PatternExtraction.addArguments(g, s); return s; }) // get expanded subtrees
				.map(s -> Pair.of(s, calculateWeight(g, s, edgeWeight, lambda)))
				.forEach(beam::add);

		// Create set of visited subtrees that have already been expanded (visited states)
		Set<SemanticTree> visited = new HashSet<>();
		beam.forEach(p -> visited.add(p.getLeft()));

		Set<Pair<SemanticTree, Double>> next;
		do
		{
			// Expand subtrees in beam to find potential next states to visit
			next = beam.stream()
					.map(Pair::getLeft)
					.map(s -> getExpansions(g,s)) // get expanded beam
					.flatMap(Set::stream)
					.filter(s -> !visited.contains(s)) // discard visited states
					.map(s -> Pair.of(s, calculateWeight(g, s, edgeWeight, lambda))) // weight subtree
					.collect(Collectors.toSet());

			// Update list of visited states
			next.forEach(p -> visited.add(p.getLeft()));

			// Update beam: 1- add new states to it
			beam.addAll(next);
			// Update beam: 2- poll top k states
			PriorityQueue<Pair<SemanticTree, Double>> bestSubtrees = new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight()));
			IntStream.range(0, min(k, beam.size()))
					.mapToObj(i -> beam.poll())
					.forEach(bestSubtrees::add);

			// Update beam: 3- replace old beam with top k states
			beam.clear();
			beam.addAll(bestSubtrees);

		}
		while (!next.isEmpty());

		if (beam.isEmpty())
			return null;

		return beam.poll().getLeft();
	}

	/**
	 * Calculates the cost of a tree as a combination of node weights and edge distances
	 * @param g graph containing the tree
	 * @param s tree to weight
	 * @param edgeWeight fixed weight assigned to each edge
	 * @return cost of the tree
	 */
	private static double calculateWeight(SemanticGraph g, SemanticTree s, double edgeWeight, double lambda)
	{
		double ws = s.vertexSet().stream()
				.mapToDouble(Node::getWeight)
				.sum();
		double ds = s.edgeSet().size() * edgeWeight; // assign edges the average node weight
		double dv = g.edgeSet().size() * edgeWeight;
		return lambda*ws - ds + dv;
	}

	/**
	 * Returns the set of expansions of a subtree relative to the graph it is a subgraph of.
	 * @return a set of expanded subtrees
	 */
	private static Set<SemanticTree> getExpansions(SemanticGraph g, SemanticTree s)
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
		return t.vertexSet().stream()
				.filter(v -> v.getCoref() == null) // replicated nodes -> ignore
				.flatMap(n -> g.outgoingEdgesOf(n).stream()
						.filter(e -> !e.isArg()) // Only edges which point to non-argumental relations
						.filter(e -> !PatternExtraction.containsEdge(t, g.getEdgeSource(e), g.getEdgeTarget(e), e))) // check that e not in t
				.map(e -> {
					SemanticTree s2 = new SemanticTree(t);
					s2.expand(  g.getEdgeSource(e),
								replicateNode(s2, g.getEdgeTarget(e)),
								new Edge(e.getRole(), e.isArg()));
					return s2;
				})
				.collect(Collectors.toSet());
	}

	/**
	 * Given a semantic graph g and a subtree t, for each node in t recursively add all its arguments in g.
	 */
	private static void addArguments(SemanticGraph g, SemanticTree t)
	{
		List<Edge> edgesToArgs;
		do
		{
			edgesToArgs = t.vertexSet().stream()
					.filter(v -> v.getCoref() == null) // replicated nodes -> ignore
					.flatMap(v -> g.outgoingEdgesOf(v).stream()  // collect edges to new args in g AND not in t
							.filter(Edge::isArg)
							.filter(e -> !PatternExtraction.containsEdge(t, g.getEdgeSource(e), g.getEdgeTarget(e), e))) // check again that e not in t
					.collect(Collectors.toList());
			edgesToArgs.forEach(e -> t.expand(  g.getEdgeSource(e),
												replicateNode(t, g.getEdgeTarget(e)), // replicates node if edge produces cycle
												new Edge(e.getRole(), e.isArg())));
		}
		while (!edgesToArgs.isEmpty());
	}


	private static Node replicateNode(SemanticTree t, Node n)
	{
		int i = 0;
		Node r = n;
		while (t.containsVertex(r))
		{
			// replicate node to avoid cycles in tree
			String newId = n.getId() + "_" + ++i;
			r = new Node(newId, n.getEntity(), n.getWeight(), n.getId()); // create coreferent node
		}

		return r;
	}

	/**
	 * @return true if t contains an edge equivalent to e between nodes identical or correferent with source and dest
	 */
	private static boolean containsEdge(SemanticTree t, Node source, Node dest, Edge e)
	{
		Set<Node> sources_tree = t.vertexSet().stream().filter(n -> n.equals(source) || n.corefers(source)).collect(Collectors.toSet());
		Set<Node> targets_tree = t.vertexSet().stream().filter(n -> n.equals(dest) || n.corefers(dest)).collect(Collectors.toSet());
		return sources_tree.stream()
				.map(t::outgoingEdgesOf)
				.flatMap(Set::stream)
				.filter(e2 -> e.getRole().equals(e2.getRole()) && e.isArg() == e2.isArg())
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
