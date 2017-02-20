package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.List;
import java.util.Set;

/**
 * Interface for strategies for pattern extraction
 */
public interface PatternExtractor
{
	Set<SemanticTree> getPatterns(List<SemanticTree> inContents);
}
