package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import org.apache.commons.collections4.ListUtils;

import java.util.*;

/**
 * This weighting function scores entities according to the average position of their mentions in the text.
 * Scoring is done using a gaussian function to decrease scoring as their position increases.
 */
public final class Position implements WeightingFunction
{
	private final Map<String, Double> avgPositions = new HashMap<>();

	@Override
	public void setContents(Set<SemanticGraph> structures)
	{
		final Map<String, List<Long>> positions = new HashMap<>();

		long maxOffset = structures.stream()
			.mapToLong(s -> s.vertexSet().stream()
					.mapToLong(n -> n.getAnnotation().getOffsetEnd())
					.max().orElse(1))
			.max().orElse(1);

		structures.forEach(s ->
				s.vertexSet().forEach(n ->
						n.getCandidates().forEach(e ->
								positions.merge(e.getLabel(), Collections.singletonList(n.getAnnotation().getOffsetStart()), ListUtils::union))));

		avgPositions.clear();
		positions.keySet().forEach(e ->
		{
			double avg = positions.get(e).stream()
					.mapToDouble(offset -> offset/maxOffset)
					.average()
					.orElse(0.0);
			avgPositions.put(e, avg);
		});
	}

	@Override
	public double weight(String item)
	{
		double x = avgPositions.get(item);
		return Math.exp(-(Math.pow(x, 2)/0.2)); // simplified gaussian formula omitting coefficients
	}
}
