package edu.upf.taln.textplanning.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

public class GlobalSemanticGraph extends SimpleDirectedGraph<String, Role> implements Serializable
{
	private final Map<String, Meaning> meanings = new HashMap<>();
	private final Multimap<String, Mention> mentions = HashMultimap.create();
	private final Map<String, Double> weights = new HashMap<>();
	private final static long serialVersionUID = 1L;

	// Default constructor
	public GlobalSemanticGraph()
	{
		super(Role.class);
	}

	/**
	 *  Build a graph G=(V,E) where V=set of vertices, and E is determined by an adjacency_function and a
	 * 	labelling_function.
	 * 	Each instance is associated with:
	 * 	    - The weighted meaning it instantiates,
	 * 	    - One or more mentions (text annotations)
	 * 	    - A weight indicating the importance or relevance of each instance
	 */
	public GlobalSemanticGraph(Set<String> vertices,
	                           Map<String, Meaning> ranked_meanings,
	                           Multimap<String, Mention> mentions,
	                           BiPredicate<String, String> adjacency_function,
	                           BinaryOperator<String> labelling_function)
	{
		super(Role.class);

		vertices.forEach(this::addVertex);
		this.meanings.putAll(ranked_meanings);
		this.mentions.putAll(mentions);
		vertices.forEach(v1 ->
				vertices.stream()
						.filter(v2 -> adjacency_function.test(v1, v2))
						.forEach(v2 -> addNewEdge(v1, v2, labelling_function.apply(v1, v2))));
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
				.filter(e -> !v.equals(getEdgeSource(e)) && !C.contains(getEdgeSource(e)))
				.forEach(e -> addNewEdge(this.getEdgeSource(e), v, e.getLabel()));
		C.stream()
				.map(this::outgoingEdgesOf)
				.flatMap(Collection::stream)
				.filter(e -> !v.equals(getEdgeTarget(e)) && !C.contains(getEdgeTarget(e)))
				.forEach(e -> addNewEdge(v, this.getEdgeTarget(e), e.getLabel()));

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

	public void addNewEdge(String source, String target, String role)
	{
		Role new_edge = Role.create(role);
		addEdge(source, target, new_edge);
	}

	public Optional<Meaning> getMeaning(String v) { return Optional.ofNullable(meanings.get(v)); }
	public void setMeaning(String v, Meaning m) { meanings.put(v, m); }
	public Collection<Mention> getMentions(String v) { return mentions.get(v); }
	public void addMention(String v, Mention m) { mentions.put(v, m); }
}
