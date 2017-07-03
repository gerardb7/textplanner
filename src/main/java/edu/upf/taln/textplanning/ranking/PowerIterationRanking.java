package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
import edu.upf.taln.textplanning.StatsReporter;
import edu.upf.taln.textplanning.TextPlanner;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Implementation of a power iteration algorithm for ranking with Markov chains.
 */
public class PowerIterationRanking
{
	private final static Logger log = LoggerFactory.getLogger(PowerIterationRanking.class);

	/**
	 * Creates joint stochastic matrix for a set of entities based on a similarity function between node's entities
	 * biased against prior relevance and connections in the graph.
	 * Approach has similarities to Otterbacher et al. 2009 paper "Biased LexRank" (p.5)
	 *
	 *
	 * "a probability matrix should be a square matrix with (a) nonnegative entries and (b) each row sum equal to 1.
	 * Given a probability matrix A, the page ranks π=(π1,π2,...,πN) are the equilibrium probabilities
	 * such that πA=π. When all entries of A are positive, the Perron-Frobenius theorem guarantees that these
	 * equilibrium probabilities are unique. If A is also symmetric, its column sums are equal to row sums, and hence
	 * all equal to 1. Therefore, π=(1/N)(1,1,...,1) is the unique probability vector that satisfies
	 * πA=π, i.e. all page ranks must be equal to each other"
	 * Taken from https://math.stackexchange.com/questions/55863/when-will-pagerank-fail
	 *
	 * @param entities items to rank (word senses, individuals in a KB, etc)
	 * @param structures structures (e.g. relations in a KB or extracted from text) containing references to the entities
	 * @param inWeighting relevance weighting function for entities
	 * @param inSimilarity similarity metric for pairs of entities
	 * @param inOptions algorithm options
	 * @return a similarity matrix
	 */
	public static Matrix createRankingMatrix(List<String> entities, Set<SemanticGraph> structures,
	                                         WeightingFunction inWeighting,
	                                         ItemSimilarity inSimilarity, TextPlanner.Options inOptions)
	{
		// Create *strictly positive* relevance row vector by applying weighting function to node's entities
		int n = entities.size();
		double[] b = entities.stream()
				.mapToDouble(inWeighting::weight)
				.map(w -> w = Math.max(inOptions.minRelevance, (1.0/n)*w)) // Laplace smoothing to avoid non-positive values
				.toArray();
		log.debug("Relevance matrix:");
		log.debug(StatsReporter.getMatrixStats(new Matrix(b, 1)));
		double accum = Arrays.stream(b).sum();
		IntStream.range(0, n).forEach(i -> b[i] /= accum); // normalize vector with sum of row

		// Create *non-symmetric non-negative* similarity matrix from sim function and links in content graph
		Matrix m = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					String ei = entities.get(i);
					String ej = entities.get(j);
					double sij = isGoverned(structures, entities.get(i), entities.get(j)) ? 1.0
							: inSimilarity.computeSimilarity(ei, ej);
					// apply lower bound
					if (sij < inOptions.simLowerBound)
						sij = 0.0;
					m.set(i, j, sij);
				}));
		log.debug("Similarity matrix:");
		log.debug(StatsReporter.getMatrixStats(m));
		normalize(m);

		// Bias each row in m using relevance bias b and damping factor d
		// Prob i->j = d*b(j) + (1.0-d)*m(i,j)
		// The resulting matrix is row-normalized because both b and m are normalized
		double d = inOptions.dampingRelevance;
		Matrix bm = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
			IntStream.range(0, n).forEach(j -> {
				double brj = b[j]; // bias towards relevance of j
				double sij = m.get(i, j); // sim of i and j
				double pij = d * brj + (1.0 - d)*sij; // brj is positive, so pij also provided d > 0.0
				bm.set(i, j, pij);
			}));
		// No need to normalize again

		log.debug("Biased similarity matrix:");
		log.debug(StatsReporter.getMatrixStats(bm));

		return bm;
	}

	/**
	 * @return true if an edge n1->n2 exists in any of the input structures such that n1 mentions e1 and n2 mentions e2
	 */
	private static boolean isGoverned(Set<SemanticGraph> structures, String e1, String e2)
	{
		return structures.stream()
				.anyMatch(s -> s.vertexSet().stream()
						.filter(v -> v.mentions(e1))
						.map(s::outgoingEdgesOf)
						.flatMap(Set::stream)
						.map(s::getEdgeTarget)
						.anyMatch(v -> v.mentions(e2)));
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

		log.debug("Starting power iteration");
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
				log.debug("..." + numIterations + " iterations");
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
		// http://stackoverflow.com/questions/6837007/comparing-float-double-values-using-operator
		// http://www.ibm.com/developerworks/java/library/j-jtp0114/#N10255
		// and http://en.wikipedia.org/wiki/Machine_epsilon#Values_for_standard_hardware_floating_point_arithmetics
		assert Arrays.stream(m.getArray()).allMatch(row ->
		{
			double sum = Math.abs(Arrays.stream(row).sum() - 1.0);
			return sum < 2*2.22e-16; //2*2.22e-16
		});
	}
}
