package edu.upf.taln.textplanning.core.structures;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

public class SemanticGraph extends SimpleDirectedGraph<String, Role> implements Serializable
{
	private final Map<String, Meaning> meanings = new HashMap<>(); // from vertices to meanings
	private final Map<String, Double> weights = new HashMap<>(); // from vertices to weights
	private final Multimap<String, Mention> mentions = HashMultimap.create(); // from vertices to mentions
	private final Multimap<String, String> sources = HashMultimap.create(); // from vertices to sources, e.g. sentences
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
	 * 	Each instance is associated with:
	 * 	    - The weighted meaning it instantiates,
	 * 	    - One or more mentions (text annotations)
	 * 	    - A weight indicating the importance or relevance of each instance
	 */
	public SemanticGraph(Map<String, Meaning> meanings,
	                     Map<String, Double> weights,
	                     Map<String, List<Mention>> mentions,
	                     Map<String, List<String>> sources,
	                     BiPredicate<String, String> adjacency_function,
	                     BinaryOperator<String> labelling_function)
	{
		super(Role.class);

		// all vertices have a meaning, a list of mentions and a list of sources
		assert meanings.keySet().equals(mentions.keySet());
		assert sources.keySet().equals(mentions.keySet());

		this.meanings.putAll(meanings);
		this.weights.putAll(weights);
		mentions.forEach((key, value) -> value.forEach(m -> this.mentions.put(key, m)));
		sources.forEach((v, l) -> l.forEach(s -> this.sources.put(v, s)));


		meanings.keySet().forEach(this::addVertex);
		meanings.keySet().forEach(v1 ->
				meanings.keySet().stream()
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

		// Reassign sources of C to v
		C.stream()
				.map(sources::get)
				.forEach(s -> sources.putAll(v, s));
		C.forEach(sources::removeAll);

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
	public Collection<Mention> getMentions(String v) { return mentions.get(v); }
	public void addMention(String v, Mention m) { mentions.put(v, m); }
	public Collection<String> getSources(String v) { return sources.get(v); }
	public void addSource(String v, String s) { sources.put(v, s); }
	public Collection<String> getTypes(String v) { return types.get(v); }
	public void addType(String v, String t) { types.put(v, t); }
}
