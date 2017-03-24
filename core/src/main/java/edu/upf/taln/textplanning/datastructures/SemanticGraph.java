package edu.upf.taln.textplanning.datastructures;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * A semantic graph is a rooted directed simple graph with weighted nodes and labels on both edges and nodes.
 */
public class SemanticGraph extends SimpleDirectedGraph<SemanticGraph.Node, SemanticGraph.Edge>
{
	public static class Node<T>
	{
		public final String id; // unique id, determines if two nodes are the same!
		public final String entity;
		public final double weight;
		public final boolean isPredicate;// If true, entity denotes a situation/event
		public final T data; // Any additional data about referred entity goes here

		public Node(String id, String inEntity, double weight, boolean isPredicate, T data)
		{
			this.id = id;
			this.entity = inEntity;
			this.weight = weight;
			this.isPredicate = isPredicate;
			this.data = data;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof Node))
				return false;
			Node other = (Node)obj;
			return id.equalsIgnoreCase(other.id);
		}

		@Override
		public int hashCode()
		{
			return id.hashCode();
		}
	}

	public static class Edge extends DefaultEdge
	{
		public final String role;
		public final boolean isArg;

		public Edge(String role, boolean isArg)
		{
			this.role = role;
			this.isArg = isArg;
		}
	}

	public static class SubTree extends SimpleDirectedGraph<Node, Edge>
	{
		private final SemanticGraph base;
		private final Node root;
		private static int counter = 0;

		public SubTree(SemanticGraph base, Node root)
		{
			super(Edge.class);

			if (!base.containsVertex(root))
				throw new RuntimeException("Root node must be part of base semantic graph");
			this.base = base;
			this.root = root;
			addVertex(root);
		}

		public SubTree(SubTree other)
		{
			super(Edge.class);
			this.base = other.base;
			this.root = other.root;
			other.getPreOrder().forEach(this::addChild);
		}

		public void addChild(Edge e)
		{
			if (!base.containsEdge(e))
				throw new RuntimeException("Edge must be part of base");
			if (this.containsEdge(e))
				return;

			Node parent = base.getEdgeSource(e);
			if (!containsVertex(parent))
				throw new RuntimeException("Cannot add edge because source is not part of tree");

			Node child = base.getEdgeTarget(e);
			if (containsVertex(child))
			{
				// If target of edge is part of tree, create duplicate
				child = new Node<>(child.id + "_" + ++counter,  child.entity, child.weight, child.isPredicate, child.data);
			}

			addVertex(child);
			addEdge(parent, child, e);
		}

		public Node getRoot() { return this.root; }
		public SemanticGraph getBase() { return this.base; }

		public List<Edge> getPreOrder() { return getPreOrder(root);	}
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

		@Override
		public boolean addVertex(Node v) { throw new RuntimeException("Operation not supported"); }
		@Override
		public Edge addEdge(Node s, Node t) { throw new RuntimeException("Operation not supported"); }
		@Override
		public boolean addEdge(Node s, Node t, Edge e) { throw new RuntimeException("Operation not supported"); }
	}

	public SemanticGraph(Class<? extends Edge> edgeClass)
	{
		super(edgeClass);
	}
}
