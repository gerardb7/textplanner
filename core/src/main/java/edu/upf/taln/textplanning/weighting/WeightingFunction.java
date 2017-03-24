package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.Entity;

import java.util.List;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setCollection(List<AnnotatedTree> inCollection);
	double weight(Entity inEntity);
}
