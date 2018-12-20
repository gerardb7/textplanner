package edu.upf.taln.textplanning.core.similarity.vectors;

public interface Vectors
{
	boolean isDefinedFor(String item);
	double[] getVector(String item);
}
