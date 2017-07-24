package edu.upf.taln.textplanning.optimization;

import edu.upf.taln.textplanning.similarity.CandidateSimilarity;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * Coherence function, based on similarity metric between pairs of candidates
 */
public class Coherence implements Function
{
	private final List<Candidate> candidates;
	private final double[][] semantic_similarity;

	public Coherence(List<Candidate> candidates, CandidateSimilarity semanticSimilarity)
	{
		this.candidates = candidates;

		// Create Matrix with pairwise semantic similarity values
		List<String> entities = candidates.stream()
				.map(Candidate::getEntity)
				.map(Entity::getId)
				.collect(toList());

		double[][] sim_matrix = candidates.stream()
				.map(c1 -> candidates.stream()
						.mapToDouble(c2 -> semanticSimilarity.computeSimilarity(c1, c2))
						.toArray())
				.toArray(double[][]::new);

		this.semantic_similarity = candidates.stream()
				.map(p1 -> candidates.stream()
						.mapToDouble(p2 ->
						{
							int e1 = entities.indexOf(p1.getEntity().getId());
							int e2 = entities.indexOf(p2.getEntity().getId());
							return sim_matrix[e1][e2];
						})
						.toArray())
				.toArray(double[][]::new);
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
		BiFunction<Integer,Integer, Double> d = (i, j) -> (Objects.equals(i, j)) ? 1.0 : 0.0;

		// Some things are better expressed with traditional loops...
		for (int k = 0; k < dist.length; ++k) // for each partial derivative wrt dist/value k
		{
			for (int i = 0; i < dist.length; ++i)
			{
				for (int j = 0; j < dist.length; ++j)
				{
					gradient[k] += semantic_similarity[i][j] * dist[i] * dist[j] * (d.apply(i,k) + d.apply(j,k) - 2 * dist[k]);
				}
			}
		}
	}
}
