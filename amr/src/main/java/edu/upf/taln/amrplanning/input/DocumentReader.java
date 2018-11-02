package edu.upf.taln.amrplanning.input;

import edu.upf.taln.amrplanning.structures.SemanticGraph;

import java.util.List;

/**
 * Base class for classes implementing read/writeGraphs access to documents containing semantic structures.
 */
public interface DocumentReader
{
	List<SemanticGraph> read(String inDocumentContents);
}
