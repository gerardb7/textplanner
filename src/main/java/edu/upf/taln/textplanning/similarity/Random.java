package edu.upf.taln.textplanning.similarity;

/**
 * Used in tests
 */
public class Random implements ItemSimilarity
{
	private final java.util.Random random = new java.util.Random();

	@Override
	public boolean isDefinedFor(String item)
	{
		return true;
	}

	@Override
	public boolean isDefinedFor(String item1, String item2)
	{
		return true;
	}

	@Override
	public double computeSimilarity(String item1, String item2)
	{
		return random.nextDouble();
	}
}
