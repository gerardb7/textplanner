package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.GraphList;

/**
 * Used for testing
 */
public final class Random implements WeightingFunction
{
	@Override
	public void setContents(GraphList contents) { }

	@Override
	public double weight(String item)
	{
		return item.hashCode();
	}
}
