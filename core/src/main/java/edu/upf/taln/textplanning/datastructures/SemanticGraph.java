package edu.upf.taln.textplanning.datastructures;

import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedSubgraph;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.HashSet;
import java.util.List;

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

	public static class SubGraph extends DirectedSubgraph<Node, Edge>
	{
		public final Node initialNode;

		public SubGraph(DirectedGraph<Node, Edge> base, Node node)
		{
			super(base, new HashSet<>(), new HashSet<>());
			addVertex(node);
			initialNode = node;
		}

		public SubGraph(SubGraph other, List<Edge> inExpansion)
		{
			super(other.getBase(), other.vertexSet(), other.edgeSet());
			initialNode = other.initialNode;

			inExpansion.stream()
					.map(e -> getBase().getEdgeSource(e))
					.forEach(this::addVertex);
			inExpansion.stream()
					.map(e -> getBase().getEdgeTarget(e))
					.forEach(this::addVertex);
			inExpansion.stream()
					.forEach(e -> addEdge(getBase().getEdgeSource(e), getBase().getEdgeTarget(e), e));
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
