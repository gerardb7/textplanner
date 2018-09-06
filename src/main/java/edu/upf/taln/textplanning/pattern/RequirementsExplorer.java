package edu.upf.taln.textplanning.pattern;

import com.google.common.collect.Sets;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.util.NeighborCache;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RequirementsExplorer extends Explorer
{
	private final Function<GlobalSemanticGraph, Function<String, Predicate<String>>> source_filter;
	private final static Logger log = LogManager.getLogger();

	public RequirementsExplorer(boolean start_from_verbs, boolean same_sources_only)
	{
		super(start_from_verbs, same_sources_only);

		source_filter =	same_sources_only   ? g -> s -> v -> g.getSources(v).contains(s)
											: g -> s -> v -> true;
	}

	@Override
	public List<Set<String>> getInitialCandidates(GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		Collection<String> start_vertices = start_from_verbs ? getFiniteVerbalVertices(g) : g.vertexSet();
		log.info(start_vertices.size() + " vertices used as starting points");

		// Used to filter candidate's vertices by source
		final Function<String, Predicate<String>> graph_filter = source_filter.apply(g);

		return start_vertices.stream()
				.map(v -> g.getSources(v).stream()
						.map(s -> Requirements.determine(v, g, graph_filter.apply(s)))
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.distinct()
				.collect(Collectors.toList());
	}

	@Override
	public List<Set<String>> getNextCandidates(Set<String> vertices, GlobalSemanticGraph g, NeighborCache<String, Role> neighbours)
	{
		// Used to filter candidate's vertices by source
		final Function<String, Predicate<String>> graph_filter = source_filter.apply(g);

		return vertices.stream()
				.map(neighbours::neighborsOf)
				.flatMap(Set::stream)
				//.filter(same_sentence) // must be in the same sentence as v_start
				.filter(n -> !vertices.contains(n)) // exclude neighbors already in vertices!
				.distinct()
				// map each neighbour to a set that includes its required nodes in the same sentence
				.map(n -> g.getSources(n).stream()
						.map(s -> Requirements.determine(n, g, graph_filter.apply(s)))
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.map(C -> Sets.union(C, vertices)) // each candidate extends 'vertices'
				.distinct()
				.collect(Collectors.toList());
	}
}
