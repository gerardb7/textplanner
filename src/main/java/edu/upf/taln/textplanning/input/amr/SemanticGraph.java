package edu.upf.taln.textplanning.input.amr;

import edu.upf.taln.textplanning.input.GraphAlignments;
import edu.upf.taln.textplanning.structures.Role;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.io.Serializable;
import java.util.Collection;

public class SemanticGraph extends DirectedAcyclicGraph<String, Role> implements Serializable
{
	private GraphAlignments alignments = null;
	private final String id;
	private final String root;
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

	public void renameVertex(String old_label, String new_label)
	{
		if (this.containsVertex(old_label))
		{
			addVertex(new_label);
			incomingEdgesOf(old_label).forEach(e -> addEdge(getEdgeSource(e), new_label, e));
			outgoingEdgesOf(old_label).forEach(e -> addEdge(new_label, getEdgeTarget(e), e));
			removeVertex(old_label);
			alignments.renameVertex(old_label, new_label);
		}
	}

	void vertexContraction(String v, Collection<String> C)
	{
		C.stream()
				.map(this::incomingEdgesOf)
				.flatMap(Collection::stream)
				.forEach(e -> this.addEdge(this.getEdgeSource(e), v, e));
		C.stream()
				.map(this::outgoingEdgesOf)
				.flatMap(Collection::stream)
				.forEach(e -> this.addEdge(v, this.getEdgeTarget(e), e));

		removeAllVertices(C);
		C.forEach(c -> alignments.removeVertex(c));
	}
}
