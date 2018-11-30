package edu.upf.taln.textplanning.core.extraction;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.io.GraphSemantics;

import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class RequirementsExplorer extends Explorer
{
	public RequirementsExplorer(GraphSemantics semantics, boolean start_from_verbs, ExpansionPolicy policy)
	{
		super(semantics, start_from_verbs, policy);
	}

	@Override
	protected Set<String> getRequiredVertices(String v, State s, SemanticGraph g)
	{
		Set<String> S = new HashSet<>(); // set of semantically required nodes
		S.add(v); // include v!
		Set<String> nodes = new HashSet<>();
		nodes.add(v);
		do
		{
			nodes = nodes.stream()
					.flatMap(vi -> getNeighboursAndRoles(vi, g).stream()
						.filter(n ->  !S.contains(n.vertex))
						.filter(n -> semantics.isRequired(vi, g.getEdgeSource(n.edge), g.getEdgeTarget(n.edge), n.edge.getLabel(), g))
						.filter(n -> isAllowed(n, s, g))
						.map(n -> n.vertex))
					.peek(S::add)
					.collect(toSet());
		}
		while (!nodes.isEmpty());

		return S;
	}
}
