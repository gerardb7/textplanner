package edu.upf.taln.textplanning.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

public class GlobalSemanticGraph extends SimpleDirectedGraph<String, Role> implements Serializable
{
	private final Map<String, Meaning> meanings = new HashMap<>();
	private final Multimap<String, Mention> mentions = HashMultimap.create();
	private final Map<String, Double> weights = new HashMap<>();
	private final static long serialVersionUID = 1L;

	// Build from AMR-like graphs
	public GlobalSemanticGraph(GraphList graphs)
	{
		super(Role.class);

		graphs.getGraphs().forEach(g -> g.edgeSet().forEach(e ->
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

	/*  Build a graph G=(V,E) where V=set of instances, and E is determined by an adjacency_function and a
		labelling_function.
		Each instance is associated with:
	        - The meaning it isntantiates
	        - One or more mentions (text annotations)
	        - A weight indicating the importance or relevance of each instance
	 */
	public GlobalSemanticGraph(Set<String> instances, Map<String, Meaning> meanings, Multimap<String, Mention> mentions,
	                           Map<String, Double> weights, BiPredicate<String, String> adjacency_function,
	                           BinaryOperator<String> labelling_function)
	{
		super(Role.class);

		instances.forEach(this::addVertex);
		this.meanings.putAll(meanings);
		this.mentions.putAll(mentions);
		this.weights.putAll(weights);
		instances.forEach(v1 ->
				instances.stream()
						.filter(v2 -> adjacency_function.test(v1, v2))
						.forEach(v2 -> this.addEdge(v1, v2, Role.create(labelling_function.apply(v1, v2)))));
	}

	public double getWeight(String v) { return weights.getOrDefault(v,0.0); }
	public void setWeight(String v, double w) { if (containsVertex(v)) weights.put(v, w); }
	public Map<String, Double> getWeights() { return new HashMap<>(weights); }

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
