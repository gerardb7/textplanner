package edu.upf.taln.textplanning.optimization;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.ranking.MatrixFactory;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * Coherence function, based on similarity metric between pairs of candidates
 */
public class Coherence implements Function
{
	private final List<Candidate> candidates;
	private final double[][] semantic_similarity;
	private final static Logger log = LogManager.getLogger(Coherence.class);

	Coherence(List<Candidate> candidates, SimilarityFunction similarity, double lower_bound)
	{
		this.candidates = candidates;
		List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(toList()); // with duplicates
		// Don't normalize matrix, normalization is part of the optimizable softmax function
		semantic_similarity = MatrixFactory.createMeaningsSimilarityMatrix(meanings, similarity, lower_bound,
				false);
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
