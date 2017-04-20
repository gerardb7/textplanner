package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.Entity;

import java.util.List;

/**
 * Combines a list of similarity functions, so that similarity is calculated by first function for which a pair of
 * entities is defined.
 */
public class Combined implements EntitySimilarity
{
	public final List<EntitySimilarity> functions;
	public Combined(List<EntitySimilarity> inFunctions)
	{
		functions = inFunctions;
	}

	@Override
	public boolean isDefinedFor(Entity inItem)
	{
		return functions.stream().anyMatch(f -> f.isDefinedFor(inItem));
	}

	@Override
	public boolean isDefinedFor(Entity inItem1, Entity inItem2)
	{
		return functions.stream().anyMatch(f -> f.isDefinedFor(inItem1, inItem2));
	}

	@Override
	public double computeSimilarity(Entity inItem1, Entity inItem2)
	{
		if (inItem1.getEntityLabel().equals(inItem2.getEntityLabel()))
			return 1.0;
		if (!isDefinedFor(inItem1, inItem2))
			return 0.0;

		return functions.stream()
				.filter(f -> f.isDefinedFor(inItem1, inItem2))
				.findFirst()
				.get().computeSimilarity(inItem1, inItem2);
	}
}
