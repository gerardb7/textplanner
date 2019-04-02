package edu.upf.taln.textplanning.core.ranking;

import Jama.Matrix;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Ranker class offers methods to rank items according to a bias function for single items and a similarity function
 * for pairs of items.
 * Ranking is addressed by creating stochastic matrices and using biased centering algorithms.
 *
 * The methods are based on personalized PageRank algorithms, as used for summarization in:
 *      Otterbacher et al. 2009 paper "Biased LexRank" (p.5)
 *
 * "a probability matrix should be a square matrix with (a) nonnegative entries and (b) each row sum equal to 1.
 * Given a probability matrix A, the page ranks π=(π1,π2,...,πN) are the equilibrium probabilities
 * such that πA=π. When all entries of A are positive, the Perron-Frobenius theorem guarantees that these
 * equilibrium probabilities are unique. If A is also symmetric, its column sums are equal to row sums, and hence
 * all equal to 1. Therefore, π=(1/N)(1,1,...,1) is the unique probability vector that satisfies
 * πA=π, index.e. all page ranks must be equal to each other"
 * Taken from https://math.stackexchange.com/questions/55863/when-will-pagerank-fail
 *
 */
public class Ranker
{
	public static double[] rank(List<String> items, List<String> labels, Function<String, Double> bias,
	                         BiFunction<String, String, OptionalDouble> sim, boolean smooth,
	                         BiPredicate<String, String> filter,
	                         double sim_threshold, double d)
	{
		double[][] rankingArrays = MatrixFactory.createRankingMatrix(items, bias, sim, smooth, filter, sim_threshold, d);
		Matrix rankingMatrix = new Matrix(rankingArrays);
		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix finalDistribution = alg.run(rankingMatrix, labels);
		return finalDistribution.getColumnPackedCopy();
	}
}
