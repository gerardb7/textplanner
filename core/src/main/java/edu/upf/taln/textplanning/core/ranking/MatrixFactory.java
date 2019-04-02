package edu.upf.taln.textplanning.core.ranking;

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
	 * Creates a row-stochastic matrix to rank a set of items according to a bias and a similarity function
	 */
	public static double[][] createRankingMatrix(List<String> items, Function<String, Double> bias,
	                                             BiFunction<String, String, OptionalDouble> sim, boolean smooth,
	                                             BiPredicate<String, String> filter,
	                                             double sim_threshold, double d)
	{
		log.info("Creating ranking matrix for " + items.size() + " items");
		int n = items.size();

		// Create normalized *strictly positive* bias row vector for the set of items
		double[] L = createBiasVector(items, bias, smooth, true);

		// Create *symmetric non-negative* similarity matrix
		double[][] X = createSimilarityMatrix(items, sim, filter, sim_threshold,true,true, true);

		// Ranking matrix: stochastic matrix describing probabilities of a random walk going from an item u to another
		// item v.
		double[][] R = new double[n][n];

		// Bias each row in X using bias L and d factor d
		// Ruv = d*Lu + (1.0-d)*Xuv
		IntStream.range(0, n).parallel().forEach(u ->
				IntStream.range(0, n).forEach(v -> {
					double Lu = L[u]; // bias towards relevance of v
					double Xuv = X[u][v]; // similarity
					double Ruv = d * Lu + (1.0 - d)*Xuv;

					R[u][v] = Ruv;
				}));

		log.info("Matrix created");
		// R is row-normalized because both L and X are normalized
		return R;
	}

	// Creates *strictly positive* bias row vector by applying the bias function to a set of items
	public static double[] createBiasVector(List<String> items, Function<String, Double> bias,
	                                        boolean smooth, boolean normalize)
	{
		int n = items.size();
		double[] v = items.stream()
				.parallel()
				.mapToDouble(bias::apply)
				.toArray();

		if (smooth)
		{
			double alpha = Arrays.stream(v)
					.average().orElse(1.0) / 100; // pseudocount α for additive smoothing of rank values
			IntStream.range(0, n).forEach(i -> v[i] = v[i] + alpha);
		}

		if (normalize)
		{
			double accum = Arrays.stream(v).sum(); // includes pseudocounts if smoothed
			if (accum > 0.0)
				IntStream.range(0, n).forEach(i -> v[i] /= accum); // normalize vector with sum of row
			else
			{
				final double const_value = 1.0 / n;
				IntStream.range(0, n).forEach(i -> v[i] = const_value);
			}
		}

		return v;
	}

	// Creates a symmetric non-negative similarity matrix
	public static double[][] createSimilarityMatrix(List<String> items,
	                                                BiFunction<String, String, OptionalDouble> sim,
	                                                BiPredicate<String, String> filter,
	                                                double sim_threshold, boolean set_undefined_to_avg,
	                                                boolean normalize, boolean report_stats)
	{
		int n = items.size();
		double[][] m = new double[n][n];

		int total_pairs = ((n * n) / 2);
		AtomicLong counter_pairs = new AtomicLong(0);
		AtomicLong num_filtered = new AtomicLong(0);
		AtomicLong num_defined = new AtomicLong(0);
		AtomicLong num_negative = new AtomicLong(0);
		ThreadReporter reporter = new ThreadReporter(log);

		// Calculate similarity values
		IntStream.range(0, n)
				.parallel()
				.peek(i -> reporter.report())
				.forEach(i -> IntStream.range(i, n).forEach(j ->
				{
					double simij = 0.0;
					String e1 = items.get(i);
					String e2 = items.get(j);
					if (filter.test(e1, e2))
					{
						num_filtered.incrementAndGet();
						final OptionalDouble osim = sim.apply(e1, e2);
						if (osim.isPresent())
							num_defined.incrementAndGet();

						double sim_value = osim.orElse(0.0);
						if (sim_value < 0.0)
							num_negative.incrementAndGet();
						else
							simij = sim_value;
					}

					if (simij < sim_threshold)
						simij = 0.0;

					m[i][j] = simij;
					m[j][i] = simij; // symmetric matrix

					if (counter_pairs.incrementAndGet() % 100000 == 0)
						log.info(counter_pairs.get() + " out of " + total_pairs);
				}));

		// Non-negative matrix -> all rows must have at least one non-zero value
		if (Arrays.stream(m).anyMatch(row -> Arrays.stream(row).allMatch(v -> v == 0.0)))
			log.error("Similarity matrix has an all-zero row");

		if (set_undefined_to_avg)
		{
			// Set all 0-valued similarity values to the average of non-zero similarities
			final OptionalDouble average = Arrays.stream(m)
					.flatMapToDouble(Arrays::stream)
					.filter(v -> v >= 0.0)
					.average();
			if (average.isPresent())
			{
				final double avg = average.getAsDouble();
				IntStream.range(0, n).forEach(i ->
						IntStream.range(0, n).forEach(j ->
						{
							if (m[i][j] == 0.0)
								m[i][j] = avg;
						}));
			}
		}

		if (report_stats)
		{
			log.info("Similarity function invoked for " + num_filtered + " out of " + total_pairs);
			log.info("Similarity function defined for " + num_defined + " out of " + num_filtered);
			log.info("Similarity values are negative for " + num_negative.get() + " out of " + num_defined);
		}

		if (normalize)
			normalize(m);

		return m;
	}

	/**
	 * Row-normalizes a matrix
	 */
	private static void normalize(double[][] m)
	{
		// Normalize matrix with sum of each row (adjacent lists)
		int r = m.length;
		int c = m[0].length;

		IntStream.range(0, r).parallel().forEach(i ->
		{
			double accum = Arrays.stream(m[i]).sum();
			IntStream.range(0, c).forEach(j -> m[i][j] = m[i][j] / accum);
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
