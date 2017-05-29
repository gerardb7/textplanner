package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.List;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setCollection(List<SemanticTree> inCollection);
	double weight(Entity inEntity);
}
