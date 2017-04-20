package edu.upf.taln.textplanning.datastructures;

import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Patterns extracted from a semantic graph are encoded as semantic trees.
 */
public class SemanticTree extends SimpleDirectedGraph<Node, Edge>
{
	private final Node root;
	private final double position;
	private static int counter = 0;

	/**
	 * Factory method: transforms a directed graph pattern into a set of trees with the given roots
	 * @return a set of semantic trees, one per each root in the pattern
	 */
	public static Set<SemanticTree> createTrees(SemanticGraph.SubGraph s, Set<Node> roots)
	{
		roots.forEach(r -> { assert s.containsVertex(r); });

		// Create copy of subgraph to be transformed
		SemanticGraph m = new SemanticGraph(Edge.class);
		s.vertexSet().forEach(m::addVertex);
		s.edgeSet().forEach(e -> m.addEdge(s.getEdgeSource(e), s.getEdgeTarget(e), e));

		// Collect nodes with multiple parents
		Set<Node> nodesToReplicate = m.vertexSet().stream()
				.filter(v -> m.inDegreeOf(v) > 1)
				.collect(Collectors.toSet());

		// Iteratively replicate nodes until all nodes have a one or no parents -> directed graph becomes a multitree
		while (!nodesToReplicate.isEmpty())
		{
			nodesToReplicate.forEach(n -> replicateNode(m, n));
			nodesToReplicate = m.vertexSet().stream()
					.filter(v -> m.inDegreeOf(v) > 1)
					.collect(Collectors.toSet());
		}

		// Create one tree per root
		Set<SemanticTree> trees = roots.stream()
				.map(SemanticTree::new)
				.collect(Collectors.toSet());

		// Populate trees
		trees.forEach(t -> m.outgoingEdgesOf(t.root).forEach(e -> t.populate(m, e)));
		return trees;
	}

	private static void replicateNode(SemanticGraph g, Node n)
	{
		assert g.containsVertex(n);
		Set<Edge> inLinks = g.incomingEdgesOf(n);
		Set<Edge> outLinks = g.outgoingEdgesOf(n);

		for (Edge i : inLinks)
		{
			Node s = g.getEdgeSource(i);
			String newId = n.id + "_" + ++counter;
			Node r = new Node(newId, n.entity, n.weight);
			g.addVertex(r);
			Edge sr = new Edge(i.role, i.isArg);
			g.addEdge(s, r, sr);
			outLinks.forEach(o -> {
					Edge o2 = new Edge(o.role, o.isArg);
					g.addEdge(r, g.getEdgeTarget(o), o2);
			});
		}

		// remove original node and edges from graph
		g.removeVertex(n);
	}

	private SemanticTree(Node inRoot)
	{
		super(Edge.class);
		root = inRoot;
		this.addVertex(inRoot);
		position = 1; // for unannotated trees
	}

	public SemanticTree(Node inRoot, double positon)
	{
		super(Edge.class);
		root = inRoot;
		this.addVertex(inRoot);
		this.position = positon; // annotated trees
	}

	public double getPosition()
	{
		return position;
	}

	/**
	 * Recursive method, expands tree starting from an edge in a simple directed graph.
	 * The source of the edge must be already part of the tree.
	 * If the target is also part of the tree, then a copy of the target node is created
	 * and added to the tree to prevent cycles.
	 */
	private void populate(SemanticGraph p, Edge e)
	{
		assert (p.containsEdge(e) && !this.containsEdge(e)); // no cycles allowed in tree!
		Node s = p.getEdgeSource(e);
		Node t = p.getEdgeTarget(e);
		assert (this.containsVertex(s) && !containsVertex(t)); // no multiple parents allowed in tree!

		addVertex(t);
		addEdge(s, t, e);
		p.outgoingEdgesOf(t).forEach(e2 -> populate(p, e2)); // Recursively add new edges
	}

	/**
	 * Calculates list of ndoes visited in preorder and in lexicographical order between siblings according to edge role
	 * and node id
	 * @return list of all edges in tree
	 */
	public List<Edge> getPreOrder() { return getPreOrder(root); }
	private List<Edge> getPreOrder(Node node)
	{
		// Sort of siblings by role and id
		List<Edge> preorder = new ArrayList<>();
		List<Edge> sortedSiblings = outgoingEdgesOf(node).stream()
				.sorted(Comparator.comparing(Edge::getRole)
						.thenComparing(Comparator.comparing(e -> this.getEdgeTarget(e).id)))
				.collect(Collectors.toList());

		for (Edge e: sortedSiblings)
		{
			preorder.add(e);
			preorder.addAll(getPreOrder(getEdgeTarget(e)));
		}
		return preorder;
	}

	public Node getRoot()
	{
		return this.root;
	}

	/**
	 * @return true if node has one or more arguments
	 */
	public boolean isPredicate(Node n)
	{
		return outgoingEdgesOf(n).stream().anyMatch(Edge::isArg);
	}

	public String toString()
	{
		return root + (outDegreeOf(root) == 0 ? " // " : "") +
				getPreOrder().stream()
				.map(e -> " -" + e.role + "-> " + getEdgeTarget(e) + (outDegreeOf(getEdgeTarget(e)) == 0 ? " // " : ""))
				.reduce(String::concat).orElse("");
	}
}
