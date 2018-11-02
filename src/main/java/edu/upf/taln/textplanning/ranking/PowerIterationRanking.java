package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.DoubleStream;

/**
 * Interface for implementations of a power iteration algorithm for ranking with Markov chains.
 *
 * Can be applied to two types of stochastic matrix for a set of entities from transition probabilities derived from
 * a similarity function between candidates senses biased against a relevance distribution and a type function.
 * This matrix is used to solve personalized PageRank algorithms, as used for summarization in:
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
@FunctionalInterface
interface PowerIterationRanking
{
	Matrix run(Matrix a, List<String> labels);
}
