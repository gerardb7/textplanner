package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Implementation of a power iteration algorithm for ranking with Markov chains.
 */
public class PowerIterationRanking
{
	private final static Logger log = LoggerFactory.getLogger(PowerIterationRanking.class);


	/**
	 * Creates an initial distribution which can be used as an initial state of a Markov chain.
	 * @param n the number of states in the chain
	 * @return distribution
	 */
	public static Matrix createInitialDistribution(int n)
	{
		double[] initialDist = IntStream.range(0, n)
				.mapToDouble(i -> 1.0 / (double) n)
				.toArray();
		return new Matrix(initialDist, 1);
	}

	/**
	 * Creates a stochastic matrix for a list of entities where probabilities are based on a weighting function.
	 * @param entities list of entities
	 * @param inWeighting weighting function
	 * @return a relevance matrix
	 */
	public static Matrix createRelevanceMatrix(List<Entity> entities, WeightingFunction inWeighting)
	{
		// Create stochastic matrix using relevance metric
		int n = entities.size();
		Matrix stochasticMatrix = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					double rj = inWeighting.weight(entities.get(j));
					stochasticMatrix.set(i, j, rj);
				}));

		// Transform into probability (stochastic) matrix by normalizing relevance scores against the sum of each row
		IntStream.range(0, n).forEach(i ->
		{
			double accum = Arrays.stream(stochasticMatrix.getArray()[i]).sum();
			IntStream.range(0, n).forEach(j -> stochasticMatrix.set(i, j, stochasticMatrix.get(i, j) / accum));
		});

		return stochasticMatrix;
	}

	/**
	 * Creates a stochastic matrix for a set of entities where probabilities between pairs are based on
	 * a similarity function.
	 * @param entities list of entities
	 * @param inSimilarity similarity metric for pairs of patterns
	 * @param useLowerBound if true then entries in each row with probability below average are set to 0
	 * @return a similarity matrix
	 */
	public static Matrix createSimilarityMatrix(List<Entity> entities, EntitySimilarity inSimilarity, boolean useLowerBound)
	{
		// Create stochastic matrix using similarity metric
		int n = entities.size();
		Matrix stochasticMatrix = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(i, n).forEach(j -> // starting from i avoids calculating symmetric similarity twice
				{
					double sij = inSimilarity.computeSimilarity(entities.get(i), entities.get(j));
					stochasticMatrix.set(i, j, sij);
					stochasticMatrix.set(j, i, sij);
				}));

		// Accumulate scores for each entity
		List<Double> averages = IntStream.range(0, n)
				.mapToObj(i -> IntStream.range(0, n)
						.filter(j -> i != j) // exclude similarity to itself
						.mapToDouble(j -> stochasticMatrix.get(i, j))
						.average().orElse(0.0))
				.collect(Collectors.toList());

		if (useLowerBound)
		{
			// Set similarity of entity pairs below their respective average to 0
			IntStream.range(0, n).forEach(i ->
					IntStream.range(0, n)
							.filter(j -> i != j) // exclude similarity to itself
							.forEach(j ->
							{
								if (stochasticMatrix.get(i, j) < averages.get(i))
									stochasticMatrix.set(i, j, 0.0);
							}));
		}

		// Transform into probability (stochastic) matrix by normalizing similarity values against the sum of each row
		IntStream.range(0, n).forEach(i ->
		{
			double accum = Arrays.stream(stochasticMatrix.getArray()[i]).sum();
			IntStream.range(0, n).forEach(j -> stochasticMatrix.set(i, j, stochasticMatrix.get(i, j) / accum));
		});

		return stochasticMatrix;
	}


	/**
	 * Power iteration method to obtain a final stationary distribution of a Markov chain
	 *
	 * @param inInitialDistribution initial state of the chain
	 * @param inTransitionMatrix stochastic matrix of a Markov chain
	 * @return a stationary distribution of the chain
	 */
	public static Matrix run(Matrix inInitialDistribution, Matrix inTransitionMatrix, double inStopThreshold)
	{
		log.info("Starting power iteration");
		int numIterations = 0;
		Matrix oldDistribution = inInitialDistribution;
		Matrix newDistribution;
		double delta;
		do
		{
			// Core operation: transform distribution according to stochastic matrix
			newDistribution = oldDistribution.times(inTransitionMatrix);

			// Find out magnitude of change in distribution vector (delta)
			Matrix difference = newDistribution.minus(oldDistribution);
			OptionalDouble optDelta = DoubleStream.of(difference.getColumnPackedCopy())
					.map(Math::abs)
					.max();
			delta = (optDelta.isPresent() ? optDelta.getAsDouble() : 0);
			oldDistribution = newDistribution;
			if (++numIterations % 100 == 0)
			{
				log.info("..." + numIterations + " iterations");
			}
		}
		while (delta >= inStopThreshold); // stopping criterion: delta falls below a certain threshold

		log.info("Power iteration completed after " + numIterations + " iterations");
		return newDistribution;
	}
}
