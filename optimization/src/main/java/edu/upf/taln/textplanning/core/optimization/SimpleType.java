package edu.upf.taln.textplanning.core.optimization;


import edu.upf.taln.textplanning.core.ranking.MatrixFactory;
import edu.upf.taln.textplanning.core.structures.Candidate;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * Simple type function based on matching NE class associated with mentions with the semantic type of candidate senses.
 */
public class SimpleType implements Function
{
	private final List<Candidate> candidates;
	private final double[] type_vector;

	SimpleType(List<Candidate> candidates)
	{
		this.candidates = candidates;
		// Don't normalize vector, normalization is part of the optimizable softmax function
		final List<String> items = candidates.stream()
				.map(Candidate::toString)
				.collect(toList());
		java.util.function.Function<String, Double> type_match = (id) -> {
			final Candidate c = candidates.get(items.indexOf(id));
			String mtype = c.getMention().getType();
			String etype = c.getMeaning().getType();
			boolean indicator = !mtype.isEmpty() && mtype.equals(etype);
			return indicator ? 1.0 : 0.0; // avoid zeros
		};


		// Mention and candidate type match and are different to
		type_vector = MatrixFactory.createBiasVector(items, type_match);
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
