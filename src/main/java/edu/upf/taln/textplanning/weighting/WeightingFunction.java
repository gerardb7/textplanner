package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;

import java.util.Set;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Set<SemanticGraph> contents);
	double weight(String item);
}
