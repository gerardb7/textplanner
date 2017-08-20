package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.io.IOException;
import java.util.List;

/**
 * Base class for classes implementing read/writeGraphs access to documents containing semantic structures.
 */
public interface DocumentReader
{
	List<LinguisticStructure> readStructures(String inDocumentContents) throws IOException;
}
