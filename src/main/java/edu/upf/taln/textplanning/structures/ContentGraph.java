package edu.upf.taln.textplanning.structures;

import org.apache.commons.collections4.ListUtils;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;

import java.util.*;

/**
 * A content graph is a directed acyclic graph with entities as nodes and edges indicating roles.
 * Optionally, a content graph may list anchors for its entities, i.e. mentions of the entities in a set of texts.
 * Anchors can be used, for instance, to support linguistic generation.
 */
public class ContentGraph  extends DirectedAcyclicGraph<Entity, Role>
{
	private final Map<Entity, List<Mention>> anchors = new HashMap<>(); // maps nodes to all their mentions in the input structures

	public ContentGraph(Class<? extends Role> edgeClass)
	{
		super(edgeClass);
	}

	public void addAnchor(Entity e, Mention m) { anchors.merge(e, Collections.singletonList(m), ListUtils::union); }
	public List<Mention> getAnchors(Entity e) { return new ArrayList<>(anchors.get(e)); }
}
