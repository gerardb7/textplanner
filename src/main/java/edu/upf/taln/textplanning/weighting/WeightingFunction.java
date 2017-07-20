package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Set;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Set<LinguisticStructure> contents);
	double weight(String item);
}
