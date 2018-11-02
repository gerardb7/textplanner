package edu.upf.taln.amrplanning.structures;

import edu.upf.taln.amrplanning.input.GraphAlignments;
import edu.upf.taln.textplanning.structures.Role;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.io.Serializable;
import java.util.Collection;

public class SemanticGraph extends DirectedAcyclicGraph<String, Role> implements Serializable
{
	private GraphAlignments alignments = null;
	private final String source; // source can be a sentence or document id, or a combination of both
	private String root;
	private final static long serialVersionUID = 1L;

	public SemanticGraph(String source, String root)
	{
		super(Role.class);
		this.source = source;
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

	public String getSource() { return source; }
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
