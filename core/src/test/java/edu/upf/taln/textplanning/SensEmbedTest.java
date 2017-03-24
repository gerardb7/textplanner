package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;

/**
 * Test for sense vectors
 */
public class SensEmbedTest
{
	private static class TestEntity extends Entity
	{
		public final String id;

		public TestEntity(String id)
		{
			this.id = id;
		}

		@Override
		public String getEntityLabel()
		{
			return null;
		}
	}

	private static final TestEntity id1 = new TestEntity("bn:00028015n");
	private static final TestEntity id2 = new TestEntity("bn:00076732n");
	private static final TestEntity id3 = new TestEntity("bn:00094089v");
	private static final TestEntity id4 = new TestEntity("bn:00086931v");
	private static final TestEntity id5 = new TestEntity("bn:00094089v");
	private static final TestEntity id6 = new TestEntity("bn:00063329n");
	private static final TestEntity id7 = new TestEntity("bn:00024591n");
	private static final TestEntity id8 = new TestEntity("bn:00088421v");

	@Test
	public void testComputeSimilarity() throws Exception
	{
		SensEmbed vectors = new SensEmbed(Paths.get("src/test/resources/test_dummy_vectors"));


		double sim11 = vectors.computeSimilarity(id1, id1);
		System.out.println(id1 + " - " + id1 + " = " + sim11);
		Assert.assertTrue(sim11 == sim11);

		double sim12 = vectors.computeSimilarity(id1, id2);
		System.out.println(id1 + " - " + id2 + " = " + sim12);
		double sim13 = vectors.computeSimilarity(id1, id3);
		System.out.println(id1 + " - " + id3 + " = " + sim13);
		double sim14 = vectors.computeSimilarity(id1, id4);
		System.out.println(id1 + " - " + id4 + " = " + sim14);
		double sim15 = vectors.computeSimilarity(id1, id5);
		System.out.println(id1 + " - " + id5 + " = " + sim15);
		double sim16 = vectors.computeSimilarity(id1, id6);
		System.out.println(id1 + " - " + id6 + " = " + sim16);
		double sim17 = vectors.computeSimilarity(id1, id7);
		System.out.println(id1 + " - " + id7 + " = " + sim17);
		double sim18 = vectors.computeSimilarity(id1, id8);
		System.out.println(id1 + " - " + id8 + " = " + sim18);
		Assert.assertTrue(sim12 > sim13 && sim12 > sim14 && sim12 > sim15 && sim12 > sim16 && sim12 > sim17 &&
				sim12 > sim18);

		System.out.println("---");
		double sim31 = vectors.computeSimilarity(id3, id1);
		System.out.println(id3 + " - " + id1 + " = " + sim31);
		double sim32 = vectors.computeSimilarity(id3, id2);
		System.out.println(id3 + " - " + id2 + " = " + sim32);
		double sim34 = vectors.computeSimilarity(id3, id4);
		System.out.println(id3 + " - " + id4 + " = " + sim34);
		double sim35 = vectors.computeSimilarity(id3, id5);
		System.out.println(id3 + " - " + id5 + " = " + sim35);
		double sim36 = vectors.computeSimilarity(id3, id6);
		System.out.println(id3 + " - " + id6 + " = " + sim36);
		double sim37 = vectors.computeSimilarity(id3, id7);
		System.out.println(id3 + " - " + id7 + " = " + sim37);
		double sim38 = vectors.computeSimilarity(id3, id8);
		System.out.println(id3 + " - " + id8 + " = " + sim38);
		Assert.assertTrue(sim34 > sim31 && sim34 > sim31 && sim35 > sim31 && sim35 > sim31 &&
				sim34 > sim32 && sim34 > sim32 && sim35 > sim32 && sim35 > sim32 &&
				sim34 > sim36 && sim34 > sim36 && sim35 > sim36 && sim35 > sim36 &&
				sim34 > sim37 && sim34 > sim37 && sim35 > sim37 && sim35 > sim37 &&
				sim34 > sim38 && sim34 > sim38 && sim35 > sim38 && sim35 > sim38);

		System.out.println("---");
		double sim61 = vectors.computeSimilarity(id6, id1);
		System.out.println(id6 + " - " + id1 + " = " + sim61);
		double sim62 = vectors.computeSimilarity(id6, id2);
		System.out.println(id6 + " - " + id2 + " = " + sim62);
		double sim63 = vectors.computeSimilarity(id6, id3);
		System.out.println(id6 + " - " + id3 + " = " + sim63);
		double sim64 = vectors.computeSimilarity(id6, id4);
		System.out.println(id6 + " - " + id4 + " = " + sim64);
		double sim65 = vectors.computeSimilarity(id6, id5);
		System.out.println(id6 + " - " + id5 + " = " + sim65);
		double sim67 = vectors.computeSimilarity(id6, id7);
		System.out.println(id6 + " - " + id7 + " = " + sim67);
		double sim68 = vectors.computeSimilarity(id6, id8);
		System.out.println(id6 + " - " + id8 + " = " + sim68);
		Assert.assertTrue(sim67 > sim61 && sim67 > sim62 && sim67 > sim63 && sim67 > sim64 &&
				sim67 > sim65 && sim67 > sim68);
	}
}