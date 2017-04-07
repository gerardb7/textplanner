package edu.upf.taln.textplanning.datastructures;

import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Patterns extracted from a semantic graph are encoded as semantic trees.
 */
public class SemanticTree extends DirectedAcyclicGraph<Node, Edge>
{
	private final Node root;
	private static int counter = 0;

	/**
	 * Factory method: transforms a directed graph pattern into a set of trees
	 * @return a set of semantic trees, one per each root in the pattern
	 */
	public static Set<SemanticTree> createTrees(SemanticGraph.SubGraph s)
	{
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

		// Collect roots of multitree
		Set<Node> roots = m.vertexSet().stream()
				.filter(v -> m.inDegreeOf(v) == 0)
				.collect(Collectors.toSet());

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

		for (Edge st : g.incomingEdgesOf(n))
		{
			Node s = g.getEdgeSource(st);
			Node t = g.getEdgeTarget(st);
			String newId = t.id + "_" + ++counter;
			Node r = new Node<>(newId, t.entity, t.weight, t.isPredicate, t.data);
			g.addVertex(r);
			Edge sr = new Edge(st.role, st.isArg);
			g.addEdge(s, r, sr);
			g.removeEdge(st);
			g.outgoingEdgesOf(n).forEach(o -> g.addEdge(r, g.getEdgeTarget(o), o));
			g.removeAllEdges(g.outgoingEdgesOf(n));
		}
	}

	private SemanticTree(Node inRoot)
	{
		super(Edge.class);
		root = inRoot;
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
	 * @return list of all edges in tree in preorder
	 */
	public List<Edge> getPreOrder() { return getPreOrder(root); }
	private List<Edge> getPreOrder(Node node)
	{
		List<Edge> preorder = new ArrayList<>();
		for (Edge e: outgoingEdgesOf(node))
		{
			preorder.add(e);
			preorder.addAll(getPreOrder(getEdgeTarget(e)));
		}
		return preorder;
	}
}
