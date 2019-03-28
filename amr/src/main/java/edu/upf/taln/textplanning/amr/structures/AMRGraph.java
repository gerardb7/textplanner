package edu.upf.taln.textplanning.amr.structures;

import edu.upf.taln.textplanning.core.structures.Role;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.io.Serializable;
import java.util.Collection;

public class AMRGraph extends DirectedAcyclicGraph<String, Role> implements Serializable
{
	private AMRAlignments alignments = null;
	private final String context_id; // Identifies context of text aligned with graph, e.g. sentence, document, etc.
	private String root;
	private final static long serialVersionUID = 1L;

	public AMRGraph(String context_id, String root)
	{
		super(Role.class);
		this.context_id = context_id;
		this.root = root;
		addVertex(root);
	}

	public AMRAlignments getAlignments()
	{
		return alignments;
	}

	public void setAlignments(AMRAlignments alignments)
	{
		this.alignments = alignments;
	}

	public String getContextId() { return context_id; }
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
			if (alignments != null)
				alignments.renameVertex(old_label, new_label); // this must go before removeVertex call
			removeVertex(old_label);

			if (root.equals(old_label))
				root = new_label;
		}
	}

	public void vertexContraction(String v, Collection<String> C)
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
		if (alignments != null)
			C.forEach(c -> alignments.renameVertex(c, v)); // re-assign alignments from C to v
	}

	@Override
	public boolean removeAllVertices(Collection<? extends String> vertices)
	{
		// Update the alignments
		if (alignments != null)
			vertices.forEach(alignments::removeVertex);

		return super.removeAllVertices(vertices);
	}

	@Override
	public boolean removeVertex(String vertex)
	{
		if (alignments != null)
			alignments.removeVertex(vertex);

		return super.removeVertex(vertex);
	}

	private void addNewEdge(String source, String target, String role)
	{
		Role new_edge = Role.create(role);
		addEdge(source, target, new_edge);
	}
}
