package edu.upf.taln.textplanning.core.extraction;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.io.GraphSemantics;

import java.util.Collections;
import java.util.Set;

public class SingleVertexExplorer extends Explorer
{
	private static class NoSemantics implements GraphSemantics
	{
		@Override
		public boolean isCore(String role)
		{
			return false;
		}

		@Override
		public boolean isRequired(String v, String source, String target, String role, SemanticGraph g)
		{
			return false;
		}
	}

	public SingleVertexExplorer(boolean start_from_verbs, ExpansionPolicy policy)
	{
		super(new NoSemantics(), start_from_verbs, policy);
	}

	@Override
	protected Set<String> getRequiredVertices(String v, State s, SemanticGraph g)
	{
		return Collections.singleton(v);
	}
}
