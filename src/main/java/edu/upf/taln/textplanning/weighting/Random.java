package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Set;

/**
 * Used for testing
 */
public final class Random implements WeightingFunction
{
	@Override
	public void setContents(Set<LinguisticStructure> contents) { }

	@Override
	public double weight(String item)
	{
		return item.hashCode();
	}
}
