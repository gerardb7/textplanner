package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.Optional;

public interface Vectors
{
	boolean isDefinedFor(String item);
	Optional<double[]> getVector(String item);
	int getNumDimensions();
}
