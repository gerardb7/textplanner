package edu.upf.taln.textplanning.core.extraction;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.io.GraphSemantics;

import java.util.Collections;
import java.util.Set;

public class SingleVertexExplorer extends Explorer
{
	public SingleVertexExplorer(GraphSemantics semantics, boolean start_from_verbs, ExpansionPolicy policy)
	{
		super(semantics, start_from_verbs, policy);
	}

	@Override
	protected Set<String> getRequiredVertices(String v, State s, SemanticGraph g)
	{
		return Collections.singleton(v);
	}
}
