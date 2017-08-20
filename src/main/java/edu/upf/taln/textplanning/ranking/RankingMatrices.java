package edu.upf.taln.textplanning.ranking;

import Jama.Matrix;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
	public static Matrix createEntityRankingMatrix(List<Entity> entities, WeightingFunction relevance,
	                                               EntitySimilarity similarity, TextPlanner.Options o)
	{
		int n = entities.size();

		// Create *strictly positive* relevance row vector by applying the weighting function to the set of entities
		double[] r = entities.stream()
				.map(Entity::getId)
				.mapToDouble(relevance::weight)
				.map(w -> w = Math.max(o.minRelevance, (1.0/n)*w)) // Laplace smoothing to avoid non-positive values
				.toArray();

		double accum = Arrays.stream(r).sum();
		IntStream.range(0, n).forEach(i -> r[i] /= accum); // normalize vector with sum of row

		// Create *non-symmetric non-negative* similarity matrix from sim function and links in content graph
		Matrix m = new Matrix(entities.stream()
				.map(e1 -> entities.stream()
						.mapToDouble(e2 -> similarity.computeSimilarity(e1, e2).orElse(similarity.getAverageSimiliarity()))
						.map(v -> v < o.simLowerBound ? 0.0 : v)
						.toArray())
				.toArray(double[][]::new));
		normalize(m);

		// Bias each row in m using relevance bias r and damping factor d
		// Prob i->j = d*r(j) + (1.0-d)*m(i,j)
		// The resulting matrix is row-normalized because both r and m are normalized
		double d = o.dampingRelevance;
		return new Matrix(entities.stream()
				.map(e1 -> entities.stream()
						.mapToDouble(e2 -> {
							int i = entities.indexOf(e1);
							int j = entities.indexOf(e2);

							double brj = r[j]; // bias towards relevance of j
							double sij = m.get(i, j); // sim of i and j
							return d * brj + (1.0 - d)*sij; // brj is positive, so pij also provided d > 0.0
						})
						.toArray())
				.toArray(double[][]::new));
		// No need to normalize again
	}

	/**
	 * Creates a stochastic matrix to rank a set of candidates (pairs of a mention and a candidate entity/sense).
	 * @param candidates structures (e.g. relations in a KB or extracted from text) containing candidates
	 * @param relevance relevance weighting function for entities
	 * @param similarity similarity metric for pairs of candidates
	 * @param o algorithm options
	 * @return a stochastic matrix
	 */
	public static Matrix createCandidateRankingMatrix(List<Candidate> candidates, WeightingFunction relevance,
	                                                  CandidateSimilarity similarity, TextPlanner.Options o)
	{
		int num_candidates = candidates.size();
		List<Entity> entities = candidates.stream()
				.map(Candidate::getEntity)
				.distinct()
				.collect(Collectors.toList());
		int num_entities = entities.size();

		// Create *strictly positive* relevance row vector by applying the weighting function to the set of entities
		double[] r = entities.stream()
				.map(Entity::getReference)
				.mapToDouble(relevance::weight)
				.map(w -> w = Math.max(o.minRelevance, (1.0/num_entities)*w)) // Laplace smoothing to avoid non-positive values
				.toArray();

		double accum_r = Arrays.stream(r).sum();
		IntStream.range(0, num_entities).forEach(i -> r[i] /= accum_r); // normalize vector with sum of row

		// Create *strictly positive* relevance row vector by applying the weighting function to the set of entities
		double[] t = candidates.stream()
				.mapToDouble(c -> {
						Candidate.Type mtype = c.getMention().getType();
						Candidate.Type etype = c.getEntity().getType();

						// Mention and candidate type match and are different to
						boolean indicator = mtype != Candidate.Type.Other && mtype == etype;
						return indicator ? 0.99 : 0.01; // avoid zeros
				})
				.toArray();

		double accum_t = Arrays.stream(t).sum();
		IntStream.range(0, num_candidates).forEach(i -> t[i] /= accum_t); // normalize vector with sum of rows

		// Create *non-symmetric non-negative* similarity matrix from sim function and links in content graph
		log.info("Creating similarity matrix for " + num_candidates + " candidates");
		AtomicLong counter = new AtomicLong(0);
		long num_calculations = ((((long)num_candidates * (long)num_candidates) - num_candidates)  / 2) + num_candidates;
		Matrix m = new Matrix(num_candidates, num_candidates);
		IntStream.range(0, num_candidates).forEach(i ->
				IntStream.range(i, num_candidates).forEach(j -> {
					double sim = 1.0;
					if (i != j)
					{
						Candidate c1 = candidates.get(i);
						Candidate c2 = candidates.get(j);
						sim = similarity.computeSimilarity(c1, c2);
						if (sim < o.simLowerBound)
							sim = 0.0;
					}

					m.set(i, j, sim);
					m.set(j, i, sim);

					if (counter.incrementAndGet() % 100000 == 0)
						log.info(counter.get() + " out of " + num_calculations);
				}));
		normalize(m);
		log.info("Done");

		// Bias each row in m using relevance bias r and type bias t
		// Prob i->j = a*r(j) + b*t(j) + (1.0-a-b)*m(i,j)
		// The resulting matrix is row-normalized because both r and m are normalized
		double a = o.dampingRelevance;
		double b = o.dampingType;
		return new Matrix(candidates.stream()
				.map(c1 -> candidates.stream()
						.mapToDouble(c2 -> {
							int i = candidates.indexOf(c1);
							int j = candidates.indexOf(c2);
							int ej = entities.indexOf(c2.getEntity());

							double rj = r[ej]; // bias towards relevance of j
							double tj = t[ej]; // bias towards type matching of j
							double sij = m.get(i, j); // sim of i and j
							return a * rj + b * tj + (1.0 - a - b)*sij; // brj is positive, so pij also provided d > 0.0
						})
						.toArray())
				.toArray(double[][]::new));
		// No need to normalize again
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
