package edu.upf.taln.textplanning.weighting;

import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;

public class NoWeights implements WeightingFunction
{
	@Override
	public void setContents(Multimap<Meaning, Mention> contents){ }

	@Override
	public double weight(String item)
	{
		return 0.0;
	}
}
