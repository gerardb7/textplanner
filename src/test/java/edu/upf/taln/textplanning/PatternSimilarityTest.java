package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.input.CoNLLFormat;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.structures.ContentPattern;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
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
		CoNLLFormat conll = new CoNLLFormat();
		List<ContentPattern> trees = null;// conll.readStructures("src/test/resources/test_dummy.conll");
		ContentPattern tree1 = trees.get(0);
		ContentPattern tree2 = trees.get(1);
		ContentPattern tree3 = trees.get(2);
		ContentPattern tree4 = trees.get(3);
		ContentPattern tree5 = trees.get(4);

		EntitySimilarity senseVectors = new SensEmbed(Paths.get("/home/gerard/data/sense/babelfy_vectors_merged_senses_only"));
		PatternSimilarity simCalc = new PatternSimilarity(senseVectors);

		System.out.println("OrderedTree 1:\n" + conll.writePatterns(Collections.singleton(tree1)));
		System.out.println("OrderedTree 2:\n" + conll.writePatterns(Collections.singleton(tree2)));
		System.out.println("OrderedTree 3:\n" + conll.writePatterns(Collections.singleton(tree3)));
		System.out.println("OrderedTree 4:\n" + conll.writePatterns(Collections.singleton(tree4)));
		System.out.println("OrderedTree 5:\n" + conll.writePatterns(Collections.singleton(tree5)));

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