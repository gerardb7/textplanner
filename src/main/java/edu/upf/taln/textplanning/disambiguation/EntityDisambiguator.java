package edu.upf.taln.textplanning.disambiguation;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Collection;

/**
 * Interface for classes performing entity disambiguation (entity linking, word sense disambiguation or both)
 * against some lexical database or KB.
 */
public interface EntityDisambiguator
{
	void annotateCandidates(Collection<LinguisticStructure> structures);
//	void expandCandidates(Set<LinguisticStructure> structures);
	void disambiguate(Collection<LinguisticStructure> structures);
}
