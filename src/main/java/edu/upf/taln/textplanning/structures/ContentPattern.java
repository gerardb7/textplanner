package edu.upf.taln.textplanning.structures;

import org.jgrapht.graph.DirectedSubgraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.*;

import static java.util.stream.Collectors.joining;

/**
 * Patterns extracted from a content graph are rooted subgraphs of the main content graph.
 *
 * Content patterns are non-induced subgraphs, edges must be added manually
 */
public class ContentPattern extends DirectedSubgraph<Entity, Role>
{
	private Entity root; // not final

	public ContentPattern(ContentGraph g, Entity root)
	{
		super(g, Collections.singleton(root), new HashSet<>()); // creates non-induced subgraph
		this.root = root;
	}

	public ContentPattern(ContentPattern other)
	{
		super(other.getBase(), other.vertexSet(), other.edgeSet()); // creates non-induced subgraph
		this.root = other.root;
	}

	public Entity getRoot() { return root; }


	/**
	 * @return list of vertices in the pattern following a fixed topological order
	 */
	public List<Entity> getTopologicalOrder()
	{
		// Create a total order over the vertex set to guarantee that the returned iterator always iterates over the same sequence
		PriorityQueue<Entity> queue = new PriorityQueue<>(vertexSet().size(), Comparator.comparing(Entity::getId));
		TopologicalOrderIterator<Entity, Role> it = new TopologicalOrderIterator<>(this, queue);
		List<Entity> es = new ArrayList<>();
		while (it.hasNext())
		{
			es.add(it.next());
		}
		return es;
	}

	public String toString()
	{
		return root + (outDegreeOf(root) == 0 ? " // " : "") +
				getTopologicalOrder().stream()
				.map(v -> v.getId() + outgoingEdgesOf(v).stream()
						.map(this::getEdgeTarget)
						.map(Entity::getId)
						.collect(joining("(", ",", ")")))
				.collect(joining(" "));
	}
}
