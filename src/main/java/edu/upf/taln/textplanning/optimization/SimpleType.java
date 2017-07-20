package edu.upf.taln.textplanning.optimization;


import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Candidate.Type;

import java.util.List;
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
				.mapToDouble(i -> {
					Candidate c = candidates.get(i);
					Type mtype = c.getMention().getType();
					Type etype = c.getEntity().getType();

					// Mention and candidate type match and are different to
					boolean indicator = mtype != Type.Other && mtype == etype;
					return dist[i] * (indicator ? 1.0 : 0.0);
				})
				.sum();
	}

	@Override
	public void getValueGradient(double[] params, double[] gradient)
	{

	}
}
