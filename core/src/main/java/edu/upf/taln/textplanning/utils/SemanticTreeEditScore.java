package edu.upf.taln.textplanning.utils;

import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import unnonouno.treedist.EditScore;


/**
 * Implementation of EditScore interface for tree edit library.
 * Immutable class.
 */
public final class SemanticTreeEditScore implements EditScore
{
	private final PatternSimilarity sim;
	private final SemanticTreeProxy tree1;
	private final SemanticTreeProxy tree2;

	public SemanticTreeEditScore(PatternSimilarity inSimilarity, SemanticTreeProxy inTree1, SemanticTreeProxy inTree2)
	{
		this.sim = inSimilarity;
		this.tree1 = inTree1;
		this.tree2 = inTree2;
	}


	@Override
	public double replace(int i1, int i2)
	{
		Node e1 = tree1.getEntity(i1);
		Node e2 = tree2.getEntity(i2);

		// Score is inverse of similarity
		assert e1 != null;
		assert e2 != null;
		return 1.0 - sim.getSimilarity(e1.entity, e2.entity);
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
