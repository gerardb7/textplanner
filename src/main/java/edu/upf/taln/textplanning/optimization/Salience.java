package edu.upf.taln.textplanning.optimization;

import edu.upf.taln.textplanning.ranking.RankingMatrices;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Salience function based on a relevance distribution over candidate senses.
 */
public class Salience implements Function
{
	private final List<Candidate> candidates;
	private final Map<Candidate, Integer> candidates2Indexes;
	private final double[] relevanceValues; // per entity, not candidate


	public Salience(List<Candidate> candidates, WeightingFunction relevance, double lower_bound)
	{
		this.candidates = candidates;

		List<Entity> entities = candidates.stream()
				.map(Candidate::getEntity)
				.distinct()
				.collect(toList());
		candidates2Indexes = candidates.stream()
				.collect(toMap(c -> c, c -> entities.indexOf(c.getEntity())));

		// Don't normalize vector, normalization is part of the optimizable softmax function
		// Store relevance values for each pair
		relevanceValues = RankingMatrices.createRelevanceVector(entities, relevance, lower_bound, false);
	}

	@Override
	public double getValue(double[] dist)
	{
		return IntStream.range(0, candidates.size())
				.mapToDouble(i -> {
					Integer entity_index = candidates2Indexes.get(candidates.get(i));
					return dist[i] * relevanceValues[entity_index];
				})
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
				Integer entity_index = candidates2Indexes.get(candidates.get(i));
				gradient[k] += relevanceValues[entity_index] * dist[i] * (d.apply(i,k) - dist[k]);
			}
		}
	}

}
