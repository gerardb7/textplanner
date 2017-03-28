package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Implementation of a power iteration algorithm for ranking with Markov chains.
 */
public class PowerIterationRanking
{
	private final static Logger log = LoggerFactory.getLogger(PowerIterationRanking.class);

	/**
	 * Creates joint stochastic matrix for relevance bias and similarity, as described in Otterbacher et al. 2009 paper
	 * "Biased LexRank" (p.5)
	 *
	 * @param entities list of entities
	 * @param inSimilarity similarity metric for pairs of patterns
	 * @param inOptions algorithm options
	 * @return a similarity matrix
	 */
	public static Matrix createRankingMatrix(List<Entity> entities, WeightingFunction inWeighting,
	                                            EntitySimilarity inSimilarity, TextPlanner.Options inOptions)
	{
		// Create relevance row vector by applying weighting function to entities
		int n = entities.size();
		double[] b = entities.stream()
				.mapToDouble(inWeighting::weight)
				.map(w -> w < inOptions.relevanceLowerBound ? 0.0 : w) // apply lower bound
				.toArray();
		log.info(StatsReporter.getMatrixStats(new Matrix(b, 1)));
		double acum = Arrays.stream(b).sum();
		IntStream.range(0, n).forEach(i -> b[i] /= acum); // normalize vector with sum of row

		// Create similarity matrix by applying function to pairs of entities
		Matrix m = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(i, n).forEach(j -> // starting from i avoids calculating symmetric similarity twice
				{
					double sij = inSimilarity.computeSimilarity(entities.get(i), entities.get(j));
					if (sij < inOptions.simLowerBound) // apply lower bound
						sij = 0.0;
					m.set(i, j, sij);
					m.set(j, i, sij);
				}));
		log.info("Similarity matrix:");
		log.info(StatsReporter.getMatrixStats(m));
		normalize(m);

		// Bias each row using relevance b and damping factor d: d*bias + (1-d)*sim
		// The resulting matrix is row-normalized because both b and m are normalized
		double d = inOptions.dampingFactor;
		IntStream.range(0, n).forEach(i ->
			IntStream.range(0, n).forEach(j ->	m.set(i, j, d *b[i] + (1.0 - d)*m.get(i, j))));
		normalize(m); // Normalize matrix again

		log.info("Biased matrix:");
		log.info(StatsReporter.getMatrixStats(m));

		return m;
	}


	/**
	 * Power iteration method to obtain a final stationary distribution of a Markov chain
	 * Implementation based on first method in http://introcs.cs.princeton.edu/java/95linear/MarkovChain.java.html
	 *
	 * @param a a transition stochastic matrix of a Markov chain
	 * @param e error used as a stopping threshold for the algorithm
	 * @return the stationary distribution of the chain
	 */
	public static Matrix run(Matrix a, double e)
	{
		// Check that a is a stochastic matrix
		assert a.getColumnDimension() == a.getRowDimension(); // Is it square?
		assert Arrays.stream(a.getArray()).allMatch(r -> Arrays.stream(r).allMatch(i -> i >= 0.0)); // Is it positive?
		assert Arrays.stream(a.getArray()).allMatch(r -> Math.abs(Arrays.stream(r).sum() - 1.0) < 2*2.22e-16); // Is it row-normalized?

		// Turn the rows into columns so that multiplication with column vector produces probs of reaching states
		a = a.transpose(); // See http://en.wikipedia.org/wiki/Matrix_multiplication#Square_matrix_and_column_vector

		// Create initial state as 1-column vector
		int n = a.getColumnDimension();
		Matrix v = new Matrix(n, 1, 1.0 / n); // v is the distribution vector that will get iteratively updated

		log.info("Starting power iteration");
		int numIterations = 0;
		double delta;
		do
		{
			// Core operation: transform distribution according to stochastic matrix
			Matrix tmp = a.times(v); // product of square matrix and column vector produces column vector
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
				log.info("..." + numIterations + " iterations");
			}
		}
		while (delta >= e); // stopping criterion: delta falls below a certain threshold

		log.info("Power iteration completed after " + numIterations + " iterations");
		return v;
	}

	/**
	 * Row-normalizes a matrix
	 */
	private static void normalize(Matrix m)
	{
		// Normalize matrix with sum of each row (adjacent lists)
		int r = m.getRowDimension();
		int c = m.getColumnDimension();

		IntStream.range(0, r).forEach(i ->
		{
			double accum = Arrays.stream(m.getArray()[i]).sum();
			IntStream.range(0, c).forEach(j -> m.set(i, j, m.get(i, j) / accum));
		});

		// This check accounts for precision of 64-bit doubles, as explained in
		// http://www.ibm.com/developerworks/java/library/j-jtp0114/#N10255
		// and http://en.wikipedia.org/wiki/Machine_epsilon#Values_for_standard_hardware_floating_point_arithmetics
		assert Arrays.stream(m.getArray()).allMatch(row ->
		{
			double sum = Math.abs(Arrays.stream(row).sum() - 1.0);
			return sum < 2*2.22e-16; //2*2.22e-16
		});
	}
}
