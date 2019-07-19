package edu.upf.taln.textplanning.core.ranking;

import Jama.Matrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class JamaPowerIteration implements PowerIterationRanking
{
	private double stopping_threshold;
	private final static Logger log = LogManager.getLogger();

	public JamaPowerIteration(double stopping_threshold)
	{
		this.stopping_threshold = stopping_threshold;
	}

	/**
	 * Power iteration method to obtain a final stationary distribution of a Markov chain
	 * Implementation based on first method in http://introcs.cs.princeton.edu/java/95linear/MarkovChain.java.html
	 *
	 * @param a a transition stochastic matrix of a Markov chain
	 * @param labels labels identifying items in matrix, used for debugging purposes
	 * @return the stationary distribution of the chain
	 */
	public Matrix run(final Matrix a, List<String> labels)
	{
		// Check that a is a row-stochastic matrix
		assert a.getColumnDimension() == a.getRowDimension(); // Is it square?
		IntStream.range(0, a.getColumnDimension()).forEach(i -> IntStream.range(0, a.getRowDimension()).forEach(j ->
		{
			assert !Double.isNaN(a.getArray()[i][j]) : "NaN value at " + i + "-" + j;
		}));
		assert Arrays.stream(a.getArray()).allMatch(r -> Arrays.stream(r).allMatch(i -> i >= 0.0)); // Is it positive?
		//assert Arrays.stream(a.getArray()).allMatch(r -> Math.abs(Arrays.stream(r).sum() - 1.0) < 4*2.22e-16); // Is it row-normalized?


		// Create initial state as a column vector
		final int n = a.getRowDimension();
		Matrix v = new Matrix(n, 1, 1.0 / n); // v is the distribution vector that will be iteratively updated

		log.info("Starting power iteration");
		int numIterations = 0;
		double delta;
		final double corrected_stopping_threshold = stopping_threshold / n;
		do
		{
			// Core operation: transform distribution according to stochastic matrix
			Matrix tmp = a.times(v); // right-multiply row-stochastic square matrix and column vector, produces column vector where each vector item is updated using a row from the matrix
			// Normalize distribution to obtain eigenvalue
			tmp = tmp.times(1.0/tmp.norm1());

			// Find out magnitude of change in distribution vector (delta)
			Matrix difference = tmp.minus(v);
			delta = DoubleStream.of(difference.getColumnPackedCopy())
					.map(Math::abs)
					.max().orElse(0.0);
			v = tmp;
			if (++numIterations % 1000 == 0)
			{
				log.info("..." + numIterations + " iterations");
			}
		}
		while (delta >= corrected_stopping_threshold); // stopping criterion: delta falls below a certain threshold

		log.info("Power iteration completed after " + numIterations + " iterations");
//		log.debug("Ranking:\n" + DebugUtils.printRank(v.getColumnPackedCopy(), n, labels));
		return v;
	}


}
