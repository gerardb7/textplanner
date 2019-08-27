package edu.upf.taln.textplanning.core.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

import static java.util.stream.Collectors.toSet;

public class SemanticGraph extends SimpleDirectedGraph<String, Role> implements Serializable
{
	private final Map<String, Meaning> meanings = new HashMap<>(); // from vertices to meanings
	private final Map<String, Double> weights = new HashMap<>(); // from vertices to weights
	private final Multimap<String, Mention> mentions = HashMultimap.create(); // from vertices to mentions
	private final Multimap<String, String> types = HashMultimap.create(); // from vertices to types, eg. amr concepts
	private final static long serialVersionUID = 1L;

	// Default constructor
	public SemanticGraph()
	{
		super(Role.class);
	}

	/**
	 *  Build a graph G=(V,E) where V=set of vertices, and E is determined by an adjacency_function and a
	 * 	labelling_function.
	 * 	Each vertex is associated with:
	 * 	    - An optional meaning,
	 * 	    - An optional weight
	 * 	    - One or more mentions
	 */
	public SemanticGraph(Set<String> vertices,
						 Map<String, Meaning> meanings,
	                     Map<String, Double> weights,
	                     Map<String, List<Mention>> mentions,
	                     BiPredicate<String, String> adjacency_function,
	                     BinaryOperator<String> labelling_function)
	{
		super(Role.class);
		assert vertices.containsAll(meanings.keySet()) && vertices.containsAll(weights.keySet()) && vertices.containsAll(mentions.keySet());

		this.meanings.putAll(meanings);
		this.weights.putAll(weights);
		mentions.forEach((key, value) -> value.forEach(m -> this.mentions.put(key, m)));

		vertices.forEach(this::addVertex);
		vertices.forEach(v1 ->
				vertices.stream()
						.filter(v2 -> adjacency_function.test(v1, v2))
						.forEach(v2 -> addNewEdge(v1, v2, labelling_function.apply(v1, v2))));
	}

	public Optional<Double> getWeight(String v) { return Optional.ofNullable(weights.get(v)); }
	public void setWeight(String v, double w) { if (containsVertex(v)) weights.put(v, w); }
	public Map<String, Double> getWeights() { return new HashMap<>(weights); }

	public void vertexContraction(String v, Collection<String> C)
	{
		// Move edges incident to C
		C.stream()
				.map(this::incomingEdgesOf)
				.flatMap(Collection::stream)
				.filter(e -> !v.equals(getEdgeSource(e)) && !C.contains(getEdgeSource(e))) // prevents loops
				.forEach(e -> addNewEdge(this.getEdgeSource(e), v, e.getLabel()));
		C.stream()
				.map(this::outgoingEdgesOf)
				.flatMap(Collection::stream)
				.filter(e -> !v.equals(getEdgeTarget(e)) && !C.contains(getEdgeTarget(e))) // prevents loops
				.forEach(e -> addNewEdge(v, this.getEdgeTarget(e), e.getLabel()));

		// Remove meanings of C (v's meaning is kept)
		C.forEach(meanings::remove);

		// Reassign mentions of C to v
		C.stream()
				.map(mentions::get)
				.forEach(m -> mentions.putAll(v, m));
		C.forEach(mentions::removeAll);

		// Reassign types of C to v
		C.stream()
				.map(types::get)
				.forEach(s -> types.putAll(v, s));
		C.forEach(types::removeAll);

		// Remove C from graph
		removeAllVertices(C);
	}

	public void addNewEdge(String source, String target, String role)
	{
		Role new_edge = Role.create(role);
		addEdge(source, target, new_edge);
	}

	public Set<Meaning> getMeanings() { return Set.copyOf(meanings.values()); }

	public Optional<Meaning> getMeaning(String v) { return Optional.ofNullable(meanings.get(v)); }
	public void setMeaning(String v, Meaning m) { meanings.put(v, m); }
	public Set<Mention> getMentions(String v) { return Set.copyOf(mentions.get(v)); }
	public void addMention(String v, Mention m) { mentions.put(v, m); }
	public Set<String> getContexts(String v) { return getMentions(v).stream().map(Mention::getContextId).collect(toSet()); }
	public Set<String> getTypes(String v) { return Set.copyOf(types.get(v)); }
	public void addType(String v, String t) { types.put(v, t); }
}
