package edu.upf.taln.textplanning.metrics;

import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Base class for metrics for evaluation of patterns
 */
public interface PatternMetric
{
	Map<SemanticTree, Double> assess(Set<String> inReferenceEntities, Collection<SemanticTree> inPatterns);
}
