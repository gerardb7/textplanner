package edu.upf.taln.textplanning.core.extraction;

import java.util.Arrays;
import java.util.stream.IntStream;

public class SoftMaxPolicy implements Policy
{
	private final static double temperature = 0.01;

	/**
	 * Softmax with low temperatures boosts probabilities of nodes with high weights and produces low probabilities
	 * for nodes with low weights. Temperature set experimentally.
	 */
	public int select(double[] weights)
	{
		if (weights.length == 1)
			return 0;

		double accum = Arrays.stream(weights).sum();
		IntStream.range(0, weights.length).forEach(i -> {
			weights[i] = weights[i] / accum;
			assert !Double.isNaN(weights[i]) : "NaN value at " + i;
		});

		// Create distribution
		double[] exps = Arrays.stream(weights)
				.map(v -> v / temperature)
				.map(Math::exp)
				.toArray();
		double sum_exps = Arrays.stream(exps).sum();
		double[] softmax = Arrays.stream(exps)
				.map(e -> e / sum_exps)
				.peek(d -> { assert !Double.isNaN(d); })
				.toArray();


		// Choose key
		double p = Math.random();
		double cumulativeProbability = 0.0;
		for (int i=0; i < weights.length; ++i)
		{
			cumulativeProbability += softmax[i];
			if (p <= cumulativeProbability)
				return i;
		}

		return -1; // only if w is empty
	}
}
