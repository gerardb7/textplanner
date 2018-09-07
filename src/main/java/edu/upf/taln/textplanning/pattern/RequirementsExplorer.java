package edu.upf.taln.textplanning.pattern;

import com.google.common.collect.Sets;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.util.NeighborCache;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RequirementsExplorer extends Explorer
{
	private final Function<GlobalSemanticGraph, Function<String, Predicate<String>>> source_filter;

	public RequirementsExplorer(boolean start_from_verbs, boolean same_sources_only)
	{
		super(start_from_verbs, same_sources_only);

		source_filter =	same_sources_only   ? g -> s -> v -> g.getSources(v).contains(s)
											: g -> s -> v -> true;
	}

	@Override
	public List<State> getStartStates(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		Set<String> start_vertices = start_from_verbs ? getFiniteVerbalVertices(g) : g.vertexSet();

		// Used to filter candidate's vertices by source
		final Function<String, Predicate<String>> graph_filter = source_filter.apply(g);

		return start_vertices.stream()
				.map(v -> g.getSources(v).stream()
						.map(s -> new State(s, Requirements.determine(v, g, graph_filter.apply(s))))
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.distinct()
				.collect(Collectors.toList());
	}

	@Override
	public List<State> getNextStates(State c, GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		// Used to filter candidate's vertices by source
		final Predicate<String> graph_filter = source_filter.apply(g).apply(c.source);

		return c.vertices.stream()
				.map(neighbours::neighborsOf)
				.flatMap(Set::stream)
				.distinct()
				.filter(n -> !same_sources_only || g.getSources(n).contains(c.source)) // exclude neighbours from different sources
				.filter(n -> !c.vertices.contains(n)) // exclude neighbors already in vertices!
				/****/
				.map(n -> Requirements.determine(n, g, graph_filter))
				/****/
				.map(r -> Sets.union(r, c.vertices)) // each candidate extends 'vertices'
				.distinct()
				.map(r -> new State(c.source, r))
				.collect(Collectors.toList());
	}
}
