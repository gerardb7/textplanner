package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.structures.Entity;

/**
 * Base class for similarity function comparing pairs of Entity objects
 */
public interface EntitySimilarity
{
	boolean isDefinedFor(Entity e);
	boolean isDefinedFor(Entity e1, Entity e2);
	double computeSimilarity(Entity e1, Entity e2);
}
