package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Collection;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Collection<LinguisticStructure> contents);
	double weight(String item);
}
