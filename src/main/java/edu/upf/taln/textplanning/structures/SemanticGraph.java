package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.input.GraphAlignments;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.Collection;

public class SemanticGraph extends DirectedAcyclicGraph<String, Role>
{
	private GraphAlignments alignments = null;
	private final String id;
	private final static long serialVersionUID = 1L;

	public SemanticGraph(String id)
	{
		super(Role.class);
		this.id = id;
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


	public void renameVertex(String old_label, String new_label)
	{
		if (this.containsVertex(old_label))
		{
			addVertex(new_label);
			incomingEdgesOf(old_label).forEach(e -> addEdge(getEdgeSource(e), new_label, e));
			outgoingEdgesOf(old_label).forEach(e -> addEdge(new_label, getEdgeTarget(e), e));
			removeVertex(old_label);
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
	}
}
