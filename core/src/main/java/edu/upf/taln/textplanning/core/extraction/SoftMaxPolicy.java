package edu.upf.taln.textplanning.core.extraction;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.Random;
import java.util.function.Function;

public class SoftMaxPolicy implements Policy
{
	private final double temperature;

	public SoftMaxPolicy(double temperature)
	{
		this.temperature = temperature;
	}

	/**
	 * Softmax with low temperatures boosts probabilities of nodes with high weights and produces low probabilities
	 * for nodes with low weights. Temperature set experimentally.
	 */
	public int select(double[] weights)
	{
		if (weights.length == 1)
			return 0;

		final DoubleSummaryStatistics stats = Arrays.stream(weights)
				.summaryStatistics();

		// if same weight, select one at random
		if (stats.getMin() == stats.getMax())
			return new Random().nextInt(weights.length);

		// Rebase weights to a [0..1] scale. Useful if differences between values are small
		// (((OldValue - OldMin) * (NewMax - NewMin)) / (OldMax - OldMin)) + NewMin
		Function<Double, Double> rebase = w -> (w - stats.getMin()) / (stats.getMax() - stats.getMin());
		final double[] rebased_weights = Arrays.stream(weights)
				.map(rebase::apply)
				.toArray();

		// Create distribution
		double[] exps = Arrays.stream(rebased_weights)
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
