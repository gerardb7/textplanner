package edu.upf.taln.textplanning.core.extraction;

import edu.upf.taln.textplanning.core.io.GraphSemantics;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;

import java.util.Set;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

public class RequirementsExplorer extends Explorer
{
	public RequirementsExplorer(GraphSemantics semantics, boolean start_from_verbs, ExpansionPolicy policy)
	{
		super(semantics, start_from_verbs, policy);
	}

	@Override
	protected SemanticSubgraph getExtendedSubgraph(Neighbour n, SemanticSubgraph s)
	{
		final SemanticGraph g = s.getBase();
		final SemanticSubgraph s_extended = new SemanticSubgraph(s);
		s_extended.addVertex(n.node);
		s_extended.addEdge(g.getEdgeSource(n.edge), g.getEdgeTarget(n.edge), n.edge);

		Set<Neighbour> neighbours = getNeighbours(n.node, s);
		Predicate<Neighbour> is_valid = neighbour ->
				semantics.isRequired(neighbour.node, g.getEdgeSource(neighbour.edge), g.getEdgeTarget(neighbour.edge), neighbour.edge.getLabel(), g);
		neighbours.removeIf(not(is_valid));

		do
		{
			neighbours = neighbours.stream()
					.flatMap(neighbour -> getNeighbours(neighbour.node, s).stream())
					.filter(is_valid)
					.peek(neighbour -> {
						s_extended.addVertex(neighbour.node);
						s_extended.addEdge(g.getEdgeSource(neighbour.edge), g.getEdgeTarget(neighbour.edge), neighbour.edge);
					})
					.collect(toSet());
		}
		while (!neighbours.isEmpty());


		return s_extended;
	}
}
