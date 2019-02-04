package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.List;
import java.util.Optional;

public interface SentenceVectors
{
	boolean isDefinedFor(String item);
	int getNumDimensions();
	Optional<double[]> getVector(List<String> items);
	double[] getUnknownVector();

	enum VectorType {BoW, SIF, Precomputed}
}
