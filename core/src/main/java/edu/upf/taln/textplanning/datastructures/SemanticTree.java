package edu.upf.taln.textplanning.datastructures;

import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Patterns extracted from a semantic graph are encoded as semantic trees.
 */
public class SemanticTree extends SimpleDirectedGraph<Node, Edge>
{
	private Node root; // not final
	private final double position; // for annotated trees

	public SemanticTree(Node root)
	{
		this(root, 1);
	}

	public SemanticTree(Node root, double position)
	{
		super(Edge.class);
		this.root = root;
		this.addVertex(this.root);
		this.position = position; // annotated trees
	}

	public SemanticTree(SemanticTree other)
	{
		super(Edge.class);

		// Copy pointers to nodes and replicate edges
		other.vertexSet().forEach(this::addVertex);
		other.edgeSet().forEach(ei ->
				this.addEdge(this.getEdgeSource(ei), this.getEdgeTarget(ei), new Edge(ei.getRole(), ei.isArg())));

		this.root = other.root;
		this.position = other.position;
	}

	public double getPosition()
	{
		return position;
	}
	public Node getRoot() { return root; }

	/**
	 * Expands tree by adding an edge e going from a node in the tree s to a new node t
	 * @param s node in the tree
	 * @param t node to be added to the tree
	 * @param e edge to be added between s and t
	 */
	public void expand(Node s, Node t, Edge e)
	{
		if (!isValidExpansion(s, t))
			throw new RuntimeException("Invalid extension");

		addVertex(t);
		addEdge(s, t, e);
	}

	private boolean isValidExpansion(Node s, Node t)
	{
		if (!containsVertex(s))
			return false; // source must always be a node in the tree
		else if (containsVertex(t))
			return false; // target must be a node which is not part of the tree

		return true;
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
						.thenComparing(Comparator.comparing(e -> this.getEdgeTarget(e).getId())))
				.collect(Collectors.toList());

		for (Edge e: sortedSiblings)
		{
			preorder.add(e);
			preorder.addAll(getPreOrder(getEdgeTarget(e)));
		}
		return preorder;
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
				.map(e -> " -" + e.getRole() + "-> " + getEdgeTarget(e) + (outDegreeOf(getEdgeTarget(e)) == 0 ? " // " : ""))
				.reduce(String::concat).orElse("");
	}
}
