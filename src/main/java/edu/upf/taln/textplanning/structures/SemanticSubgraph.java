package edu.upf.taln.textplanning.structures;

import org.jgrapht.graph.AsSubgraph;

import java.util.HashSet;
import java.util.Set;

/**
 * A rooted induced subgraph of a graph
 */
public class SemanticSubgraph extends AsSubgraph<String, Role>
{
	private final Set<String> center = new HashSet<>();
	private final double value;

	public SemanticSubgraph(GlobalSemanticGraph base, Set<String> center, Set<String> vertexSubset, double value)
	{
		super(base, vertexSubset);
		this.center.addAll(center);
		this.value = value;
	}

	public GlobalSemanticGraph getBase() { return (GlobalSemanticGraph)base; }
	public Set<String> getCenter() { return center; }
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
