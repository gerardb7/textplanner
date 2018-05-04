package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.amr.GraphList;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(GraphList contents);
	double weight(String item);
}
