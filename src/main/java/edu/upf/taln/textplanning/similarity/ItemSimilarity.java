package edu.upf.taln.textplanning.similarity;


/**
 * Interface for similarity measures between generic atomic items
 */
public interface ItemSimilarity
{
	boolean isDefinedFor(String item);
	boolean isDefinedFor(String item1, String item2);
	double computeSimilarity(String item1, String item2);

}
