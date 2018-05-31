package edu.upf.taln.textplanning.weighting;

import java.util.Collection;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Collection<String> contents);
	double weight(String item);
}
