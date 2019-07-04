package edu.upf.taln.textplanning.core.extraction;

public interface Policy
{
	enum Type {ArgMax, Softmax}

	int select(double[] weights);
}
