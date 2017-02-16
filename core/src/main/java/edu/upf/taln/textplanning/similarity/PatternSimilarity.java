package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.SemanticTree;

/**
 * Interface for classes implementing similarity metrics between pairs of patterns.
 */
public interface PatternSimilarity
{
	double getSimilarity(SemanticTree inPattern1, SemanticTree inPatern2);

	double getSimilarity(String inEntity1, String inEntity2);
}
