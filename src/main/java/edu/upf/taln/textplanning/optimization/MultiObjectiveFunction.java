package edu.upf.taln.textplanning.optimization;

import cc.mallet.types.MatrixOps;

import java.util.Arrays;
import java.util.List;

/**
 * Aggregates multiple functions into a single multiobjective function.
 */
public class MultiObjectiveFunction implements Function
{
	private final List<Function> optimizables;

	public MultiObjectiveFunction(Function... ops)
	{
		optimizables = Arrays.asList(ops);
	}

	// The value returned is the sum  of the values of the aggregated functions.
	public double getValue(double[] dist)
	{
		return optimizables.stream()
				.mapToDouble(f -> f.getValue(dist))
				.sum();
	}

	// The gradient returned is the vector sum of all gradients
	@Override
	public void getValueGradient(double[] params, double[] gradient)
	{
		// todo look at how to combine multiple gradients
		optimizables.forEach(f ->
		{
			double[] g = new double[params.length];
			MatrixOps.setAll(g, 0.0D);
			f.getValueGradient(params, g);
			MatrixOps.plusEquals(g, gradient);
		});
	}
}
