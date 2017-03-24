package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.utils.TreeEditSimilarity;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tester for semantic similarity between trees
 */
public class TreeEditSimilarityTest
{

	@Test
	public void testGetSimilarity() throws Exception
	{
		ConLLAcces reader = new ConLLAcces();
		List<AnnotatedTree> trees = reader.readSemanticTrees("src/test/resources/test_dummy.conll");
		AnnotatedTree tree1 = trees.get(0);
		AnnotatedTree tree2 = trees.get(1);
		AnnotatedTree tree3 = trees.get(2);
		AnnotatedTree tree4 = trees.get(3);
		AnnotatedTree tree5 = trees.get(4);

		EntitySimilarity wordVectors = null; //new Word2Vec("/home/gerard/data/GoogleNews-vectors-negative300.bin");
		EntitySimilarity senseVectors = null; //new SensEmbed("/home/gerard/data/sensembed/babelfy_vectors_merged_senses_only");
		TreeEditSimilarity simCalc = new TreeEditSimilarity(wordVectors, senseVectors);

		ConLLAcces conll = new ConLLAcces();
		System.out.println("OrderedTree 1:\n" + conll.writeSemanticTrees(Collections.singleton(tree1)));
		System.out.println("OrderedTree 2:\n" + conll.writeSemanticTrees(Collections.singleton(tree2)));
		System.out.println("OrderedTree 3:\n" + conll.writeSemanticTrees(Collections.singleton(tree3)));
		System.out.println("OrderedTree 4:\n" + conll.writeSemanticTrees(Collections.singleton(tree4)));
		System.out.println("OrderedTree 5:\n" + conll.writeSemanticTrees(Collections.singleton(tree5)));

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