package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.DocumentAccess;
import edu.upf.taln.textplanning.input.DocumentProvider;

import java.util.Set;

/**
 * Interface for strategies for pattern extraction
 */
public interface PatternExtractor
{
	Set<SemanticTree> getPatterns(DocumentProvider inDocs, DocumentAccess inReader);
}
