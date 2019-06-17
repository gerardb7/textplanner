package edu.upf.taln.textplanning.core.extraction;

import com.google.common.collect.Sets;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.io.GraphSemantics;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.Role;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Explorer
{
	public static class State
	{
		public final String root; // root vertex
		public final String source; // source/sentence of the root (vertices can have multiple sources)
		public final Set<String> vertices = new HashSet<>(); // selected vertices, including the root
		public State(String root, String source, Set<String> vertices)
		{
			this.root = root;
			this.source = source;
			this.vertices.addAll(vertices);
			this.vertices.add(this.root);
		}
	}

	protected static class Neighbour
	{
		public final String vertex;
		public final Role edge;
		public Neighbour(String vertex, Role edge) { this.vertex = vertex; this.edge = edge; }
	}

	public enum ExpansionPolicy
	{Same_source, Non_core_only, All}

	protected final GraphSemantics semantics;
	protected final boolean start_from_verbs;
	protected final ExpansionPolicy policy;

	public Explorer(GraphSemantics semantics, boolean start_from_verbs, ExpansionPolicy policy)
	{
		this.semantics = semantics;
		this.start_from_verbs = start_from_verbs;
		this.policy = policy;
	}

	public List<State> getStartStates(SemanticGraph g)
	{
		final Set<String> start_vertices = new HashSet<>();
		if (start_from_verbs)
		{
			start_vertices.addAll(getVerbalVertices(g));
		}

		// if no start vertices, consider the whole set
		if (start_vertices.isEmpty())
			start_vertices.addAll(g.vertexSet());

		return start_vertices.stream()
				.flatMap(v -> g.getSources(v).stream()
						.map(source -> new State(v, source, Collections.singleton(v))) // Create initial state
						.map(state -> new State(state.root, state.source, getRequiredVertices(v, state, g)))
						)
				.distinct()
				.collect(Collectors.toList());
	}

	public List<State> getNextStates(State s, SemanticGraph g)
	{
		return s.vertices.stream()
				.map(v -> getNeighboursAndRoles(v, g))
				.flatMap(Set::stream)
				.filter(n -> isAllowed(n, s, g))
				.map(n -> getRequiredVertices(n.vertex, s, g))
				.map(r -> Sets.union(r, s.vertices)) // each candidate extends 'vertices'
				.distinct()
				.map(r -> new State(s.root, s.source, r))
				.collect(Collectors.toList());
	}

	protected Set<Neighbour> getNeighboursAndRoles(String v, SemanticGraph g)
	{
		return g.edgesOf(v).stream()
				.map(e ->
				{
					String source_v = g.getEdgeSource(e);
					String target_v = g.getEdgeTarget(e);
					return source_v.equals(v)   ? new Neighbour(target_v, e)
												: new Neighbour(source_v, e);
				})
				.collect(Collectors.toSet());
	}

	protected boolean isAllowed(Neighbour n, State s, SemanticGraph g)
	{
		boolean allow = !s.vertices.contains(n.vertex);
		switch (policy)
		{
			case Same_source:
				return allow && g.getSources(n.vertex).contains(s.source);
			case Non_core_only:
				return allow && (g.getSources(n.vertex).contains(s.source) ||
						// Allow neighbours pointed by non-core relations
						(!semantics.isCore(n.edge.getLabel()) && g.getEdgeTarget(n.edge).equals(n.vertex)));
			case All:
			default:
				return allow;
		}
	}

	protected abstract Set<String> getRequiredVertices(String v, State s, SemanticGraph g);

	// returns list of verbal vertices sorted by weight
	private static Set<String> getVerbalVertices(SemanticGraph g)
	{
		return g.vertexSet().stream()
				.filter(v -> g.getMentions(v).stream()
						.anyMatch(Mention::isVerbal))
				.collect(Collectors.toSet());
	}
}
