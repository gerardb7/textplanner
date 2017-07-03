package edu.upf.taln.textplanning.similarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Linear combination of other weighting functions.
 */
public class Linear implements ItemSimilarity
{
	private final Map<ItemSimilarity, Double> functions;
	public Linear(Map<ItemSimilarity, Double> inFunctions)
	{
		functions = inFunctions;
	}


	public Map<ItemSimilarity, Double> getFunctions()
	{
		Map<ItemSimilarity, Double> l = new HashMap<>();
		l.putAll(functions);
		return l;
	}

	@Override
	public boolean isDefinedFor(String i)
	{
		return false;
	}

	@Override
	public boolean isDefinedFor(String item1, String item2)
	{
		return false;
	}

	@Override
	public double computeSimilarity(String item1, String item2)
	{
		return functions.entrySet().stream()
				.mapToDouble(p -> p.getKey().computeSimilarity(item1, item2) * p.getValue())
				.sum();
	}
}
