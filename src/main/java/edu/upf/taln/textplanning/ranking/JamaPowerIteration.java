package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.DoubleStream;

public class JamaPowerIteration implements PowerIterationRanking
{
	private final static Logger log = LogManager.getLogger(JamaPowerIteration.class);

	/**
	 * Power iteration method to obtain a final stationary distribution of a Markov chain
	 * Implementation based on first method in http://introcs.cs.princeton.edu/java/95linear/MarkovChain.java.html
	 *
	 * @param a a transition stochastic matrix of a Markov chain
	 * @param labels labels identifying items in matrix, used for debugging purposes
	 * @return the stationary distribution of the chain
	 */
	public Matrix run(Matrix a, List<String> labels)
	{
		// Check that a is a row-stochastic matrix
		assert a.getColumnDimension() == a.getRowDimension(); // Is it square?
		assert Arrays.stream(a.getArray()).allMatch(r -> Arrays.stream(r).allMatch(i -> i >= 0.0)); // Is it positive?
		assert Arrays.stream(a.getArray()).allMatch(r -> Math.abs(Arrays.stream(r).sum() - 1.0) < 2*2.22e-16); // Is it row-normalized?

		// Change matrix from row-normalized to column normalized
		// Turns rows into columns so that multiplication with column vector produces probs of reaching states
		a = a.transpose(); // See http://en.wikipedia.org/wiki/Matrix_multiplication#Square_matrix_and_column_vector

		// Create initial state as a column vector
		final int n = a.getColumnDimension();
		final double e = 1.0/(n*1000); // set stopping threshold
		Matrix v = new Matrix(n, 1, 1.0 / n); // v is the distribution vector that will create iteratively updated

		log.debug("Starting power iteration");
		int numIterations = 0;
		double delta;
		do
		{
			//debug_rank(v, n, labels);

			// Core operation: transform distribution according to stochastic matrix
			Matrix tmp = a.times(v); // right-multiply column-stochastic square matrix and column vector, produces column vector
			// Normalize distribution to obtain eigenvalue
			tmp = tmp.times(1.0/tmp.norm1());

			// Find out magnitude of change in distribution vector (delta)
			Matrix difference = tmp.minus(v);
			delta = DoubleStream.of(difference.getColumnPackedCopy())
					.map(Math::abs)
					.max().orElse(0.0);
			v = tmp;
			if (++numIterations % 100 == 0)
			{
				log.debug("..." + numIterations + " iterations");
			}
		}
		while (delta >= e); // stopping criterion: delta falls below a certain threshold

		debug_rank(v, n, labels);
		log.info("Power iteration completed after " + numIterations + " iterations");
		return v;
	}

	private static void debug_rank(Matrix v, int n, List<String> labels)
	{
		final List<Pair<String, Double>> sorted_items = new ArrayList<>();
		for (int i =0; i < n; ++i)
		{
			final String l = labels.get(i);
			final double v_i = v.getColumnPackedCopy()[i];
			sorted_items.add(Pair.of(l, v_i));
		}
		sorted_items.sort(Comparator.comparingDouble(Pair<String, Double>::getRight).reversed());
		log.debug(sorted_items.subList(0, 100));
	}
}
