package edu.upf.taln.textplanning.disambiguation;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;

import java.util.Map;
import java.util.Set;

/**
 * Interface for classes performing entity disambiguation (entity linking, word sense disambiguation or both)
 * against some lexical database or KB.
 */
public interface EntityDisambiguator
{
	void annotateCandidates(Set<SemanticGraph> structures);
	void expandCandidates(Set<SemanticGraph> structures);
	void disambiguate(Set<SemanticGraph> structures, Map<String, Double> rankedEntities);
}
