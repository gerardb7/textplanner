package edu.upf.taln.textplanning.metrics;

import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This summarization metric scores patterns according to their relative position in a document.
 * Scoring is done using a gaussian function to decrease scoring as sentence position increases.
 */
public class PositionMetric implements PatternMetric
{

	@Override
	public Map<SemanticTree, Double> assess(Set<String> inReferenceEntities, Collection<SemanticTree> inPatterns)
	{
		List<SemanticTree> patternList = inPatterns.stream().collect(Collectors.toList());

		return IntStream.range(0, patternList.size())
				.boxed()
				.collect(Collectors.toMap(patternList::get, i -> gaussian(patternList.get(i).getPosition())));
	}

	private double gaussian(double x)
	{
		// final double a = 1.0;
		// final double b = 0.0;
		// final double c = 0.3333;
		return Math.exp(-(Math.pow(x, 2)/0.2)); // simplified gaussian formula omitting coefficients
	}
}
