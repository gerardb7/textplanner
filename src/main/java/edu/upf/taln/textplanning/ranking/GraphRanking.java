package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.TextPlanner;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
	private final static Logger log = LogManager.getLogger(TextPlanner.class);


	public static void rankMeanings(Multimap<Meaning, Mention> meanings, WeightingFunction weighting, SimilarityFunction similarity,
	                                double meaning_similarity_threshold, double damping_factor_meanings)
	{
		Stopwatch timer = Stopwatch.createStarted();
		weighting.setContents(meanings);
		final List<String> labels = new ArrayList<>(meanings.keySet()).stream()
				.map(Meaning::toString)
				.collect(toList());
		final List<String> references = meanings.keySet().stream()
				.map(Meaning::getReference)
				.collect(Collectors.toList());

		double[][] rankingArrays = MatrixFactory.createMeaningRankingMatrix(references, weighting, similarity,
				meaning_similarity_threshold, damping_factor_meanings);
		Matrix rankingMatrix = new Matrix(rankingArrays);

		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix finalDistribution = alg.run(rankingMatrix, labels);
		double[] ranking = finalDistribution.getColumnPackedCopy();

		// Assign ranking values to meanings
		meanings.keySet().stream()
				.distinct()
				.forEach(m ->
				{
					int i = references.indexOf(m.getReference());
					m.setWeight(ranking[i]);
				});

		log.info("Meanings ranked in " + timer.stop());
	}

	public static void rankVariables(GlobalSemanticGraph graph, double minimum_meaning_ranking,
	                                    double damping_factor_variables)
	{
		Stopwatch timer = Stopwatch.createStarted();
		List<String> variables = graph.vertexSet().stream()
				.sorted(Comparator.naturalOrder())
				.collect(toList());

		double[][] rankingArrays = MatrixFactory.createVariableRankingMatrix(variables, graph, damping_factor_variables,
				minimum_meaning_ranking);
		Matrix rankingMatrix = new Matrix(rankingArrays);

		timer.reset(); timer.start();
		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix finalDistribution = alg.run(rankingMatrix, variables);
		double[] ranking = finalDistribution.getColumnPackedCopy();

		IntStream.range(0, variables.size()).boxed()
				.forEach(i -> graph.setWeight(variables.get(i),  ranking[i]));

		log.info("Variables ranked in " + timer.stop());
	}
}
