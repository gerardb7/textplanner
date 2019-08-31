package edu.upf.taln.textplanning.core.extraction;

import edu.upf.taln.textplanning.core.structures.GraphSemantics;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.Role;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public abstract class Explorer
{
	protected static class Neighbour
	{
		public final String node;
		public final Role edge;
		public Neighbour(String node, Role edge)
		{
			this.node = node;
			this.edge = edge;
		}
	}

	public enum ExpansionConstraints
	{Same_source, Non_core_only, All}

	protected final GraphSemantics semantics;
	protected final boolean start_from_verbs;
	protected final ExpansionConstraints constraints;

	public Explorer(GraphSemantics semantics, boolean start_from_verbs, ExpansionConstraints constraints)
	{
		this.semantics = semantics;
		this.start_from_verbs = start_from_verbs;
		this.constraints = constraints;
	}

	public List<SemanticSubgraph> getStartStates(SemanticGraph g)
	{
		final Set<String> start_vertices = new HashSet<>();
		if (start_from_verbs)
			start_vertices.addAll(getVerbalVertices(g));

		// if no start vertices, consider the whole set
		if (start_vertices.isEmpty())
			start_vertices.addAll(g.vertexSet());

		return start_vertices.stream()
				.flatMap(v -> g.getContexts(v).stream()
						.map(source -> new SemanticSubgraph(g, v, Set.of(v), Set.of(), 0)))
				.distinct()
				.collect(Collectors.toList());
	}

	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor){
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}

	public List<SemanticSubgraph> getNextStates(SemanticSubgraph s)
	{
		return s.vertexSet().stream()
				.map(v -> getNeighbours(v, s))
				.flatMap(Set::stream)
				.filter(n -> isAllowed(n, s))
				.map(n -> getExtendedSubgraph(n, s))
				.filter(distinctByKey(SemanticSubgraph::vertexSet)) // See SemanticSubgraph and jGraphT AbstractGraph implementations of equals
				.collect(Collectors.toList());
	}

	// Given a subgraph s o a graph g, returns neighbours of a vertex v of s.
	// A neighbour n must satisfy the following:
	//  1- be connected to v via an edge e in g but not in s
	//  2- not be part of s
	protected Set<Neighbour> getNeighbours(String v, SemanticSubgraph s)
	{
		final SemanticGraph g = s.getBase();
		return g.edgesOf(v).stream()
				.filter(not(s::containsEdge)) // avoid visited edges
				.map(e ->
				{
					String source_v = g.getEdgeSource(e);
					String target_v = g.getEdgeTarget(e);
					return source_v.equals(v)   ? new Neighbour(target_v, e)
												: new Neighbour(source_v, e);
				})
				.filter(n -> !s.containsVertex(n.node)) // avoid loops and cycles
				.collect(Collectors.toSet());
	}

	// What neighbours are allowed for expanding a subgraph?
	protected boolean isAllowed(Neighbour n, SemanticSubgraph s)
	{
		final SemanticGraph g = s.getBase();
		final Collection<String> root_sources = g.getContexts(s.getRoot());
		final Set<String> n_sources = g.getContexts(n.node);
		final boolean same_source = n_sources.stream().anyMatch(root_sources::contains);

		switch (constraints)
		{
			case Same_source:
				return same_source;
			case Non_core_only:
				return same_source ||
						// Allow neighbours pointed by non-core relations
						(!semantics.isCore(n.edge.getLabel()) && g.getEdgeTarget(n.edge).equals(n.node));
			case All:
			default:
				return true;
		}
	}

	protected abstract SemanticSubgraph getExtendedSubgraph(Neighbour n, SemanticSubgraph s);

	// returns list of verbal vertices sorted by weight
	private static Set<String> getVerbalVertices(SemanticGraph g)
	{
		return g.vertexSet().stream()
				.filter(v -> g.getMentions(v).stream()
						.anyMatch(Mention::isVerbal))
				.collect(Collectors.toSet());
	}
}
