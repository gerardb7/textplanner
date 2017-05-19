package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
import edu.upf.taln.textplanning.StatsReporter;
import edu.upf.taln.textplanning.TextPlanner;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;

/**
 * Implementation of a power iteration algorithm for ranking with Markov chains.
 */
public class PowerIterationRanking
{
	private final static Logger log = LoggerFactory.getLogger(PowerIterationRanking.class);

	/**
	 * Creates joint stochastic matrix for a content graph based on a similarity function between node's entities
	 * biased against prior relevance and connections in the graph.
	 * Approach has similarities to Otterbacher et al. 2009 paper "Biased LexRank" (p.5)
	 *
	 * @param g content graph where nodes have entities associated to them
	 * @param inWeighting relevance weighting function for entities
	 * @param inSimilarity similarity metric for pairs of entities
	 * @param inOptions algorithm options
	 * @return a similarity matrix
	 */
	public static Matrix createRankingMatrix(SemanticGraph g, WeightingFunction inWeighting,
	                                         EntitySimilarity inSimilarity, TextPlanner.Options inOptions)
	{
		List<Node> nodes = new ArrayList<>(g.vertexSet());

		// Create relevance row vector by applying weighting function to node's entities
		int n = nodes.size();
		double[] b = nodes.stream()
				.map(Node::getEntity)
				.mapToDouble(inWeighting::weight)
				.map(w -> w < inOptions.relevanceLowerBound ? 0.0 : w) // apply lower bound
				.toArray();
		log.info(StatsReporter.getMatrixStats(new Matrix(b, 1)));
		double accum = Arrays.stream(b).sum();
		IntStream.range(0, n).forEach(i -> b[i] /= accum); // normalize vector with sum of row

		// Create an adjacency matrix for the content graph
		Matrix m_graph = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(i, n).forEach(j -> // starting from i avoids calculating symmetric co-occurrence twice
				{
				Node ni = nodes.get(i);
					Node nj = nodes.get(j);
					double sij = i == j ? 1.0 : (cooccur(g, ni, nj) ? 1.0 : 0.0);
					m_graph.set(i, j, sij);
					m_graph.set(j, i, sij);
				}));
		log.info("Content graph adjacency matrix:");
		log.info(StatsReporter.getMatrixStats(m_graph));
		normalize(m_graph);

		// Create similarity matrix by applying function to pairs of nodes
		Matrix m_sim = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(i, n).forEach(j -> // starting from i avoids calculating symmetric similarity twice
				{
					Entity ei = nodes.get(i).getEntity();
					Entity ej = nodes.get(j).getEntity();
					double sij = inSimilarity.computeSimilarity(ei, ej);
					if (sij < inOptions.simLowerBound) // apply lower bound
						sij = 0.0;
					m_sim.set(i, j, sij);
					m_sim.set(j, i, sij);
				}));
		log.info("Similarity matrix:");
		log.info(StatsReporter.getMatrixStats(m_sim));
		normalize(m_sim);

		// Bias each row using relevance bias with damping factor d1, and graph bias with damping factor d2:
		// Prob i->j = d1*rel_bias(j) + d2*graph_bias(i,j) (1-d1 - d2)*sim(i,j)
		// The resulting matrix is row-normalized because both b and m are normalized
		double d1 = inOptions.dampingRelevance;
		double d2 = inOptions.dampingSyntactic;
		IntStream.range(0, n).forEach(i ->
			IntStream.range(0, n).forEach(j -> {
				double brj = b[j]; // bias towards relevance of j
				double bsij = m_graph.get(i, j); // content graph bias
				double sij = m_sim.get(i, j); // sim of i and j
				double pij = d1 * brj + d2 * bsij + (1.0 - d1 - d2) * sij; // biased prob of going from i to j
				m_sim.set(i, j, pij);
			}));
		// No need to normalize again

		log.info("Biased similarity matrix:");
		log.info(StatsReporter.getMatrixStats(m_sim));

		return m_sim;
	}


	/**
	 * @return true if both nodes are connected through an edge in the content graph
	 */
	private static boolean cooccur(SemanticGraph g, Node n1, Node n2)
	{
		boolean isGovernorOf = g.incomingEdgesOf(n1).stream()
					.map(g::getEdgeSource)
					.anyMatch(n -> n == n2);

		boolean isDependentOf = g.outgoingEdgesOf(n1).stream()
				.map(g::getEdgeTarget)
				.anyMatch(n -> n == n2);

		return isGovernorOf || isDependentOf;
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
