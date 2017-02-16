package edu.upf.taln.textplanning.similarity;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import unnonouno.treedist.EditScore;
import unnonouno.treedist.TreeEditDistance;

/**
 * Semantic similarity between pairs of semantic trees.
 * This class is immutable.
 */
public final class TreeEditSimilarity implements PatternSimilarity
{
	private final ItemSimilarity wordSenseVectors;
	private final ItemSimilarity wordFormVectors;
	public static long numWordSuccessfulLookups = 0; // for debugging purposes
	public static long numWordFailedLookups = 0; // for debugging purposes
	public static long numSenseSuccessfulLookups = 0; // for debugging purposes
	public static long numSenseFailedLookups = 0; // for debugging purposes

//	private final static Logger log = LoggerFactory.getLogger(TreeEditSimilarity.class);

	public TreeEditSimilarity(ItemSimilarity inWordFormVectors, ItemSimilarity inWordSenseVectors)
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
	public double getSimilarity(AnnotationInfo inAnn1, AnnotationInfo inAnn2)
	{
		if (wordSenseVectors != null)
		{
			if (wordSenseVectors.isDefinedFor(inAnn1.getReference(), inAnn2.getReference()))
			{
				++numSenseSuccessfulLookups;
				return wordSenseVectors.computeSimilarity(inAnn1.getReference(), inAnn2.getReference());
			}
			else
			{
				++numSenseFailedLookups;
			}
		}
		if (wordFormVectors != null)
		{
			if (wordFormVectors.isDefinedFor(inAnn1.getReference(), inAnn2.getReference()))
			{
				++numWordSuccessfulLookups;
				return wordFormVectors.computeSimilarity(inAnn1.getForm(), inAnn2.getForm());
			}
			else
			{
				++numWordFailedLookups;
			}
		}
		return inAnn1.getLemma().equalsIgnoreCase(inAnn2.getLemma()) ? 1.0 : 0.0; // Use lemma matching
	}


	/**
	 * Returns similarity between a pair of entities
	 *
	 * @param inEntity1 first entity
	 * @param inEntity2 second entity
	 * @return non-normalized similarity value
	 */
	public double getSimilarity(String inEntity1, String inEntity2)
	{
		return wordSenseVectors.computeSimilarity(inEntity1, inEntity2);
	}
}
