package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.Entity;

/**
 * Used in tests
 */
public class Random implements EntitySimilarity
{
	private final java.util.Random random = new java.util.Random();

	@Override
	public boolean isDefinedFor(Entity inItem)
	{
		return true;
	}

	@Override
	public boolean isDefinedFor(Entity inItem1, Entity inItem2)
	{
		return true;
	}

	@Override
	public double computeSimilarity(Entity inItem1, Entity inItem2)
	{
		return random.nextDouble();
	}
}
