package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Collection;

/**
 * Used for testing
 */
public final class Random implements WeightingFunction
{
	@Override
	public void setContents(Collection<LinguisticStructure> contents) { }

	@Override
	public double weight(String item)
	{
		return item.hashCode();
	}
}
