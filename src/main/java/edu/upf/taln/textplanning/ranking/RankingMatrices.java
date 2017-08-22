package edu.upf.taln.textplanning.ranking;

import edu.upf.taln.textplanning.TextPlanner;
import edu.upf.taln.textplanning.similarity.CandidateSimilarity;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Creates stochastic matrices for addressing text planning task with PageRank algorithms.
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
 * πA=π, i.e. all page ranks must be equal to each other"
 * Taken from https://math.stackexchange.com/questions/55863/when-will-pagerank-fail
 *
 */
public class RankingMatrices
{
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	/**
	 * Creates a stochastic matrix to rank a set of entities
	 * @param entities entities to be ranked
	 * @param relevance relevance weighting function for entities
	 * @param similarity similarity metric for pairs of entities
	 * @param o algorithm options
	 * @return a stochastic matrix
	 */
	public static double[][] createEntityRankingMatrix(List<Entity> entities, WeightingFunction relevance,
	                                               EntitySimilarity similarity, TextPlanner.Options o)
	{
		int n = entities.size();

		// Create *strictly positive* relevance row vector by applying the weighting function to the set of entities
		double[] r = createRelevanceVector(entities, relevance, o.minRelevance, true);

		// Create *non-symmetric non-negative* similarity matrix from sim function and links in content graph
		double[][] m = createEntitySimilarityMatrix(entities, similarity, o.simLowerBound, true);

		// Bias each row in m using relevance bias r and damping factor d
		// Prob i->j = d*r(j) + (1.0-d)*m(i,j)
		// The resulting matrix is row-normalized because both r and m are normalized
		double d = o.dampingRelevance;
		double[][] ranking = new double[n][n];
		AtomicLong counter = new AtomicLong(0);
		long num_calculations2 = n * n;

		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j -> {
					double rj = r[j]; // bias towards relevance of j
					double sij = m[i][j]; // sim of i and j
					double vij = d * rj + (1.0 - d)*sij; // brj is positive, so pij also provided d > 0.0

					ranking[i][j] = vij;
					if (counter.incrementAndGet() % 10000000 == 0)
						log.info(counter.get() + " out of " + num_calculations2);
				}));

		// No need to normalize again
		return ranking;
	}

	/**
	 * Creates a stochastic matrix to rank a set of candidates (pairs of a mention and a candidate entity/sense).
	 * @param candidates structures (e.g. relations in a KB or extracted from text) containing candidates
	 * @param relevance relevance weighting function for entities
	 * @param similarity similarity metric for pairs of candidates
	 * @param o algorithm options
	 * @return a stochastic matrix
	 */
	public static double[][] createCandidateRankingMatrix(List<Candidate> candidates, WeightingFunction relevance,
	                                                  CandidateSimilarity similarity, TextPlanner.Options o)
	{
		int num_candidates = candidates.size();
		AtomicInteger index = new AtomicInteger(0);
		List<Entity> entities = candidates.stream()
				.map(Candidate::getEntity)
				.distinct()
				.collect(toList());
		Map<Entity, Integer> entities2Indexes = entities.stream()
				.collect(toMap(e -> e, e -> index.getAndIncrement()));

		// Get normalized *strictly positive* relevance row vector by applying the weighting function to the set of entities
		double[] r = createRelevanceVector(entities, relevance, o.minRelevance, true);

		// Get normalized *strictly positive* type row vector for the candidate set
		double[] t = createTypeVector(candidates, true, true);

		// Get normalized symmetric non-negative similarity matrix from sim function
		double[][] m = createCandidateSimilarityMatrix(candidates, similarity, o.simLowerBound, true);

		// Bias each row in m using relevance bias r and type bias t
		// Prob i->j = a*r(j) + b*t(j) + (1.0-a-b)*m(i,j)
		// The resulting matrix is row-normalized because both r and m are normalized
		log.info("Creating ranking matrix for " + num_candidates + " candidates");
		double a = o.dampingRelevance;
		double b = o.dampingType;
		AtomicLong counter = new AtomicLong(0);
		long num_calculations2 = num_candidates * num_candidates;

		double[][] ranking = new double[num_candidates][num_candidates];
		IntStream.range(0, num_candidates).forEach(i ->
				IntStream.range(0, num_candidates).forEach(j -> {
					Candidate c2 = candidates.get(j);
					int ej = entities2Indexes.get(c2.getEntity());

					double rj = r[ej]; // bias towards relevance of j
					double tj = t[j]; // bias towards type matching of j
					double sij = m[i][j]; // sim of i and j
					double vij = a * rj + b * tj + (1.0 - a - b)*sij; // brj is positive, so pij also provided d > 0.0

					ranking[i][j] = vij;
					if (counter.incrementAndGet() % 10000000 == 0)
						log.info(counter.get() + " out of " + num_calculations2);
				}));
		log.info("Done");

		// No need to normalize again
		return ranking;
	}

	// Creates normalized *strictly positive* relevance row vector by applying the weighting function to the set of entities
	public static double[] createRelevanceVector(List<Entity> entities, WeightingFunction relevance, double lower_bound, boolean normalize)
	{
		int num_entities = entities.size();
		double[] v = entities.stream()
				.map(Entity::getReference)
				.mapToDouble(relevance::weight)
				.map(w -> w = Math.max(lower_bound, (1.0/num_entities)*w)) // Laplace smoothing to avoid non-positive values
				.toArray();

		if (normalize)
		{
			double accum_r = Arrays.stream(v).sum();
			IntStream.range(0, num_entities).forEach(i -> v[i] /= accum_r); // normalize vector with sum of row
		}

		return v;
	}

	// Creates normalized *strictly positive* type row vector for the candidate set
	public static double[] createTypeVector(List<Candidate> candidates, boolean smooth, boolean normalize)
	{
		double[] v = candidates.stream()
				.mapToDouble(c -> {
					Candidate.Type mtype = c.getMention().getType();
					Candidate.Type etype = c.getEntity().getType();

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

	// Creates normalized symmetric non-negative similarity matrix from sim function
	private static double[][] createEntitySimilarityMatrix(List<Entity> entities, EntitySimilarity similarity, double lower_bound, boolean normalize)
	{
		int num_entities = entities.size();

		// Create symmetric non-negative similarity matrix from sim function
		log.info("Creating similarity matrix for " + num_entities + " entities");
		AtomicLong counter = new AtomicLong(0);
		long num_calculations1 = ((((long)num_entities * (long)num_entities) - num_entities)  / 2) + num_entities;
		double[][] m = new double[num_entities][num_entities];
		IntStream.range(0, num_entities).forEach(i ->
				IntStream.range(i, num_entities).forEach(j -> {
					double sim = 1.0;
					if (i != j)
					{
						Entity e1 = entities.get(i);
						Entity e2 = entities.get(j);
						sim = similarity.computeSimilarity(e1, e2).orElse(similarity.getAverageSimiliarity());
						if (sim < lower_bound)
							sim = 0.0;
					}

					m[i][j] = sim;
					m[j][i] = sim; // symmetric matrix

					if (counter.incrementAndGet() % 10000000 == 0)
						log.info(counter.get() + " out of " + num_calculations1);
				}));

		if (normalize)
			normalize(m);
		log.info("Done");

		return m;
	}

	// Creates normalized symmetric non-negative similarity matrix from sim function
	public static double[][] createCandidateSimilarityMatrix(List<Candidate> candidates, CandidateSimilarity similarity, double lower_bound, boolean normalize)
	{
		int num_candidates = candidates.size();

		// Create symmetric non-negative similarity matrix from sim function
		log.info("Creating similarity matrix for " + num_candidates + " candidates");
		AtomicLong counter = new AtomicLong(0);
		long num_calculations1 = ((((long)num_candidates * (long)num_candidates) - num_candidates)  / 2) + num_candidates;
		double[][] m = new double[num_candidates][num_candidates];
		IntStream.range(0, num_candidates).forEach(i ->
				IntStream.range(i, num_candidates).forEach(j -> {
					double sim = 1.0;
					if (i != j)
					{
						Candidate c1 = candidates.get(i);
						Candidate c2 = candidates.get(j);
						sim = similarity.computeSimilarity(c1, c2);
						if (sim < lower_bound)
							sim = 0.0;
					}

					m[i][j] = sim;
					m[j][i] = sim; // symmetric matrix

					if (counter.incrementAndGet() % 10000000 == 0)
						log.info(counter.get() + " out of " + num_calculations1);
				}));

		if (normalize)
			normalize(m);
		log.info("Done");

		return m;
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
