package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.utils.DebugUtils.ThreadReporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.IntStream;

public class MatrixFactory
{
	private final static Logger log = LogManager.getLogger();

	/**
	 * Creates a square non-negative matrix to rank a set of items according to a bias and an edge weight function
	 * The matrix is row-stochastic. In has to be transposed into a column-stochastic matrix before used in a power
	 * iteration implementation of PageRank
	 *
	 * 1- Linear algebra explanation of Perron–Frobenius theorem:
	 * A positive matrix with exclusively positive real numbers as elements has a dominant real eigenvalue k (larger than
	 * any other eigenvalue) and a dominant strictly positive eigenvector v with eigenvalue k. There are no other non-negative
	 * eigenvectors except positive multiples of v -> other eigenvectors must contain negative or complex components
	 *
	 * Non-negative and irreducible matrices also satisfy theorem.
	 *
	 * 2- Probability theory explanation of the theorem:
	 * A stationary distribution π is a non-negative unit (summking 1) left eigenvector with eigenvalue 1 of a row
	 * stochastic (non-negative) matrix P. If the chain is irreducible and aperiodic, then there is a unique π for any
	 * starting distribution. This unique π describes the limit of all unit eigenvectors -the dominant eigenvector?
	 *
	 * A chain is irreducible if it is possible to get to any state from any state -the directed graph is strongly connected.
	 *
	 * An irreducible chain is aperiodic if at least one state is aperiodic (chain an return to the sate in any
	 * number of steps, without a period). A state is aperiodic if the chain can return to it in an even and in an uneven
	 * number of transitions.
	 *
	 * If a chain has more than one closed communicating class, each one will have its own unique stationary distribution.
	 *
	 * If @param make_positive is set to true then the underlying graph is turned into a complete graph, and the stochastic
	 * matrix made irreducible, aperiodic and positive.
	 * If @row_normalize is set to true, the matrix returned is row stochastic
	 *
	 * On normalization and other implementation details followed "CSL: PageRank Algorithm" by Nicole Beckage and Marina Kogan, 2013
	 * http://tuvalu.santafe.edu/~aaronc/courses/5454/csci5454_spring2013_CSL11.pdf
	 *
	 */
	public static double[][] createRankingMatrix(List<String> items, Function<String, Double> bias,
	                                             BiFunction<String, String, OptionalDouble> edge_weights,
	                                             boolean simmetric,
	                                             BiPredicate<String, String> filter,
	                                             double sim_threshold, double d)
	{
		if (d <= 0.0)
			throw new RuntimeException("Damping value must be greater than 0");

		log.info("Creating ranking matrix for " + items.size() + " items");
		int n = items.size();

		// Create strictly positive bias row vector with values in range [0..1]
		double[] L = createBiasVector(items, bias);

		// Create non-negative, row-normalized transition matrix
		double[][] X = createTransitionMatrix(items, edge_weights, simmetric, filter, sim_threshold, true);

		// Create ranking matrix
		double[][] R = new double[n][n];

		// Bias each row in X using bias L and factor d
		// Ru = d*Lu + (1.0-d)* SUM Xvu, for all v pointing to u
		// Rvu = d*Lu/n + (1.0-d)*Xvu, for all v
		IntStream.range(0, n).parallel().forEach(u -> { // row for u
			final double Lu = L[u]; // bias towards u
			final double bias_term = (d * Lu) / n; // distribute bias across row -> divided by n

			IntStream.range(0, n).forEach(v -> { // edge from u to u
				final double Xuv = X[u][v]; // weight of edge from u to v
				final double trans_term = (1.0 - d) * Xuv;
				final double Ruv = bias_term + trans_term;

				assert !Double.isNaN(Ruv);
				R[u][v] = Ruv;
			});
		});

		// rows in resulting matrix don't sum 1 anymore, but that's ok
		log.info("Matrix created");

		return R; // each row corresponds to an item to be ranked
	}

	// Creates strictly positive bias vector by applying the bias function to a set of items
	public static double[] createBiasVector(List<String> items, Function<String, Double> bias)
	{
		final double[] v = items.parallelStream()
				.mapToDouble(i ->
				{
					final Double w = bias.apply(i);
					assert w >= 0.0 && w <= 1.0;
					return w;
				})
				.toArray();
		final double[] v2 = rebase(v);

		if (Arrays.stream(v2).anyMatch(d -> d == 0.0))
			makePositive(v2);

		//normalize(v2); // by normalizing we make the magnitude of the bias term comparable to magnitude of the transition term
		return v2;
	}

	// Creates a symmetric, non-negative, row-normalized matrix
	public static double[][] createTransitionMatrix(List<String> items,
	                                                BiFunction<String, String, OptionalDouble> edge_weights,
	                                                boolean simmetric,
	                                                BiPredicate<String, String> filter,
	                                                double sim_threshold, boolean report_stats)
	{
		int n = items.size();
		double[][] m = new double[n][n];

		int total_pairs = ((n * n) / 2);
		AtomicLong counter_pairs = new AtomicLong(0);
		AtomicLong num_filtered = new AtomicLong(0);
		AtomicLong num_defined = new AtomicLong(0);
		AtomicLong num_negative = new AtomicLong(0);
		ThreadReporter reporter = new ThreadReporter(log);

		// Fill matrix as an adjacency matrix: each row corresponds to a node and contains weights of outgoing edges
		IntStream.range(0, n)
				.parallel() // each thread writes to separate portions (rows) of the matrix
				.peek(i -> reporter.report())
				.forEach(i -> IntStream.range(simmetric ? i : 0, n).forEach(j ->
				{
					double wij = 0.0;
					final String ei = items.get(i);
					final String ej = items.get(j);
					if (filter.test(ej, ei))
					{
						final OptionalDouble o = edge_weights.apply(ei, ej);
						o.ifPresent(d -> num_defined.incrementAndGet());
						wij = o.orElse(0.0);

						if (wij < 0.0)
						{
							num_negative.incrementAndGet();
							wij = 0.0;
						}
					}
					else
						num_filtered.incrementAndGet();

					if (wij < sim_threshold)
						wij = 0.0;

					m[i][j] = wij; // weight for edge going from i to j
					if (simmetric)
						m[j][i] = wij; // weight for edge going from j to i -> same if symmetric

					if (counter_pairs.incrementAndGet() % 100000 == 0)
						log.info(counter_pairs.get() + " out of " + total_pairs);
				}));

		// Remove sinks
		AtomicInteger num_sinks = new AtomicInteger();
		Arrays.stream(m).forEach(row -> {
			if (Arrays.stream(row).allMatch(d -> d == 0.0))
			{
				Arrays.fill(row, 1.0 / n);
				num_sinks.incrementAndGet();
			}
		});

		if (report_stats)
		{
			log.info( num_filtered.intValue() + " edge weights set to 0");
			log.info( num_sinks.get() + " sinks");
			log.info("Weights are negative for " + num_negative.get() + " edges out of " + total_pairs);
		}

		// we row-normalize becuase it is the rows that will be multiplied with the ranking vector
		rowNormalize(m);
		return m;
	}

	/**
	 * Smooths values to make each row positive.
	 * Required when bias vector isn't guaranteed to be strictly positive
	 */
	private static void makePositive(double[][] m)
	{
		int n = m.length;

		IntStream.range(0, n).parallel().forEach(i ->
		{
			double accum = Arrays.stream(m[i]).average().orElse(0.0);
			if (accum == 0.0)
				accum = 1.0;
			final double alpha = accum / 100.0; // pseudocount α for additive smoothing of rank values

			IntStream.range(0, n).forEach(j ->
			{
				if (m[i][j] == 0.0)
					m[i][j] = alpha;

				assert !Double.isNaN(m[i][j]) : "NaN value at " + i + "-" + j;
			});
		});
	}

	/**
	 * Smooths values to make each row positive.
	 * Required when bias vector isn't guaranteed to be strictly positive
	 */
	private static void makePositive(double[] v)
	{
		int n = v.length;

		double accum = Arrays.stream(v).average().orElse(0.0);
		if (accum == 0.0)
			accum = 1.0;
		final double alpha = accum / 100.0; // pseudocount α for additive smoothing of rank values

		IntStream.range(0, n).forEach(i ->
		{
			if (v[i] == 0.0)
				v[i] = alpha;

			assert !Double.isNaN(v[i]) : "NaN value at " + i;
		});
	}

	/**
	 * Row-normalizes a square matrix, e.g normalizes adjacency links, similarity values, etc.
	 */
	private static void rowNormalize(double[][] m)
	{
		// Normalize matrix with sum of each row (adjacent lists)
		int n = m.length;
		IntStream.range(0, n).parallel().forEach(i -> normalize(m[i]));

	}

	/**
	 * Normalize a one-dimensional vector.
	 * After calculation, checks that sum is 1 and there are no NaN values.
	 * @param v vector to normalize
	 */
	private static void normalize(double[] v)
	{
		int n = v.length;

		double accum = Arrays.stream(v).sum();
		if (accum != 0.0)
		{
			IntStream.range(0, n).forEach(i ->
			{
				v[i] = v[i] / accum;
				assert !Double.isNaN(v[i]) : "NaN value at " + i;
			});

			// This check accounts for precision of 64-bit doubles, as explained in
			// http://stackoverflow.com/questions/6837007/comparing-float-double-values-using-operator
			// http://www.ibm.com/developerworks/java/library/j-jtp0114/#N10255
			// and http://en.wikipedia.org/wiki/Machine_epsilon#Values_for_standard_hardware_floating_point_arithmetics
			assert (Math.abs(Arrays.stream(v).sum() - 1.0) < 2 * 2.22e-16);
		}
	}

	// rebases ranking values to [0..1] range
	public static double[] rebase(double[] ranking)
	{
		final DoubleSummaryStatistics stats = Arrays.stream(ranking)
				.summaryStatistics();
		if(stats.getMin() == stats.getMax())
			return ranking;

		//(((OldValue - OldMin) * (NewMax - NewMin)) / (OldMax - OldMin)) + NewMin
		Function<Double, Double> rebase = w -> (w - stats.getMin()) / (stats.getMax() - stats.getMin());
		return Arrays.stream(ranking)
				.map(rebase::apply)
				.toArray();
	}
}
