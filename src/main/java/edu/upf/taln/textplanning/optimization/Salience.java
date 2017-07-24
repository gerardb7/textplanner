package edu.upf.taln.textplanning.optimization;

import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

/**
 * Salience function based on a relevance distribution over candidate senses.
 */
public class Salience implements Function
{
	private final List<Candidate> candidates;
	private final double[] relevanceValues;

	public Salience(List<Candidate> candidates, WeightingFunction relevance)
	{
		this.candidates = candidates;

		// Store relevance values for each pair
		relevanceValues = candidates.stream()
				.map(Candidate::getEntity)
				.map(Entity::getId)
				.mapToDouble(relevance::weight)
				.toArray();
	}

	@Override
	public double getValue(double[] dist)
	{
		return IntStream.range(0, candidates.size())
				.mapToDouble(i -> dist[i] * relevanceValues[i])
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
				gradient[k] += relevanceValues[i] * dist[i] * (d.apply(i,k) - dist[k]);
			}
		}
	}

}
