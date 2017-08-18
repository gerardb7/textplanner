package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Collection;
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
	public void setContents(Collection<LinguisticStructure> contents)
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
		Map<WeightingFunction, Double> l = new HashMap<>();
		l.putAll(functions);
		return l;
	}
}
