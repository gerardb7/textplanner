package edu.upf.taln.textplanning.core.similarity;

import treedist.EditScore;

import java.util.Optional;


/**
 * Implementation of EditScore interface for tree edit library.
 * Immutable class.
 */
public final class SemanticTreeEditScorer implements EditScore
{
	private final SemanticTreeSimilarity sim;
	private final double lambda;
	private final SemanticTreeProxy tree1;
	private final SemanticTreeProxy tree2;

	SemanticTreeEditScorer(SemanticTreeSimilarity inSimilarity, double lambda, SemanticTreeProxy inTree1, SemanticTreeProxy inTree2)
	{
		this.sim = inSimilarity;
		this.lambda = lambda;
		this.tree1 = inTree1;
		this.tree2 = inTree2;
	}


	@Override
	public double replace(int i1, int i2)
	{
		Optional<String> m1 = tree1.getMeaning(i1);
		Optional<String> m2 = tree2.getMeaning(i2);
		String r1 = tree1.getParentRole(i1);
		String r2 = tree2.getParentRole(i2);

		double similarity = 0.0;
		if (m1.isPresent() && m2.isPresent())
			similarity = sim.getSimilarity(m1.get(), m2.get());

		return 1.0 - lambda *(r1.equals(r2) ? 1.0 : 0.0) - (1.0 - lambda)*similarity;
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
