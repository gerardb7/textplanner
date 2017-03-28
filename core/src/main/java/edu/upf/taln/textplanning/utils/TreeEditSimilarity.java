package edu.upf.taln.textplanning.utils;

import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import unnonouno.treedist.EditScore;
import unnonouno.treedist.TreeEditDistance;

/**
 * Semantic similarity between pairs of annotated trees.
 * This class is immutable.
 */
public final class TreeEditSimilarity
{
	private final EntitySimilarity wordSenseVectors;
	private final EntitySimilarity wordFormVectors;
	public static long numWordSuccessfulLookups = 0; // for debugging purposes
	public static long numWordFailedLookups = 0; // for debugging purposes
	public static long numSenseSuccessfulLookups = 0; // for debugging purposes
	public static long numSenseFailedLookups = 0; // for debugging purposes

//	private final static Logger log = LoggerFactory.getLogger(TreeEditSimilarity.class);

	public TreeEditSimilarity(EntitySimilarity inWordFormVectors, EntitySimilarity inWordSenseVectors)
	{
		wordFormVectors = inWordFormVectors;
		wordSenseVectors = inWordSenseVectors;
	}

	/**
	 * Public method to be called to obtain similarity between two semantic trees.
	 * In order to calculate similarity, the two trees are interpreted as linearly ordered trees and compared
	 * using a tree edit distance metric where costs of edits depend on semantic similarity of referred entities.
	 * This semantic similarity between entities is obtained by comparing the vectors of each entity.
	 *
	 * @param inTree1 1st tree
	 * @param inTree2 2nd tree
	 * @return a similarity metric normalized to [0,1.0]
	 */
	public double getSimilarity(AnnotatedTree inTree1, AnnotatedTree inTree2)
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

//		log.debug(mapping.toString());
//		for (int op : mapping.getAllInsertion())
//			log.debug("Insertion: " + String.valueOf(op));
//		for (int op : mapping.getAllDeletion())
//			log.debug("Deletion: " + String.valueOf(op));
//		for (int[] op : mapping.getAllReplacement())
//			log.debug("Replacement: " + String.valueOf(op[0]) + " -> " + String.valueOf(op[1]));
	}

	/**
	 * Callback method called from tree edit algorithm (i.e. SemanticScore class)
	 */
	public double getSimilarity(AnnotatedEntity inAnn1, AnnotatedEntity inAnn2)
	{
		if (wordSenseVectors != null)
		{
			if (wordSenseVectors.isDefinedFor(inAnn1, inAnn2))
			{
				++numSenseSuccessfulLookups;
				return wordSenseVectors.computeSimilarity(inAnn1, inAnn2);
			}
			else
			{
				++numSenseFailedLookups;
			}
		}
		if (wordFormVectors != null)
		{
			if (wordFormVectors.isDefinedFor(inAnn1, inAnn2))
			{
				++numWordSuccessfulLookups;
				return wordFormVectors.computeSimilarity(inAnn1, inAnn2);
			}
			else
			{
				++numWordFailedLookups;
			}
		}

		// Fall back to lemma matching
		return inAnn1.getAnnotation().getLemma().equalsIgnoreCase(inAnn2.getAnnotation().getLemma()) ? 1.0 : 0.0;
	}
}
