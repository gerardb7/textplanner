package edu.upf.taln.textplanning.similarity;


import edu.upf.taln.textplanning.datastructures.Entity;

/**
 * Interface for similarity measures between generic items
 */
public interface EntitySimilarity
{
	boolean isDefinedFor(Entity inItem);
	boolean isDefinedFor(Entity inItem1, Entity inItem2);
	double computeSimilarity(Entity inItem1, Entity inItem2);

}
