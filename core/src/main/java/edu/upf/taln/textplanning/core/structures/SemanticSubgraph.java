package edu.upf.taln.textplanning.core.structures;

import org.jgrapht.graph.AsSubgraph;

import java.util.Set;

/**
 * A subgraph of a semantic graph
 */
public class SemanticSubgraph extends AsSubgraph<String, Role>
{
	private final String root;
	private final double value; // Value assigned to this graph by the function optimized by the extraction procedure

	public SemanticSubgraph(GlobalSemanticGraph base, String root, Set<String> vertexSubset, double value)
	{
		super(base, vertexSubset);
		this.root = root;
		this.value = value;
	}

	public GlobalSemanticGraph getBase() { return (GlobalSemanticGraph)base; }
	public String getRoot() { return root; }
	public double getValue() { return value; }

	// Ignores vertices with weight set to 0 (no weight)
	public double getAverageWeight()
	{
		return vertexSet().stream()
				.mapToDouble(v -> getBase().getWeight(v))
				.filter(d -> d != 0.0)
				.average().orElse(0.0);
	}
}
