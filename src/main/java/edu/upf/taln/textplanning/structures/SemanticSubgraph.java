package edu.upf.taln.textplanning.structures;

import org.jgrapht.graph.AsSubgraph;

import java.util.Set;

/**
 * A rooted induced subgraph of a graph
 */
public class SemanticSubgraph extends AsSubgraph<String, Role>
{
	public SemanticSubgraph(GlobalSemanticGraph base, Set<String> vertexSubset)
	{
		super(base, vertexSubset);
	}

	public GlobalSemanticGraph getBase() { return (GlobalSemanticGraph)base; }
}
