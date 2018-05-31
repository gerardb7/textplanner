package edu.upf.taln.textplanning.input.amr;

import edu.upf.taln.textplanning.input.GraphAlignments;
import edu.upf.taln.textplanning.structures.Role;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.io.Serializable;
import java.util.Collection;

public class SemanticGraph extends DirectedAcyclicGraph<String, Role> implements Serializable
{
	private GraphAlignments alignments = null;
	private final String id;
	private String root;
	private final static long serialVersionUID = 1L;

	public SemanticGraph(String id, String root)
	{
		super(Role.class);
		this.id = id;
		this.root = root;
		addVertex(root);
	}

	public GraphAlignments getAlignments()
	{
		return alignments;
	}

	public void setAlignments(GraphAlignments alignments)
	{
		this.alignments = alignments;
	}

	public String getId() { return id; }
	public String getRoot() { return root; }

	public int getDepth(String v)
	{
		return DijkstraShortestPath.findPathBetween(this, root, v).getLength();
	}

	public void renameVertex(String old_label, String new_label)
	{
		if (this.containsVertex(old_label))
		{
			addVertex(new_label);
			incomingEdgesOf(old_label).forEach(e ->	addNewEdge(getEdgeSource(e), new_label, e.getLabel()));
			outgoingEdgesOf(old_label).forEach(e ->	addNewEdge(new_label, getEdgeTarget(e), e.getLabel()));
			removeVertex(old_label);
			alignments.renameVertex(old_label, new_label);

			if (root.equals(old_label))
				root = new_label;
		}
	}

	void vertexContraction(String v, Collection<String> C)
	{
		C.stream()
				.map(this::incomingEdgesOf)
				.flatMap(Collection::stream)
				.filter(e -> !v.equals(getEdgeSource(e)) && !C.contains(getEdgeSource(e)))
				.forEach(e -> addNewEdge(getEdgeSource(e), v, e.getLabel()));
		C.stream()
				.map(this::outgoingEdgesOf)
				.flatMap(Collection::stream)
				.filter(e -> !v.equals(getEdgeTarget(e)) && !C.contains(getEdgeTarget(e)))
				.forEach(e -> addNewEdge(v, getEdgeTarget(e), e.getLabel()));

		removeAllVertices(C);
		C.forEach(c -> alignments.removeVertex(c));
	}

	private void addNewEdge(String source, String target, String role)
	{
		Role new_edge = Role.create(role);
		addEdge(source, target, new_edge);
	}
}
