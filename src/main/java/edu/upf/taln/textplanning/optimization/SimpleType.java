package edu.upf.taln.textplanning.optimization;


import edu.upf.taln.textplanning.ranking.RankingMatrices;
import edu.upf.taln.textplanning.structures.Candidate;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

/**
 * Simple type function based on matching NE class associated with mentions with the semantic type of candidate senses.
 */
public class SimpleType implements Function
{
	private final List<Candidate> candidates;
	private final double[] type_vector;

	public SimpleType(List<Candidate> candidates)
	{
		this.candidates = candidates;
		// Don't normalize vector, normalization is part of the optimizable softmax function
		type_vector = RankingMatrices.createTypeVector(candidates, false, false);
	}

	@Override
	public double getValue(double[] dist)
	{
		// todo normalize goal for NE mentions
		return IntStream.range(0, candidates.size())
				.mapToDouble(i -> type_vector[i] * dist[i])
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
				gradient[k] += type_vector[i] * dist[i] * (d.apply(i,k) - dist[k]);
			}
		}
	}
}
