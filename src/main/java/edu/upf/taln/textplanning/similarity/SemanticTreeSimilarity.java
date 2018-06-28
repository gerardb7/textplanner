package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.structures.SemanticTree;
import unnonouno.treedist.EditScore;
import unnonouno.treedist.TreeEditDistance;

/**
 * Semantic similarity between pairs of semantic trees, or more generally, labelled ordered trees.
 * This class is immutable.
 */
public final class SemanticTreeSimilarity
{
	private final SimilarityFunction sim;
	private final double delta;

	public SemanticTreeSimilarity(SimilarityFunction s, double delta)
	{
		sim = s;
		this.delta = delta;
	}

	/**
	 * Public method to be called to obtain similarity between two semantic trees.
	 * In order to calculate similarity, the two trees are interpreted as linearly ordered trees and compared
	 * using a tree edit distance metric where costs of edits depend on semantic similarity of pairs of nodes/entities.
	 */
	public double getSimilarity(SemanticTree t1, SemanticTree t2)
	{
		SemanticTreeProxy p1 = new SemanticTreeProxy(t1);
		SemanticTreeProxy p2 = new SemanticTreeProxy(t2);
		EditScore scorer = new SemanticTreeEditScorer(this, delta, p1, p2);
//		Mapping mapping = new Mapping(p1, p2);
		double distance = new TreeEditDistance(scorer).calc(p1, p2);//, mapping);

		// Squashing functions, see http://tinyurl.com/jobjdxl
//		double normalizedDistance = Math.tanh(distance);
//		double normalizedDistance = (1 / (1 + Math.exp(-distance))) * 2 - 1;
		// See LI, ZHANG1 (2011) "A metric normalization of tree edit distance"
		double normalizedDistance = Math.min(1.0, distance / (double) (p1.size() + p2.size()));

		return 1.0 - normalizedDistance;

	}

	/**
	 * Callback method called from tree edit algorithm (index.e. SemanticScore class)
	 */
	public double getSimilarity(String m1, String m2)
	{
		return sim.computeSimilarity(m1, m2).orElse(0.0);
	}
}
