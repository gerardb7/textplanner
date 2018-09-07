package edu.upf.taln.textplanning.pattern;

import com.google.common.collect.Sets;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.util.NeighborCache;

import java.util.*;
import java.util.stream.Collectors;

public class SingleVertexExplorer extends Explorer
{
	private final static Logger log = LogManager.getLogger();

	public SingleVertexExplorer(boolean start_from_verbs, boolean same_sources_only)
	{
		super(start_from_verbs, same_sources_only);
	}

	@Override
	public List<State> getStartStates(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		Set<String> start_vertices = start_from_verbs ? getFiniteVerbalVertices(g) : g.vertexSet();

		return start_vertices.stream()
				.flatMap(v -> g.getSources(v).stream()
						.map(s -> new State(s, Collections.singleton(v))))
				.collect(Collectors.toList());
	}

	@Override
	public List<State> getNextStates(State c, GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		if (c.vertices.isEmpty())
			return new ArrayList<>();

		return c.vertices.stream()
				.map(neighbours::neighborsOf)
				.flatMap(Set::stream)
				.distinct()
				.filter(n -> !same_sources_only || g.getSources(n).contains(c.source)) // exclude neighbours from different sources
				.filter(n -> !c.vertices.contains(n))
				/****/
				.map(Collections::singleton)
				/****/
				.map(n -> Sets.union(n, c.vertices)) // each candidate extends 'vertices'
				.distinct()
				.map(r -> new State(c.source, r))
				.collect(Collectors.toList());
	}
}
