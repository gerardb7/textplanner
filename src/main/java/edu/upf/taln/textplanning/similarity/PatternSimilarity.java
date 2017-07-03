package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import unnonouno.treedist.EditScore;
import unnonouno.treedist.TreeEditDistance;

/**
 * Semantic similarity between pairs of semantic trees, or more generally, labelled ordered trees.
 * This class is immutable.
 */
public final class PatternSimilarity
{
	private final ItemSimilarity itemSimilarity;

	public PatternSimilarity(ItemSimilarity inItemSimilarity)
	{
		itemSimilarity = inItemSimilarity;
	}

	/**
	 * Public method to be called to obtain similarity between two semantic trees.
	 * In order to calculate similarity, the two trees are interpreted as linearly ordered trees and compared
	 * using a tree edit distance metric where costs of edits depend on semantic similarity of pairs of nodes/entities.
	 *
	 * @param inTree1 1st tree
	 * @param inTree2 2nd tree
	 * @return a similarity metric normalized to [0,1.0]
	 */
	public double getSimilarity(SemanticTree inTree1, SemanticTree inTree2)
	{
		SemanticTreeProxy tree1 = new SemanticTreeProxy(inTree1);
		SemanticTreeProxy tree2 = new SemanticTreeProxy(inTree2);
		EditScore scorer = new SemanticTreeEditScore(this, tree1, tree2);
//		Mapping mapping = new Mapping(tree1, tree2);
		double distance = new TreeEditDistance(scorer).calc(tree1, tree2);//, mapping);
		if (distance == 0.0 || distance == 1.0)
		{
			return 1.0 - distance;
		}

		// Squashing functions, see http://tinyurl.com/jobjdxl
//		double normalizedDistance = Math.tanh(distance);
//		double normalizedDistance = (1 / (1 + Math.exp(-distance))) * 2 - 1;
		// See LI, ZHANG1 (2011) "A metric normalization of tree edit distance"
		double normalizedDistance = Math.min(1.0, distance / (double) (tree1.size() + tree2.size()));

		return 1.0 - normalizedDistance;

	}

	/**
	 * Callback method called from tree edit algorithm (i.e. SemanticScore class)
	 */
	public double getSimilarity(Entity e1, Entity e2)
	{
		return itemSimilarity.computeSimilarity(e1.getLabel(), e2.getLabel());
	}
}
