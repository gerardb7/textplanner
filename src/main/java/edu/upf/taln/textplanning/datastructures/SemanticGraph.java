package edu.upf.taln.textplanning.datastructures;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.Optional;

/**
 * A semantic graph is a rooted directed simple graph with weighted nodes and labels on both edges and nodes.
 */
public class SemanticGraph extends SimpleDirectedGraph<SemanticGraph.Node, SemanticGraph.Edge>
{
	public static class Node
	{
		private final String id; // unique id, determines if two nodes are the same!
		private final Entity entity;
		private double weight;
		private final String coref; // id of node with which this node corefers, used in semantic trees

		public Node(String id, Entity e)
		{
			this(id, e, 0.0);
		}

		public Node(String id, Entity e, double w)
		{
			this.id = id;
			this.entity = e;
			this.weight = w;
			this.coref = null;
		}

		public Node(String id, Entity e, double w, String corefId)
		{
			this.id = id;
			this.entity = e;
			this.weight = w;
			this.coref = corefId;
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

		public void setWeight(double w)
		{
			weight = w;
		}

		public Optional<String> getCoref() { return Optional.ofNullable(this.coref); }

		public boolean corefers(Node n)
		{
			return  (coref != null && coref.equals(n.getId())) ||
					(n.coref != null && n.coref.equals(id));
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
		private final String role;
		private final boolean isArg;

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

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (o == null || getClass() != o.getClass())
			{
				return false;
			}

			Edge edge = (Edge) o;
			if (isArg != edge.isArg || !role.equals(edge.role))
				return false;

			//noinspection SimplifiableIfStatement
			if ((getSource() == null && edge.getSource() != null) || (getTarget() == null && edge.getTarget() != null))
				return false;
			return getSource().equals(edge.getSource()) && getTarget().equals(edge.getTarget());
		}

		@Override
		public int hashCode()
		{
			int result = role.hashCode();
			result = 31 * result + (isArg ? 1 : 0);
			result = 31 * result + (getSource() != null ? getSource().hashCode() : 0);
			result = 31 * result + (getTarget() != null ? getTarget().hashCode() : 0);
			return result;
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
