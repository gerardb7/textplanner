package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.jgrapht.graph.DefaultEdge;

import java.io.IOException;
import java.util.List;

/**
 * Base class for classes implementing read/writeGraphs access to documents containing semantic structures.
 */
public interface DocumentAccess
{
	class LabelledEdge extends DefaultEdge
	{
		private final String label;

		public LabelledEdge(String inLabel)
		{
			label = inLabel;
		}

		public String getLabel()
		{
			return label;
		}
	}

	List<SemanticTree> readTrees(String inDocumentContents) throws IOException;
}
