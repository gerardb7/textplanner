package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import edu.upf.taln.textplanning.similarity.Random;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tester for semantic similarity between trees
 */
public class PatternSimilarityTest
{

	@Test
	public void testGetSimilarity() throws Exception
	{
		ConLLAcces conll = new ConLLAcces();
		// todo fix this once figured out wether output should be trees or graphs
		List<SemanticTree> trees = null;// conll.readStructures("src/test/resources/test_dummy.conll");
		SemanticTree tree1 = trees.get(0);
		SemanticTree tree2 = trees.get(1);
		SemanticTree tree3 = trees.get(2);
		SemanticTree tree4 = trees.get(3);
		SemanticTree tree5 = trees.get(4);

		ItemSimilarity wordVectors = null; //new word("/home/gerard/data/GoogleNews-vectors-negative300.bin");
		ItemSimilarity senseVectors = null; //new sense("/home/gerard/data/sense/babelfy_vectors_merged_senses_only");
		ItemSimilarity combined = new Random();
		PatternSimilarity simCalc = new PatternSimilarity(combined);

		System.out.println("OrderedTree 1:\n" + conll.writeTrees(Collections.singleton(tree1)));
		System.out.println("OrderedTree 2:\n" + conll.writeTrees(Collections.singleton(tree2)));
		System.out.println("OrderedTree 3:\n" + conll.writeTrees(Collections.singleton(tree3)));
		System.out.println("OrderedTree 4:\n" + conll.writeTrees(Collections.singleton(tree4)));
		System.out.println("OrderedTree 5:\n" + conll.writeTrees(Collections.singleton(tree5)));

		double sim12 = simCalc.getSimilarity(tree1, tree2);
		System.out.println("Similarity 1-2 = " + sim12);
		double sim13 = simCalc.getSimilarity(tree1, tree3);
		System.out.println("Similarity 1-3 = " + sim13);
		double sim14 = simCalc.getSimilarity(tree1, tree4);
		System.out.println("Similarity 1-4 = " + sim14);
		double sim15 = simCalc.getSimilarity(tree1, tree5);
		System.out.println("Similarity 1-5 = " + sim15);
		double sim34 = simCalc.getSimilarity(tree3, tree4);
		System.out.println("Similarity 2-3 = " + sim34);

		Assert.assertTrue(sim12 > sim13);
		Assert.assertTrue(sim13 > sim14);
		Assert.assertTrue(sim12 > sim15);
	}
}