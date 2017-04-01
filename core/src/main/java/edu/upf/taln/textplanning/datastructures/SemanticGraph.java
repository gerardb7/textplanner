package edu.upf.taln.textplanning.datastructures;

import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.*;
import java.util.stream.Collectors;

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

	/**
	 * A SemanticPattern is a multitree formed from a subset of the nodes and edges of a semantic graph.
	 * A multitree is a directed acyclic graph where all the nodes reachable from any node form a tree.
	 */
	public static class SemanticPattern extends DirectedAcyclicGraph<Node, Edge>
	{
		private final SemanticGraph base;
		private static int counter = 0;
		private final Map<String, String> replicatedNodes =  new HashMap<>();

		public SemanticPattern(SemanticGraph base, Node inInitialNode)
		{
			super(Edge.class);
			this.base = base;
			super.addVertex(inInitialNode);
		}

		public SemanticPattern(SemanticPattern other)
		{
			super(Edge.class);
			this.base = other.base;
			other.vertexSet().forEach(super::addVertex);
			other.edgeSet().forEach(e -> {
				try { super.addDagEdge(other.getEdgeSource(e), other.getEdgeTarget(e), e); } // Use the base methods
				catch (CycleFoundException ex) { throw new IllegalArgumentException(ex); }
			});
		}

		/**
		 * Expands pattern by adding an edge in the base graph.
		 * To keep the pattern as a valid multitree, the new edge must go from a node in the pattern to a new node.
		 * If both nodes linked by the edge are part of the pattern, then a new copy of the target node is created
		 * and added to the pattern.
		 */
		public void expand(Edge e)
		{
			if (!base.containsEdge(e))
				throw new RuntimeException("Edge in expansion is not part of base graph");
			if (this.containsEdge(e))
				throw new RuntimeException("Edge in expansion is already part of pattern");

			Node source = base.getEdgeSource(e);
			Node target = base.getEdgeTarget(e);
			if (this.containsVertex(source) && !this.containsVertex(target))
				super.addVertex(target);
			else if (!this.containsVertex(source) && this.containsVertex(target))
				super.addVertex(source);
			else if (this.containsVertex(source) && this.containsVertex(target))
			{
				// If both source and target are part of the pattern, create a new target
				String newId = target.id + "_" + ++counter;
				super.addVertex(new Node<>(newId, target.entity, target.weight, target.isPredicate, target.data));
				this.replicatedNodes.put(newId, target.id); // Keep track of replicated nodes
			}
			else if (!this.containsVertex(source) && !this.containsVertex(target))
				throw new RuntimeException("None of the edges in expansion are in the pattern");

			try	{ super.addDagEdge(source, target, e); } // Use the base methods
			catch (CycleFoundException ex) { throw new IllegalArgumentException(ex); }
		}

		public SemanticGraph getBase() { return this.base; }

		public Set<Node> getRoots()
		{
			return vertexSet().stream()
					.filter(v -> this.degreeOf(v) == 0)
					.collect(Collectors.toSet());
		}

		public List<List<Edge>> getPreOrders()
		{
			return getRoots().stream()
					.map(this::getPreOrder)
					.collect(Collectors.toList());
		}

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
		@Override
		public boolean addDagEdge(Node s, Node t, Edge e) { throw new RuntimeException("Operation not supported"); }
	}

	public SemanticGraph(Class<? extends Edge> edgeClass)
	{
		super(edgeClass);
	}
}
