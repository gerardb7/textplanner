package edu.upf.taln.textplanning.core.structures;

import org.jgrapht.graph.AsSubgraph;

import java.util.Optional;
import java.util.Set;

/**
 * A subgraph of a semantic graph
 */
public class SemanticSubgraph extends AsSubgraph<String, Role>
{
	private final String root;
	private double value; // Value assigned to this graph by the function optimized by the extraction procedure

	public SemanticSubgraph(SemanticGraph base, String root, Set<String> vertexSubset, double value)
	{
		super(base, vertexSubset); // this creates an induced subgraph
		this.root = root;
		this.value = value;
	}

	public SemanticSubgraph(SemanticGraph base, String root, Set<String> vertexSubset, Set<Role> edgeSubset, double value)
	{
		super(base, vertexSubset, edgeSubset); // this creates an induced subgraph
		this.root = root;
		this.value = value;
	}

	public SemanticSubgraph(SemanticSubgraph s)
	{
		this(s.getBase(), s.root, s.vertexSet, s.edgeSet, s.value);
	}

	public SemanticGraph getBase() { return (SemanticGraph)base; }
	public String getRoot() { return root; }
	public double getValue() { return value; }
	public void setValue(double value)
	{
		this.value = value;
	}

	// Ignores vertices with weight set to 0 (no weight)
	public double getAverageWeight()
	{
		return vertexSet().stream()
				.map(v -> getBase().getWeight(v))
				.flatMap(Optional::stream)
				.mapToDouble(d -> d)
				.average().orElse(0.0);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		SemanticSubgraph that = (SemanticSubgraph) o;

		return root.equals(that.root) && super.equals(that);
	}

	@Override
	public int hashCode()
	{
		int result = super.hashCode();
		result = 31 * result + root.hashCode();
		result = 31 * result + super.hashCode();
		return result;
	}
}
