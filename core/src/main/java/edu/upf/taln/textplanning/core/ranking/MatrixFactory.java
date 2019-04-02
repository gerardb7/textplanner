package edu.upf.taln.textplanning.core.ranking;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
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
	 * Creates a row-stochastic matrix to rank a set of meanings
	 */
	public static double[][] createMeaningRankingMatrix(List<String> meanings, Function<String, Double> weighting,
	                                                    BiFunction<String, String, OptionalDouble> sim,
	                                                    BiPredicate<String, String> filter,
	                                                    double sim_threshold, double d)
	{
		log.info("Creating ranking matrix for " + meanings.size() + " meanings");
		int n = meanings.size();

		// Create *strictly positive* bias row vector for the set of meanings
		double[] L = createMeaningsBiasVector(meanings, weighting, true);

		// Create *symmetric non-negative* similarity matrix
		double[][] X = createMeaningsSimilarityMatrix(meanings, sim, filter, sim_threshold,
				true,true, true);

		// GraphRanking matrix: stochastic matrix describing probabilities of a random walk going from a type u to another
		// type v.
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
		log.info("Meanings matrix created");

		// R is row-normalized because both L and X are normalized
		return R;
	}

	/**
	 * Creates a row-stochastic matrix to rank a set of variables in a graph.
	 */
	public static double[][] createVariableRankingMatrix(List<String> variables, SemanticGraph graph, double d)
	{
		log.info("Creating ranking matrix for " + variables.size() + " variables");
		int n = variables.size();

		// Get normalized *strictly positive* bias row vector for the set of variables
		double[] T = createVariablesBiasVector(variables, graph);

		// Get normalized *symmetric non-negative* adjacency matrix
		double[][] Y = createVariablesAdjacencyMatrix(variables, graph);

		// GraphRanking matrix: stochastic matrix describing probabilities of a random walk going from a variable u to another
		// variable v.
		double[][] R = new double[n][n];

		// Bias each row in Y using bias T and d factor d
		// Prob index->j = a*T(j) + b*t(j) + (1.0-a-b)*Y(index,j)
		IntStream.range(0, n).parallel().forEach(u ->
				IntStream.range(0, n).forEach(v -> {
					double tu = T[u]; // bias towards meanings of u
					double Yuv = Y[u][v]; // adjacency
					double Ruv = d * tu + (1.0 - d)*Yuv;

					R[u][v] = Ruv;
				}));

		log.info("Variables matrix created");
		// R is row-normalized because both T and Y are normalized
		return R;
	}

	// Creates normalized *strictly positive* bias row vector by applying the weighting function to a set of meanings
	public static double[] createMeaningsBiasVector(List<String> meanings, Function<String, Double> weighting,
	                                                boolean normalize)
	{
		int num_entities = meanings.size();
		double[] v = meanings.stream()
				.parallel()
				.mapToDouble(weighting::apply)
				.toArray();

		if (normalize)
		{
			double accum = Arrays.stream(v).sum();
			if (accum > 0.0)
				IntStream.range(0, num_entities).forEach(i -> v[i] /= accum); // normalize vector with sum of row
			else
			{
				final double const_value = 1.0 / num_entities;
				IntStream.range(0, num_entities).forEach(i -> v[i] = const_value);
			}
		}

		return v;
	}

	// Creates normalized *strictly positive* bias row vector for variables from the rankings of their meanings
	private static double[] createVariablesBiasVector(List<String> variables, SemanticGraph graph)
	{
		final double[] weights = variables.stream()
				.parallel()
				.mapToDouble(graph::getWeight)
				.toArray();

		double alpha = Arrays.stream(weights)
				.average().orElse(1.0) / 100; // pseudocount α for additive smoothing of meaning rank values

		int num_variables = variables.size();
		double[] v = Arrays.stream(weights)
				.map(w -> w = w + alpha) // Laplace smoothing to avoid zero values
				.toArray();
		double accum = Arrays.stream(v).sum(); // includes pseudocounts
		IntStream.range(0, num_variables).forEach(i -> v[i] /= accum); // normalize vector with sum of row

		return v;
	}

	// Creates row-normalized symmetric non-negative similarity matrix
	public static double[][] createMeaningsSimilarityMatrix(List<String> meanings,
	                                                        BiFunction<String, String, OptionalDouble> sim,
	                                                        BiPredicate<String, String> filter,
	                                                        double sim_threshold, boolean set_undefined_to_avg,
	                                                        boolean normalize, boolean report_stats)
	{
		int n = meanings.size();
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
					if (i == j)
						simij = 1.0;
					else
					{
						String e1 = meanings.get(i);
						String e2 = meanings.get(j);

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
					}

					if (simij < sim_threshold)
						simij = 0.0;

					m[i][j] = simij;
					m[j][i] = simij; // symmetric matrix

					if (counter_pairs.incrementAndGet() % 100000 == 0)
						log.info(counter_pairs.get() + " out of " + total_pairs);
				}));

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

	// Creates row-normalized symmetric non-negative adjacency matrix
	private static double[][] createVariablesAdjacencyMatrix(List<String> variables, SemanticGraph graph)
	{
		int n = variables.size();
		ThreadReporter reporter = new ThreadReporter(log);

		// Create symmetric non-negative similarity matrix from sim function
//		AtomicLong counter = new AtomicLong(0);
//		long num_calculations1 = ((((long)n * (long)n) - n)  / 2) + n;
		double[][] m = new double[n][n];
		IntStream.range(0, n)
				.parallel()
				.peek(i -> reporter.report())
				.forEach(i -> IntStream.range(i, n)
						.filter(j -> i != j)
						.forEach(j ->
						{
							String v1 = variables.get(i);
							String v2 = variables.get(j);
							double sim = graph.containsEdge(v1, v2) || graph.containsEdge(v2, v1) ? 1.0 : 0.0;

							m[i][j] = sim;
							m[j][i] = sim; // symmetric matrix
						}));

		// All vertices must be touched by at least one edge -> all rows must have at least one non-zero value
		if (Arrays.stream(m).anyMatch(row -> Arrays.stream(row).noneMatch(v -> v != 0.0)))
			log.error("Adjacency matrix has all-zero row");

		normalize(m);

		return m;
	}

	// Creates normalized *strictly positive* type row vector for the candidate set
	public static double[] createTypeVector(List<Candidate> candidates, boolean smooth, boolean normalize)
	{
		ThreadReporter reporter = new ThreadReporter(log);

		double[] v = candidates.stream()
				.parallel()
				.peek(c -> reporter.report())
				.mapToDouble(c -> {
					String mtype = c.getMention().getType();
					String etype = c.getMeaning().getType();

					// Mention and candidate type match and are different to
					boolean indicator = !mtype.isEmpty() && mtype.equals(etype);
					if (smooth)
						return indicator ? 0.99 : 0.01; // avoid zeros
					else
						return indicator ? 1.0 : 0.0;
				})
				.toArray();

		if (normalize)
		{
			double accum_t = Arrays.stream(v).sum();
			IntStream.range(0, candidates.size()).forEach(i -> v[i] /= accum_t); // normalize vector with sum of rows
		}

		return v;
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
