package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.TextPlanner;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Comparator;
import java.util.List;
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
	private final WeightingFunction weighting;
	private final SimilarityFunction similarity;
	private final double min_meaning_weight;
	private final double meaning_similarity_threshold;
	private final double damping_factor_meanings;
	private final double minimum_meaning_ranking;
	private final double damping_factor_variables;
	private final PowerIterationRanking alg;
	private final static Logger log = LogManager.getLogger(TextPlanner.class);

	public GraphRanking(WeightingFunction weighting, SimilarityFunction similarity, double min_meaning_weight,
	                    double meaning_similarity_threshold, double damping_factor_meanings,
	                    double minimum_meaning_ranking, double damping_factor_variables)
	{
		this.weighting = weighting;
		this.similarity = similarity;
		this.min_meaning_weight = min_meaning_weight;
		this.meaning_similarity_threshold = meaning_similarity_threshold;
		this.damping_factor_meanings = damping_factor_meanings;
		this.minimum_meaning_ranking = minimum_meaning_ranking;
		this.damping_factor_variables = damping_factor_variables;
		this.alg = new JamaPowerIteration();
	}

	public void rankMeanings(GraphList graphs)
	{
		Stopwatch timer = Stopwatch.createStarted();
		this.weighting.setContents(graphs);

		List<String> meanings = graphs.getCandidates().stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(toList());
		double[][] rankingArrays = MatrixFactory.createMeaningRankingMatrix(meanings, weighting, min_meaning_weight, similarity,
				meaning_similarity_threshold, damping_factor_meanings);
		Matrix rankingMatrix = new Matrix(rankingArrays);

		Matrix finalDistribution = alg.run(rankingMatrix);
		double[] ranking = finalDistribution.getColumnPackedCopy();

		// Assign ranking values to meanings
		graphs.getCandidates().stream()
				.map(Candidate::getMeaning)
				.distinct()
				.forEach(m ->
				{
					int i = meanings.indexOf(m.getReference());
					m.setWeight(ranking[i]);
				});

		log.info("Meanings ranked in " + timer.stop());
	}

	public void rankVariables(GlobalSemanticGraph graph)
	{
		Stopwatch timer = Stopwatch.createStarted();
		List<String> variables = graph.vertexSet().stream()
				.sorted(Comparator.naturalOrder())
				.collect(toList());
		double[][] rankingArrays = MatrixFactory.createVariableRankingMatrix(variables, graph, damping_factor_variables,
				minimum_meaning_ranking);
		Matrix rankingMatrix = new Matrix(rankingArrays);

		timer.reset(); timer.start();
		Matrix finalDistribution = alg.run(rankingMatrix);
		double[] ranking = finalDistribution.getColumnPackedCopy();

		IntStream.range(0, variables.size()).boxed()
				.forEach(i -> graph.setWeight(variables.get(i),  ranking[i]));

		log.info("Variables ranked in " + timer.stop());
	}


}
