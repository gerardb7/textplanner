package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.input.amr.SemanticGraph;

import java.util.List;

/**
 * Base class for classes implementing read/writeGraphs access to documents containing semantic structures.
 */
public interface DocumentReader
{
	List<SemanticGraph> read(String inDocumentContents);
}
