package edu.upf.taln.textplanning.core.extraction;

import edu.upf.taln.textplanning.core.structures.GraphSemantics;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;

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

	public SingleVertexExplorer(boolean start_from_verbs, ExpansionConstraints policy)
	{
		super(new NoSemantics(), start_from_verbs, policy);
	}

	@Override
	protected SemanticSubgraph getExtendedSubgraph(Neighbour n, SemanticSubgraph s)
	{
		final SemanticSubgraph s_extended = new SemanticSubgraph(s);
		s_extended.addVertex(n.node);
		s_extended.addEdge(s.getBase().getEdgeSource(n.edge), s.getBase().getEdgeTarget(n.edge), n.edge);
		return s_extended;
	}
}
