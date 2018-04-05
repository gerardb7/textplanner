package edu.upf.taln.textplanning.similarity;

import unnonouno.treedist.EditScore;


/**
 * Implementation of EditScore interface for tree edit library.
 * Immutable class.
 */
public final class SemanticTreeEditScorer implements EditScore
{
	private final SemanticTreeSimilarity sim;
	private final double delta;
	private final SemanticTreeProxy tree1;
	private final SemanticTreeProxy tree2;

	SemanticTreeEditScorer(SemanticTreeSimilarity inSimilarity, double delta, SemanticTreeProxy inTree1, SemanticTreeProxy inTree2)
	{
		this.sim = inSimilarity;
		this.delta = delta;
		this.tree1 = inTree1;
		this.tree2 = inTree2;
	}


	@Override
	public double replace(int i1, int i2)
	{
		String m1 = tree1.getMeaning(i1);
		String m2 = tree2.getMeaning(i2);
		String r1 = tree1.getParentRole(i1);
		String r2 = tree2.getParentRole(i2);

		return 1.0 - delta*(r1.equals(r2) ? 1.0 : 0.0) - (1.0 - delta)*sim.getSimilarity(m1, m2);
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
