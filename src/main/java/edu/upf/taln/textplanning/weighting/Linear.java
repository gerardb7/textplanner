package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linear combination of other weighting functions.
 */
public class Linear implements WeightingFunction
{
	private final Map<WeightingFunction, Double> functions;
	public Linear(Map<WeightingFunction, Double> inFunctions)
	{
		functions = inFunctions;
	}

	@Override
	public void setCollection(List<SemanticTree> inCollection)
	{
		functions.forEach((f, w) -> f.setCollection(inCollection));
	}

	@Override
	public double weight(Entity inEntity)
	{
		return functions.entrySet().stream()
				.mapToDouble(p -> p.getKey().weight(inEntity) * p.getValue())
				.sum();
	}

	public Map<WeightingFunction, Double> getFunctions()
	{
		Map<WeightingFunction, Double> l = new HashMap<>();
		l.putAll(functions);
		return l;
	}
}
