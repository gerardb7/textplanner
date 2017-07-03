package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;

import java.io.IOException;
import java.util.List;

/**
 * Base class for classes implementing read/writeGraphs access to documents containing semantic structures.
 */
public interface DocumentAccess
{
	List<SemanticGraph> readStructures(String inDocumentContents) throws IOException;
}
