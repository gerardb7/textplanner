package edu.upf.taln.textplanning.similarity;

import java.util.OptionalDouble;

/**
 * Base class for similarity functions comparing pairs of items
 */
public interface SimilarityFunction
{
	boolean isDefinedFor(String e);
	boolean isDefinedFor(String e1, String e2);
	OptionalDouble computeSimilarity(String e1, String e2);}
