package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.List;
import java.util.Optional;

public interface SentenceVectors
{
	boolean isDefinedFor(List<String> tokens);
	int getNumDimensions();
	Optional<double[]> getVector(List<String> tokens);

	public enum SentenceVectorType {BoW, SIF, Precomputed, Random}
}
