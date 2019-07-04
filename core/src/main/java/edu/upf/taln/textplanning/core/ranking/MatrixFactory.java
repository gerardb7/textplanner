package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.utils.DebugUtils;
import edu.upf.taln.textplanning.core.utils.DebugUtils.ThreadReporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
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
	 */
	public static double[][] createRankingMatrix(List<String> items, List<String> labels, Function<String, Double> bias,
	                                             BiFunction<String, String, OptionalDouble> edge_weights,
	                                             boolean simmetric,
	                                             BiPredicate<String, String> filter,
	                                             double sim_threshold, double d,
	                                             boolean make_positive, boolean row_normalize)
	{
		log.info("Creating ranking matrix for " + items.size() + " items");
		int n = items.size();

		// Create non-negative bias row vector for the set of items
		double[] L = createBiasVector(items, bias);
		log.debug("Bias:\n" + DebugUtils.printRank(L, n, labels));


		// Create non-negative transition matrix
		double[][] X = createTransitionMatrix(items, edge_weights, simmetric, filter, sim_threshold, true);

		// Create ranking matrix:
		double[][] R = new double[n][n];

		// Bias each row in X using bias L and d factor d
		// Ru = d*Lu + (1.0-d)* SUM Xvu, for all v pointing to u
		// Rvu = d*Lu + (1.0-d)*Xvu
		IntStream.range(0, n).parallel().forEach(u -> // row for u
				IntStream.range(0, n).forEach(v -> { // from v to u
					double Lu = L[u]; // bias towards u
					double Xvu = X[u][v]; // similarity from v to u
					double Ruv = d * Lu + (1.0 - d)*Xvu;

					assert !Double.isNaN(Ruv);
					R[u][v] = Ruv;
				}));

		log.info("Matrix created"); // guaranteed to be non-negative

		if (make_positive)
			makePositive(R);

		if (row_normalize)
			rowNormalize(R);

		return R;
	}

	// Creates non-negative bias row vector by applying the bias function to a set of items
	public static double[] createBiasVector(List<String> items, Function<String, Double> bias)
	{
		return items.parallelStream()
				.mapToDouble(i ->
				{
					final Double w = bias.apply(i);
					if (w < 0.0)
					{
						// only positive bias_values are allowed!
						log.warn("Negative weight for item " + i);
						return 0.0;
					}

					return w;
				})
				.toArray();
	}

	// Creates a symmetric non-negative matrix
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

		// Fill matrix
		IntStream.range(0, n)
				.parallel() // each thread writes to separate portions (rows) of the matrix
				.peek(i -> reporter.report())
				.forEach(i -> IntStream.range(simmetric ? i : 0, n).forEach(j ->
				{
					double wji = 0.0;
					final String ej = items.get(j);
					final String ei = items.get(i);
					if (filter.test(ej, ei))
					{
						final OptionalDouble o = edge_weights.apply(ej, ei);
						o.ifPresent(d -> num_defined.incrementAndGet());
						wji = o.orElse(0.0);

						if (wji < 0.0)
						{
							num_negative.incrementAndGet();
							wji = 0.0;
						}
					}
					else
						num_filtered.incrementAndGet();

					if (wji < sim_threshold)
						wji = 0.0;

					m[i][j] = wji; // weight for edge going from j to i
					if (simmetric)
						m[j][i] = wji; // weight for edge going from i to j -> same if symmetric

					if (counter_pairs.incrementAndGet() % 100000 == 0)
						log.info(counter_pairs.get() + " out of " + total_pairs);
				}));

		if (Arrays.stream(m).anyMatch(row -> Arrays.stream(row).allMatch(v -> v == 0.0)))
			log.warn("Transition matrix has an all-zero row");

		if (report_stats)
		{
			log.info( num_filtered.intValue() + " edge weights set to 0");
			log.info("Weights are negative for " + num_negative.get() + " edges out of " + total_pairs);
		}

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
	 * Row-normalizes a square matrix, e.g normalizes adjacency links, similarity values, etc.
	 */
	private static void rowNormalize(double[][] m)
	{
		// Normalize matrix with sum of each row (adjacent lists)
		int n = m.length;

		IntStream.range(0, n).parallel().forEach(i ->
		{
			double accum = Arrays.stream(m[i]).sum();
			IntStream.range(0, n).forEach(j -> {
				m[i][j] = m[i][j] / accum;
				assert !Double.isNaN(m[i][j]) : "NaN value at " + i + "-" + j;
			});
		});

		// This check accounts for precision of 64-bit doubles, as explained in
		// http://stackoverflow.com/questions/6837007/comparing-float-double-values-using-operator
		// http://www.ibm.com/developerworks/java/library/j-jtp0114/#N10255
		// and http://en.wikipedia.org/wiki/Machine_epsilon#Values_for_standard_hardware_floating_point_arithmetics
		assert Arrays.stream(m).allMatch(row ->
		{
			double sum = Math.abs(Arrays.stream(row).sum() - 1.0);
			return sum < 2*2.22e-16; //2*2.22e-16
		});
	}
}
