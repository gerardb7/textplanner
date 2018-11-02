package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;

import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.utils.DebugUtils;
import edu.upf.taln.textplanning.weighting.WeightingFunction;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

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
	// Accept references which are not candidates of exactly the same set of mentions
	public static class DifferentMentions implements BiPredicate<String, String>
	{
		private final Collection<Candidate> candidates;
		public DifferentMentions(Collection<Candidate> candidates)	{ this.candidates = candidates; }

		@Override
		public boolean test(String r1, String r2)
		{
			final Set<Mention> mentions_r1 = candidates.stream()
					.filter(c -> c.getMeaning().getReference().equals(r1))
					.map(Candidate::getMention)
					.collect(Collectors.toSet());
			final Set<Mention> mentions_r2 = candidates.stream()
					.filter(c -> c.getMeaning().getReference().equals(r2))
					.map(Candidate::getMention)
					.collect(Collectors.toSet());
			return !mentions_r1.equals(mentions_r2); // should differ in at least one mention
		}
	}

	public static void rankMeanings(Collection<Candidate> candidates, WeightingFunction weighting, SimilarityFunction similarity,
	                                double meaning_similarity_threshold, double damping_factor_meanings)
	{
		weighting.setContents(candidates);
		final List<String> references = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());
		final List<String> labels = candidates.stream() // for debugging purposes
				.map(Candidate::getMeaning)
				.map(Meaning::toString)
				.distinct()
				.collect(toList());

		if (references.isEmpty())
			return;

		DifferentMentions filter = new DifferentMentions(candidates);
		double[][] rankingArrays = MatrixFactory.createMeaningRankingMatrix(references, weighting, similarity, filter,
				meaning_similarity_threshold, damping_factor_meanings);
		Matrix rankingMatrix = new Matrix(rankingArrays);

		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix finalDistribution = alg.run(rankingMatrix, labels);
		double[] ranking = finalDistribution.getColumnPackedCopy();

		// Assign ranking values to meanings
		candidates.stream()
				.map(Candidate::getMeaning)
				.distinct()
				.forEach(m ->
				{
					int i = references.indexOf(m.getReference());
					m.setWeight(ranking[i]);
				});
	}

	public static void rankVariables(GlobalSemanticGraph graph, double damping_factor_variables)
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
