package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.structures.Role;
import org.jgrapht.alg.util.NeighborCache;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Explorer
{
	protected final boolean start_from_verbs;
	protected final boolean same_sources_only;

	public Explorer(boolean start_from_verbs, boolean same_sources_only)
	{
		this.start_from_verbs = start_from_verbs;
		this.same_sources_only = same_sources_only;
	}

	abstract List<Set<String>> getInitialCandidates(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours);
	abstract List<Set<String>> getNextCandidates(Set<String> vertices, GlobalSemanticGraph g, NeighborCache<String, Role> neighbours);

	// returns list of finite verbal vertices sorted by weight
	static List<String> getFiniteVerbalVertices(GlobalSemanticGraph g)
	{
		return g.vertexSet().stream()
				.filter(v -> g.getMentions(v).stream()
						.anyMatch(Mention::isFiniteVerb))
				.sorted(Comparator.comparingDouble(g::getWeight).reversed())
				.collect(Collectors.toList());
	}
}
