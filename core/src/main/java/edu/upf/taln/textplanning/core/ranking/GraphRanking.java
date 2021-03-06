package edu.upf.taln.textplanning.core.ranking;

import Jama.Matrix;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.utils.DebugUtils;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

/**
 * GraphRanking class offers methods to rank items in semantic graphs, i.e. meanings and variables.
 * GraphRanking is addressed by creating stochastic matrices and using biased centering algorithms.
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
public class GraphRanking
{
	public static void rankMeanings(Collection<Candidate> candidates,
	                                Predicate<Candidate> candidates_filter,
	                                BiPredicate<String, String> meanings_filter,
	                                Function<String, Double> weighting,
	                                BiFunction<String, String, OptionalDouble> similarity,
	                                double meaning_similarity_threshold, double damping_factor_meanings)
	{
		final List<Candidate> filtered_candidates = candidates.stream()
				.filter(candidates_filter)
				.collect(toList());
		final List<String> references = filtered_candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());
		final List<String> labels = filtered_candidates.stream() // for debugging purposes
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.distinct()
				.collect(toList());

		if (references.isEmpty())
			return;

		double[][] ranking_arrays = MatrixFactory.createMeaningRankingMatrix(references, weighting, similarity,
				meanings_filter, meaning_similarity_threshold, damping_factor_meanings);
		Matrix ranking_matrix = new Matrix(ranking_arrays);

		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix final_distribution = alg.run(ranking_matrix, labels);
		double[] ranking = final_distribution.getColumnPackedCopy();

		// Assign ranking values to meanings
		filtered_candidates.forEach(m ->
		{
			int i = references.indexOf(m.getMeaning().getReference());
			m.setWeight(ranking[i]);
		});
	}

	public static void rankVariables(SemanticGraph graph, double damping_factor_variables)
	{
		List<String> variables = graph.vertexSet().stream()
				.sorted(Comparator.naturalOrder())
				.collect(toList());
		final List<String> labels = variables.stream() // for debugging purposes
				.map(v -> DebugUtils.createLabelForVariable(v, graph.getMeaning(v), graph.getMentions(v)))
				.collect(Collectors.toList());

		if (variables.isEmpty())
			return;

		double[][] rankingArrays = MatrixFactory.createVariableRankingMatrix(variables, graph, damping_factor_variables);
		Matrix rankingMatrix = new Matrix(rankingArrays);

		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix finalDistribution = alg.run(rankingMatrix, labels);
		double[] ranking = finalDistribution.getColumnPackedCopy();

		IntStream.range(0, variables.size()).boxed()
				.forEach(i -> graph.setWeight(variables.get(i),  ranking[i]));
	}
}
