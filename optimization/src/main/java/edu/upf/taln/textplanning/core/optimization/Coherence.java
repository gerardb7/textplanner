package edu.upf.taln.textplanning.core.optimization;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.ranking.MatrixFactory;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * Coherence function, based on similarity metric between pairs of candidates
 */
public class Coherence implements Function
{
	private final List<Candidate> candidates;
	private final double[][] semantic_similarity;
	private final static Logger log = LogManager.getLogger();

	Coherence(List<Candidate> candidates, BiFunction<String, String, OptionalDouble> similarity, double lower_bound)
	{
		this.candidates = candidates;
		List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(toList()); // with duplicates

		// Similarity for pairs of references that appear in the same mentions should be 0
		BiPredicate<String, String> filter = (r1, r2) ->
		{
			final Set<Mention> mentions_r1 = candidates.stream()
					.filter(c -> c.getMeaning().getReference().equals(r1))
					.map(Candidate::getMention)
					.collect(Collectors.toSet());
			final Set<Mention> mentions_r2 = candidates.stream()
					.filter(c -> c.getMeaning().getReference().equals(r2))
					.map(Candidate::getMention)
					.collect(Collectors.toSet());
			return Collections.disjoint(mentions_r1, mentions_r2);
		};

		// Don't normalize matrix, normalization is part of the optimizable softmax function
		semantic_similarity = MatrixFactory.createTransitionMatrix(meanings, similarity, true, filter, lower_bound,
				true);
	}

	@Override
	public double getValue(double[] dist)
	{
		return IntStream.range(0, dist.length)
				.mapToDouble(i -> IntStream.range(0, dist.length)
						.filter(j -> i != j)
						.filter(j -> candidates.get(i).getMention() != candidates.get(j).getMention()) // important!
						.mapToDouble(j -> dist[i] * dist[j] * semantic_similarity[i][j])
						.sum())
				.sum();
	}

	@Override
	public void getValueGradient(double[] dist, double[] gradient)
	{
		// Kronecker delta function
		//BiFunction<Integer,Integer, Double> d = (index, j) -> (Objects.equals(index, j)) ? 1.0 : 0.0;

		log.info("Calculating product of similarity matrix with distribution");
		Stopwatch timer = Stopwatch.createStarted();
		double[][] m = new double[dist.length][dist.length];
		for (int i = 0; i < dist.length; ++i)
		{
			for (int j = 0; j < dist.length; ++j)
			{
				m[i][j] = semantic_similarity[i][j] * dist[i] * dist[j];
			}
		}
		log.info("Done in " + timer.stop());


		log.info("Calculating gradients");
		timer.reset(); timer.start();

		double[] kronecker_values = new double[dist.length];

		AtomicInteger counter = new AtomicInteger(0);
		IntStream.range(0, dist.length).parallel()
 				.forEach(k -> gradient[k] = calculateDifferential(dist, m, k, kronecker_values, counter));
//			if ( k % 100 == 0)
//				log.info("Calculating differential " + k);
		log.info("Done in " + timer.stop());
	}

	private double calculateDifferential(double[] dist, double[][] m, int k, double kronecker_values[], AtomicInteger counter)
	{
		double k_value = 2 * dist[k];

		if (k > 0)
			kronecker_values[k - 1] = 0.0;
		kronecker_values[k] = 1.0;

		double gradient = 0.0;
		for (int i = 0; i < dist.length; ++i)
		{
			for (int j = 0; j < dist.length; ++j)
			{
				// gradient[k] += semantic_similarity[index][j] * dist[index] * dist[j] * (d.apply(index,k) + d.apply(j,k) - 2 * dist[k]);
				gradient += m[i][j] * (kronecker_values[i] + kronecker_values[j] + k_value);
			}
		}

		if (counter.incrementAndGet() % 1000 == 0)
			log.info("Completed " + counter.get() + " out of " + dist.length);
		return gradient;
	}
}
