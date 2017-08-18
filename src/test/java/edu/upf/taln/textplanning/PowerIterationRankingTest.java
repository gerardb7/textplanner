package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.ranking.PowerIterationRanking;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.junit.Test;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Tests ranking algorithm using synthetic data
 */
public class PowerIterationRankingTest
{
	private static int counter = 0;

	private static final class DummyWeighting implements WeightingFunction
	{
		private final int exp;

		public DummyWeighting(int exp) { this.exp = exp; }

		@Override
		public void setContents(Collection<LinguisticStructure> contents) { }

		@Override
		public double weight(String item)
		{
			double sumWeights = IntStream.range(1, counter + 1)
					.mapToDouble(i -> Math.pow(i, exp))
					.sum();
			return Math.pow(Integer.parseInt(item), exp) / sumWeights;
		}
	}

	private static class DummySimilarity implements EntitySimilarity
	{
		@Override public boolean isDefinedFor(Entity item) { return true; }
		@Override public boolean isDefinedFor(Entity item1, Entity item2) {	return true; }

		@Override public double computeSimilarity(Entity item1, Entity item2)
		{
			int i1 = Integer.parseInt(item1.getId());
			int i2 = Integer.parseInt(item2.getId());

			double k = counter;
			double d = (double) Math.abs(i1 - i2);
			return (k-d)/k ;
		}
	}

	@Test
	public void testRanking()
	{
		List<String> entities = new ArrayList<>();
		for (int i=0; i<9; ++i)
		{
			entities.add(String.valueOf(counter++));
		}

		TextPlanner.Options options = new TextPlanner.Options();
		options.rankingStopThreshold = 0.0001;
		options.dampingRelevance = 0.3;
		options.generateStats = true;
		DummyWeighting weight = new DummyWeighting(5);
		Matrix rankingMatrix = null; // PowerIterationRanking.createCandidateRankingMatrix(entities, weight, new DummySimilarity(), options);
		Matrix result = PowerIterationRanking.run(rankingMatrix, options.rankingStopThreshold);

		double[] weights = entities.stream().mapToDouble(weight::weight).toArray();
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(3);
		f.setMinimumFractionDigits(3);
		System.out.println("Initial weights = " + Arrays.stream(weights)
				.mapToObj(f::format)
				.collect(Collectors.joining(", ")));
		System.out.println("Final ranking = " + Arrays.stream(result.getColumnPackedCopy())
				.mapToObj(f::format)
				.collect(Collectors.joining(", ")));
	}
}
