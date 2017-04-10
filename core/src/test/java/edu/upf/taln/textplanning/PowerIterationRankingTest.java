package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.ranking.PowerIterationRanking;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Tests ranking algorithm using synthetic data
 */
public class PowerIterationRankingTest
{
	private static class DummyEntity extends Entity
	{
		private final int id;
		private static int counter = 0;

		public DummyEntity() { id = ++counter; }
		@Override
		public String getEntityLabel()	{ return Integer.toString(id); }
	}

	private static class DummyWeighting implements WeightingFunction
	{
		private final int exp;

		public DummyWeighting(int exp) { this.exp = exp; }

		@Override
		public void setCollection(List<SemanticTree> inCollection) { }

		@Override
		public double weight(Entity inEntity)
		{
			DummyEntity e = (DummyEntity)inEntity;
			double sumWeights = IntStream.range(1, DummyEntity.counter + 1)
					.mapToDouble(i -> Math.pow(i, exp))
					.sum();
			return Math.pow(e.id, exp) / sumWeights;
		}
	}

	private static class DummySimilarity implements EntitySimilarity
	{
		@Override public boolean isDefinedFor(Entity inItem) { return true; }
		@Override public boolean isDefinedFor(Entity inItem1, Entity inItem2) {	return true; }

		@Override public double computeSimilarity(Entity inItem1, Entity inItem2)
		{
			DummyEntity e1 = (DummyEntity)inItem1;
			DummyEntity e2 = (DummyEntity)inItem2;

			double k = DummyEntity.counter;
			double d = (double) Math.abs(e1.id - e2.id);
			return (k-d)/k ;
		}
	}

	@Test
	public void testRanking()
	{
		List<Entity> entities = new ArrayList<>();
		for (int i=0; i<3; ++i)
		{
			entities.add(new DummyEntity());
		}

		TextPlanner.Options options = new TextPlanner.Options();
		options.rankingStopThreshold = 0.00001;
		options.dampingFactor = 0.5;
		options.generateStats = true;
		DummyWeighting weight = new DummyWeighting(2);
		Matrix rankingMatrix = PowerIterationRanking.createRankingMatrix(entities, weight, new DummySimilarity(), options);
		Matrix result = PowerIterationRanking.run(rankingMatrix, options.rankingStopThreshold);

		double[] weights = entities.stream().mapToDouble(weight::weight).toArray();
		System.out.println("Initial weights = " + Arrays.toString(weights));
		System.out.println("Final ranking = " + Arrays.toString(result.getColumnPackedCopy()));
	}
}
