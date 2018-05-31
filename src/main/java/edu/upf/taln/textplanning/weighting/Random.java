package edu.upf.taln.textplanning.weighting;

import java.util.Collection;

/**
 * Used for testing
 */
public final class Random implements WeightingFunction
{
	@Override
	public void setContents(Collection<String> contents) { }

	@Override
	public double weight(String item)
	{
		return item.hashCode();
	}
}
