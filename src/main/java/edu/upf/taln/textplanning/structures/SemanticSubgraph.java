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

	public SemanticSubgraph(GlobalSemanticGraph base, Set<String> center, Set<String> vertexSubset)
	{
		super(base, vertexSubset);
		this.center.addAll(center);
	}

	public GlobalSemanticGraph getBase() { return (GlobalSemanticGraph)base; }
	public Set<String> getCenter() { return center; }

	// Ignores vertices with weight set to 0 (no weight)
	public double getAverageWeight()
	{
		return vertexSet().stream()
				.mapToDouble(v -> getBase().getWeight(v))
				.filter(d -> d != 0.0)
				.average().orElse(0.0);
	}
}
