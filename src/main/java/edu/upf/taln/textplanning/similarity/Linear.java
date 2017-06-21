package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Linear combination of other weighting functions.
 */
public class Linear implements EntitySimilarity
{
	private final Map<EntitySimilarity, Double> functions;
	public Linear(Map<EntitySimilarity, Double> inFunctions)
	{
		functions = inFunctions;
	}


	public Map<EntitySimilarity, Double> getFunctions()
	{
		Map<EntitySimilarity, Double> l = new HashMap<>();
		l.putAll(functions);
		return l;
	}

	@Override
	public boolean isDefinedFor(Entity e)
	{
		return false;
	}

	@Override
	public boolean isDefinedFor(Entity e1, Entity e2)
	{
		return false;
	}

	@Override
	public double computeSimilarity(Entity e1, Entity e2)
	{
		return functions.entrySet().stream()
				.mapToDouble(p -> p.getKey().computeSimilarity(e1, e2) * p.getValue())
				.sum();
	}
}
