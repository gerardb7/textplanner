package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.List;

/**
 * Base class for classes implementing read/writeSemanticDAGs access to documents containing semantic structures.
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

	List<DirectedAcyclicGraph<AnnotationInfo, LabelledEdge>> readSemanticDAGs(String inDocumentContents);

	List<SemanticTree> readSemanticTrees(String inDocumentContents);
}
