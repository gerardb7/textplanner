package edu.upf.taln.textplanning.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GlobalSemanticGraph extends SimpleDirectedGraph<String, Role>
{
	private final GraphList graphs;
	private final Map<String, Meaning> meanings = new HashMap<>();
	private final Multimap<String, Mention> mentions = HashMultimap.create();
	private final Map<String, Double> weights = new HashMap<>();
	private final static long serialVersionUID = 1L;

	public GlobalSemanticGraph(GraphList graphs)
	{
		super(Role.class);
		this.graphs = graphs;

		graphs.forEach(g -> g.edgeSet().forEach(e ->
		{
			String v1 = g.getEdgeSource(e);
			addVertex(v1);
			String v2 = g.getEdgeTarget(e);
			addVertex(v2);
			addEdge(v1, v2, e);
		}));

		graphs.getCandidates().forEach(c ->
		{
			meanings.put(c.getVertex(), c.getMeaning());
			mentions.put(c.getVertex(), c.getMention());
		});
	}

	public double getWeight(String v) { return weights.getOrDefault(v,0.0); }
	public void setWeight(String v, double w) { if (containsVertex(v)) weights.put(v, w); }
	public Map<String, Double> getWeights() { return new HashMap<>(weights); }
	public GraphList getGraphs()
	{
		return graphs;
	}

	public void vertexContraction(String v, Collection<String> C)
	{
		// Move edges incident to C
		C.stream()
				.map(this::incomingEdgesOf)
				.flatMap(Collection::stream)
				.forEach(e -> this.addEdge(this.getEdgeSource(e), v, e));
		C.stream()
				.map(this::outgoingEdgesOf)
				.flatMap(Collection::stream)
				.forEach(e -> this.addEdge(v, this.getEdgeTarget(e), e));

		// Remove meanings of C (v's meaning is kept)
		C.forEach(meanings::remove);

		// Reassign mentions of C to v
		C.stream()
				.map(mentions::get)
				.flatMap(Collection::stream)
				.forEach(m -> mentions.put(v, m));
		C.forEach(mentions::removeAll);

		// Remove C from graph
		removeAllVertices(C);
	}

	public Meaning getMeaning(String v) { return meanings.get(v); }
	public Collection<Mention> getMentions(String v) { return mentions.get(v); }
}
