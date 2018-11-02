package edu.upf.taln.textplanning.core.weighting;


import edu.upf.taln.textplanning.core.structures.Candidate;

import java.util.Collection;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Collection<Candidate> contents);
	double weight(String item);
}
