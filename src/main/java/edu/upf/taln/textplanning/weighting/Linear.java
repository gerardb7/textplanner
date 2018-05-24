package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.input.amr.GraphList;

import java.util.HashMap;
import java.util.Map;

/**
 * Linear combination of other weighting functions.
 */
public final class Linear implements WeightingFunction
{
	private final Map<WeightingFunction, Double> functions;
	public Linear(Map<WeightingFunction, Double> inFunctions)
	{
		functions = inFunctions;
	}

	@Override
	public void setContents(GraphList contents)
	{
		functions.forEach((f, w) -> f.setContents(contents));
	}

	@Override
	public double weight(String item)
	{
		return functions.entrySet().stream()
				.mapToDouble(p -> p.getKey().weight(item) * p.getValue())
				.sum();
	}

	public Map<WeightingFunction, Double> getFunctions()
	{
		return new HashMap<>(functions);
	}
}
