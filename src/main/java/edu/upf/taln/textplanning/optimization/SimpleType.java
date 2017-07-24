package edu.upf.taln.textplanning.optimization;


import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Candidate.Type;

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

	public SimpleType(List<Candidate> candidates)
	{
		this.candidates = candidates;
	}



	@Override
	public double getValue(double[] dist)
	{
		// todo normalize goal for NE mentions
		return IntStream.range(0, candidates.size())
				.mapToDouble(i -> typeMatch(i) * dist[i])
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
				gradient[k] += typeMatch(i) * dist[i] * (d.apply(i,k) - dist[k]);
			}
		}
	}

	private double typeMatch(int i)
	{
			Candidate c = candidates.get(i);
			Type mtype = c.getMention().getType();
			Type etype = c.getEntity().getType();
			boolean indicator = mtype != Type.Other && mtype == etype;
			return indicator ? 1.0 : 0.0;
	}
}
