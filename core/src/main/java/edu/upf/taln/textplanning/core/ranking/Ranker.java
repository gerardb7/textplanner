package edu.upf.taln.textplanning.core.ranking;

import Jama.Matrix;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Rank sitems according to a bias function for single items and a weight function for pairs of items.
 * Ranking is addressed by creating a biased stochastic matrix and applying a centering algorithm.
 *
 * The ranking is based on personalized PageRank algorithms, such as the one used for summarization in:
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
 * Also look at http://tuvalu.santafe.edu/~aaronc/courses/5454/csci5454_spring2013_CSL11.pdf
 *
 */
public class Ranker
{
	public static double[] rank(List<String> items, List<String> labels, Function<String, Double> bias,
	                         BiFunction<String, String, OptionalDouble> edge_weights, boolean symmetric,
	                         BiPredicate<String, String> filter,
	                         double sim_threshold, double d, double stopping_threshold)
	{
		double[][] rankingArrays = MatrixFactory.createRankingMatrix(items, labels, bias, edge_weights, symmetric, filter,
				sim_threshold, d);
		Matrix rankingMatrix = new Matrix(rankingArrays);
		JamaPowerIteration alg = new JamaPowerIteration(stopping_threshold);
		Matrix finalDistribution = alg.run(rankingMatrix, labels);
		return finalDistribution.getColumnPackedCopy();
	}
}
