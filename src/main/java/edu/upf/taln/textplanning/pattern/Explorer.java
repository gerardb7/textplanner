package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.Role;
import org.jgrapht.alg.util.NeighborCache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Explorer
{
	public static class State
	{
		public final String source;
		public final Set<String> vertices = new HashSet<>();
		public State(String source, Set<String> vertices) { this.source = source; this.vertices.addAll(vertices); }
	}

	protected final boolean start_from_verbs;
	protected final boolean same_sources_only;

	public Explorer(boolean start_from_verbs, boolean same_sources_only)
	{
		this.start_from_verbs = start_from_verbs;
		this.same_sources_only = same_sources_only;
	}

	abstract List<State> getStartStates(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours);
	abstract List<State> getNextStates(State c, GlobalSemanticGraph g, NeighborCache<String, Role> neighbours);

	// returns list of finite verbal vertices sorted by weight
	static Set<String> getFiniteVerbalVertices(GlobalSemanticGraph g)
	{
		return g.vertexSet().stream()
				.filter(v -> g.getMentions(v).stream()
						.anyMatch(Mention::isFiniteVerb))
				.collect(Collectors.toSet());
	}
}
