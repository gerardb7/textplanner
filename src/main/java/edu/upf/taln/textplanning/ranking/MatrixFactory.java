package edu.upf.taln.textplanning.ranking;

import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class MatrixFactory
{
	private final static Logger log = LogManager.getLogger(MatrixFactory.class);

	/**
	 * Creates a row-stochastic matrix to rank a set of meanings
	 */
	static double[][] createMeaningRankingMatrix(List<String> meanings, WeightingFunction weighting,
	                                             SimilarityFunction sim, double sim_threshold,
	                                             double d)
	{
		int n = meanings.size();

		// Create *strictly positive* bias row vector for the set of meanings
		double[] L = createMeaningsBiasVector(meanings, weighting, true);

		// Create *non-symmetric non-negative* similarity matrix
		double[][] X = createMeaningsSimilarityMatrix(meanings, sim, sim_threshold, true);

		// GraphRanking matrix: stochastic matrix describing probabilities of a random walk going from a type u to another
		// type v.
		double[][] R = new double[n][n];

		// Bias each row in X using bias L and d factor d
		// Ruv = d*Lu + (1.0-d)*Xuv
		AtomicLong counter = new AtomicLong(0);
		long num_calculations2 = n * n;

		IntStream.range(0, n).forEach(u ->
				IntStream.range(0, n).forEach(v -> {
					double Lu = L[u]; // bias towards relevance of v
					double Xuv = X[u][v]; // similarity
					double Ruv = d * Lu + (1.0 - d)*Xuv;

					R[u][v] = Ruv;
					if (counter.incrementAndGet() % 10000000 == 0)
						log.info(counter.get() + " out of " + num_calculations2);
				}));

		// R is row-normalized because both L and X are normalized
		return R;
	}

	/**
	 * Creates a row-stochastic matrix to rank a set of variables in a graph.
	 */
	static double[][] createVariableRankingMatrix(List<String> variables, GlobalSemanticGraph graph,
	                                              double d, double min_rank)
	{
		int n = variables.size();

		// Get normalized *strictly positive* bias row vector for the set of variables
		double[] T = createVariablesBiasVector(variables, graph, min_rank);

		// Get normalized symmetric non-negative adjacency matrix
		double[][] Y = createVariablesAdjacencyMatrix(variables, graph);

		// GraphRanking matrix: stochastic matrix describing probabilities of a random walk going from a variable u to another
		// variable v.
		double[][] R = new double[n][n];

		// Bias each row in Y using bias T and d factor d
		// Prob index->j = a*T(j) + b*t(j) + (1.0-a-b)*Y(index,j)
//		AtomicLong counter = new AtomicLong(0);
//		long num_calculations2 = n * n;
		IntStream.range(0, n).forEach(u ->
				IntStream.range(0, n).forEach(v -> {
					double tu = T[u]; // bias towards meanings of u
					double Yuv = Y[u][v]; // adjacency
					double Ruv = d * tu + (1.0 - d)*Yuv;

					R[u][v] = Ruv;
//					if (counter.incrementAndGet() % 10000000 == 0)
//						log.info(counter.create() + " out of " + num_calculations2);
				}));

		// R is row-normalized because both T and Y are normalized
		return R;
	}

	// Creates normalized *strictly positive* bias row vector by applying the weighting function to a set of meanings
	public static double[] createMeaningsBiasVector(List<String> meanings, WeightingFunction weighting,
	                                                boolean normalize)
	{
		int num_entities = meanings.size();
		double[] v = meanings.stream()
				.mapToDouble(weighting::weight)
				.toArray();


		if (normalize)
		{
			double accum = Arrays.stream(v).sum();
			IntStream.range(0, num_entities).forEach(i -> v[i] /= accum); // normalize vector with sum of row
		}

		return v;
	}


	// Creates normalized *strictly positive* bias row vector for variables from the rankings of their meanings
	private static double[] createVariablesBiasVector(List<String> variables, GlobalSemanticGraph graph, double min_rank)
	{
		int num_variables = variables.size();
		double[] v = variables.stream()
				.map(graph::getMeaning)
				.mapToDouble(Meaning::getWeight)
				.map(w -> w = Math.max(min_rank, (1.0/num_variables)*w)) // Laplace smoothing to avoid non-positive values
				.toArray();
		double accum = Arrays.stream(v).sum();
		IntStream.range(0, num_variables).forEach(i -> v[i] /= accum); // normalize vector with sum of row

		return v;
	}

	// Creates row-normalized symmetric non-negative similarity matrix
	public static double[][] createMeaningsSimilarityMatrix(List<String> meaning, SimilarityFunction sim,
	                                                        double sim_threshold, boolean normalize)
	{
		int n = meaning.size();

		// Create symmetric non-negative similarity matrix from sim function
//		AtomicLong counter = new AtomicLong(0);
//		long num_calculations1 = ((((long)n * (long)n) - n)  / 2) + n;
		double[][] m = new double[n][n];
		IntStream.range(0, n).forEach(i ->
				IntStream.range(i, n).forEach(j -> {
					double simij = 1.0;
					if (i != j)
					{
						String e1 = meaning.get(i);
						String e2 = meaning.get(j);
						simij = sim.computeSimilarity(e1, e2).orElse(sim.getAverageSimiliarity());
						if (simij < sim_threshold)
							simij = 0.0;
					}

					m[i][j] = simij;
					m[j][i] = simij; // symmetric matrix

//					if (counter.incrementAndGet() % 10000000 == 0)
//						log.info(counter.create() + " out of " + num_calculations1);
				}));

		if (normalize)
			normalize(m);

		return m;
	}

	// Creates row-normalized symmetric non-negative adjacency matrix
	private static double[][] createVariablesAdjacencyMatrix(List<String> variables, GlobalSemanticGraph graph)
	{
		int n = variables.size();

		// Create symmetric non-negative similarity matrix from sim function
//		AtomicLong counter = new AtomicLong(0);
//		long num_calculations1 = ((((long)n * (long)n) - n)  / 2) + n;
		double[][] m = new double[n][n];
		IntStream.range(0, n).forEach(i ->
				IntStream.range(i, n).forEach(j ->
				{
					String v1 = variables.get(i);
					String v2 = variables.get(j);
					double sim = graph.containsEdge(v1, v2) ? 1.0 : 0.0;

					m[i][j] = sim;
					m[j][i] = sim; // symmetric matrix

//					if (counter.incrementAndGet() % 10000000 == 0)
//						log.info(counter.create() + " out of " + num_calculations1);
				}));

		normalize(m);

		return m;
	}

	// Creates normalized *strictly positive* type row vector for the candidate set
	public static double[] createTypeVector(List<Candidate> candidates, boolean smooth, boolean normalize)
	{
		double[] v = candidates.stream()
				.mapToDouble(c -> {
					Candidate.Type mtype = c.getMention().getType();
					Candidate.Type etype = c.getMeaning().getType();

					// Mention and candidate type match and are different to
					boolean indicator = mtype != Candidate.Type.Other && mtype == etype;
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

		IntStream.range(0, r).forEach(i ->
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
