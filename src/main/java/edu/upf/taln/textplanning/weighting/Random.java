package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;

import java.util.Set;

/**
 * Used for testing
 */
public final class Random implements WeightingFunction
{
	@Override
	public void setContents(Set<SemanticGraph> contents) { }

	@Override
	public double weight(String item)
	{
		return item.hashCode();
	}
}
