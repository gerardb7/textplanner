package edu.upf.taln.textplanning.core.extraction;

import java.util.Comparator;
import java.util.stream.IntStream;

public class ArgMaxPolicy implements Policy
{
	@Override
	public int select(double[] weights)
	{
		return IntStream.range(0, weights.length)
				.boxed()
				.max(Comparator.comparing(i -> weights[i]))
				.orElse(-1);
	}
}
