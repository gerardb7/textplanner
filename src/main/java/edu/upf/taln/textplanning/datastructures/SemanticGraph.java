package edu.upf.taln.textplanning.datastructures;

import org.apache.commons.collections4.SetUtils;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;

/**
 * A semantic graph is a rooted directed simple graph with weighted nodes and labels on both edges and nodes.
 */
public final class SemanticGraph extends DirectedAcyclicGraph<SemanticGraph.Node, SemanticGraph.Edge>
{
	public final static class Node
	{
		private final String id; // unique id, determines if two nodes are the same!
		private Entity entity; // mutable!
		private final Map<Entity, Mention> candidates = new HashMap<>(); // candidate entities
		private final Map<Mention, Set<Entity>> mentions = new HashMap<>(); // candidate entities
		private final Annotation ann;

		private final String coref; // id of node with which this node corefers, used in semantic trees

		public Node(String id, Entity e, Annotation a)
		{
			this(id, e, a, null);
		}

		public Node(String id, Entity e, Annotation a, String corefId)
		{
			this.id = id;
			this.entity = e;
			this.ann = a;
			this.coref = corefId;
		}

		public String getId()
		{
			return id;
		}
		public Entity getEntity()	{ return entity; }
		public void setEntity(Entity e) { this.entity = e; }
		public Set<Entity> getCandidates() { return candidates.keySet(); }
		public Set<Entity> getCandidates(Mention m) { return mentions.containsKey(m) ? mentions.get(m) : Collections.emptySet(); }
		public Set<Mention> getMentions() { return mentions.keySet(); }
		public Optional<Mention> getMention(Entity e) { return Optional.ofNullable(candidates.get(e)); }
		public void addCandidate(Entity e, Mention m) { candidates.put(e, m); mentions.merge(m, Collections.singleton(e), SetUtils::union); }
		public Annotation getAnnotation() { return ann; }
		public Optional<String> getCoref() { return Optional.ofNullable(this.coref); }

		public boolean mentions(String e)
		{
			return getEntity().getLabel().equals(e) || candidates.keySet().stream().map(Entity::getLabel).anyMatch(c -> c.equals(e));
		}

		public boolean corefers(Node n)
		{
			return  (coref != null && coref.equals(n.getId())) ||
					(n.coref != null && n.coref.equals(id));
		}

		@Override
		public String toString() { return this.entity.getLabel(); }

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

	// Immutable class
	public final static class Edge extends DefaultEdge
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
