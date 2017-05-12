package edu.upf.taln.textplanning.datastructures;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedSubgraph;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.HashSet;

/**
 * A semantic graph is a rooted directed simple graph with weighted nodes and labels on both edges and nodes.
 */
public class SemanticGraph extends SimpleDirectedGraph<SemanticGraph.Node, SemanticGraph.Edge>
{
	public static class Node
	{
		public final String id; // unique id, determines if two nodes are the same!
		public final Entity entity;
		public final double weight;

		public Node(String id, Entity inEntity, double weight)
		{
			this.id = id;
			this.entity = inEntity;
			this.weight = weight;
		}

		public String getId()
		{
			return id;
		}

		public Entity getEntity()
		{
			return entity;
		}

		public double getWeight()
		{
			return weight;
		}

		@Override
		public String toString() { return this.entity.toString(); }

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			Node node = (Node) o;
			return id.equals(node.id);
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

		public String getRole()
		{
			return role;
		}

		public boolean isArg()
		{
			return isArg;
		}

		@Override
		public String toString() { return role; }
	}

	/**
	 * Implements connected rooted subgraphs of a semantic graph
	 */
	public static class SubGraph extends DirectedSubgraph<Node, Edge>
	{
		private Node root;

		public SubGraph(DirectedGraph<Node, Edge> base, Node root)
		{
			super(base, new HashSet<>(), new HashSet<>());
			addVertex(root);
			this.root = root;
		}

		public SubGraph(SubGraph other, Edge e)
		{
			super(other.getBase(), other.vertexSet(), other.edgeSet());
			this.root = other.root;
			expand(e); // may update root
		}

		public Node getRoot() { return root; }


		public boolean isValidExpansion(Edge e)
		{
			Node s = base.getEdgeSource(e);
			Node t = base.getEdgeTarget(e);
			if (s.equals(t))
				return false; // ni loops
			if (!base.containsEdge(e))
				return false; // keep subgraph consistent with base
			if (containsEdge(e))
				return false; // no duplicated edges
			if (!containsVertex(s) && !containsVertex(t))
				return false; // keep subgraph connected
			return !(t.equals(root) && containsVertex(s));
		}


		/**
		 * Adds edge in base to subgraph, while checking that the subgraph remains a connected rooted subgraph.
		 */
		public void expand(Edge e)
		{
			if (!isValidExpansion(e))
				throw new RuntimeException("Invalid extension to subgraph");
			Node s = base.getEdgeSource(e);
			Node t = base.getEdgeTarget(e);

			if (!containsVertex(s))
			{
				addVertex(s);

				// update root
				if (root.equals(t))
					root = s;
			}

			if (!containsVertex(t))
				addVertex(t);
			addEdge(s, t, e);
		}
	}

	public SemanticGraph(Class<? extends Edge> edgeClass)
	{
		super(edgeClass);
	}

	/**
	 * @return true if node has one or more arguments
	 */
	public boolean isPredicate(Node n)
	{
		return outgoingEdgesOf(n).stream().anyMatch(Edge::isArg);
	}
}
