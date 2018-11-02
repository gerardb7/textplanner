package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.input.amr.Candidate;

import java.util.Collection;

public class NoWeights implements WeightingFunction
{
	@Override
	public void setContents(Collection<Candidate> contents){ }

	@Override
	public double weight(String item)
	{
		return 0.0;
	}
}
