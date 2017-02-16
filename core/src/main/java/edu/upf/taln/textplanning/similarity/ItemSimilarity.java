package edu.upf.taln.textplanning.similarity;

/**
 * Interface for similarity measures between annotated items, e.g. forms, lemmas, word senses, etc.
 */
public interface ItemSimilarity
{
	boolean isDefinedFor(String inEntry1, String inEntry2);
	double computeSimilarity(String inEntry1, String inEntry2);
}
