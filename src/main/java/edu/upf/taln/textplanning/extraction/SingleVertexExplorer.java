package edu.upf.taln.textplanning.extraction;

import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;

import java.util.Collections;
import java.util.Set;

public class SingleVertexExplorer extends Explorer
{
	public SingleVertexExplorer(boolean start_from_verbs, ExpansionPolicy policy)
	{
		super(start_from_verbs, policy);
	}

	@Override
	protected Set<String> getRequiredVertices(String v, State s, GlobalSemanticGraph g)
	{
		return Collections.singleton(v);
	}
}
