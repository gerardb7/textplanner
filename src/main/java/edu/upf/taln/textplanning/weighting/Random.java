package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.List;

/**
 * Used for testing
 */
public class Random implements WeightingFunction
{
	//private final java.util.Random random = new java.util.Random();
	@Override
	public void setCollection(List<SemanticTree> inCollection) { }

	@Override
	public double weight(Entity inEntity)
	{
		return inEntity.getEntityLabel().hashCode();
	}
}
