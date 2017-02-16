package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import org.apache.commons.lang3.tuple.Pair;
import unnonouno.treedist.EditScore;


/**
 * Implementation of EditScore interface for tree edit library.
 * Immutable class.
 */
public final class SemanticTreeEditScore implements EditScore
{
	private final TreeEditSimilarity sim;
	private final SemanticTreeProxy tree1;
	private final SemanticTreeProxy tree2;

	public SemanticTreeEditScore(TreeEditSimilarity inSimilarity, SemanticTreeProxy inTree1, SemanticTreeProxy inTree2)
	{
		this.sim = inSimilarity;
		this.tree1 = inTree1;
		this.tree2 = inTree2;
	}


	@Override
	public double replace(int i1, int i2)
	{
		OrderedTree.Node<Pair<AnnotationInfo, String>> e1 = tree1.getEntity(i1);
		OrderedTree.Node<Pair<AnnotationInfo, String>> e2 = tree2.getEntity(i2);

		// Score is inverse of similarity
		double score = 1.0 - sim.getSimilarity(e1.getData().getLeft(), e2.getData().getLeft());
		return score;
	}

	@Override
	public double delete(int i)
	{
		return 1.0;
	}

	@Override
	public double insert(int i)
	{
		return 1.0;
	}
}
