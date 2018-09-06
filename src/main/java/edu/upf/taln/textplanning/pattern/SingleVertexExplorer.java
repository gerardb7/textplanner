package edu.upf.taln.textplanning.pattern;

import com.google.common.collect.Sets;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.util.NeighborCache;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SingleVertexExplorer extends Explorer
{
	private final static Logger log = LogManager.getLogger();

	public SingleVertexExplorer(boolean start_from_verbs, boolean same_sources_only)
	{
		super(start_from_verbs, same_sources_only);
	}

	@Override
	public List<Set<String>> getInitialCandidates(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		Collection<String> start_vertices = start_from_verbs ? getFiniteVerbalVertices(g) : g.vertexSet();

		return start_vertices.stream()
				.map(Collections::singleton)
				.collect(Collectors.toList());
	}

	@Override
	public List<Set<String>> getNextCandidates(Set<String> vertices, GlobalSemanticGraph g,
	                                           NeighborCache<String, Role> neighbours)
	{
		// Used to enforce candidates from the same source(s)
		final Collection<String> sources = vertices.stream()
				.map(g::getSources)
				.reduce(Collections.emptySet(), (s1, s2) -> s1.stream()
						.filter(s2::contains)
						.collect(Collectors.toSet()));

		return vertices.stream()
				.map(neighbours::neighborsOf)
				.flatMap(Set::stream)
				.filter(n -> !same_sources_only || g.getSources(n).stream().anyMatch(sources::contains))
				.map(Collections::singleton)
				.map(n -> Sets.union(n, vertices)) // each candidate extends 'vertices'
				.distinct()
				.collect(Collectors.toList());
	}
}
