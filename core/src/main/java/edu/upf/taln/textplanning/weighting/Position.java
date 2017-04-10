package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.collections4.ListUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This weighting function scores items according to their average position in a collection of documents.
 * Scoring is done using a gaussian function to decrease scoring as sentence position increases.
 */
public class Position implements WeightingFunction
{
	private final Map<String, Double> avgPositions = new HashMap<>();

	@Override
	public void setCollection(List<SemanticTree> inCollection)
	{
		final Map<String, List<Double>> positions = new HashMap<>();
		inCollection.forEach(t ->
				t.vertexSet().stream()
						.map(n -> n.getEntity().getEntityLabel())
						.forEach(s -> positions.merge(s, Collections.singletonList(t.getPosition()), ListUtils::union)));

		avgPositions.clear();
		avgPositions.putAll(positions.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
						.mapToDouble(Double::valueOf)
						.average()
						.orElse(1.0))));
	}

	@Override
	public double weight(Entity inEntity)
	{
		// final double a = 1.0;
		// final double b = 0.0;
		// final double c = 0.3333;
		double x = avgPositions.get(inEntity.getEntityLabel());
		return Math.exp(-(Math.pow(x, 2)/0.2)); // simplified gaussian formula omitting coefficients
	}
}
