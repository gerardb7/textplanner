package edu.upf.taln.textplanning.weighting;


import edu.upf.taln.textplanning.structures.Candidate;

import java.util.Collection;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Collection<Candidate> contents);
	double weight(String item);
}
