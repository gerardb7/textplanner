package edu.upf.taln.textplanning.metrics;

import edu.upf.taln.textplanning.corpora.CorpusCounts;
import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class implements corpora-based scoring of patterns.
 * Scoring is average of individual scores of each entity in a pattern against a set of reference entities.
 * It implements three different metrics for pairs of entities based on their annotations in semantically
 * annotated corpora.
 * Immutable class.
 */
public final class CorpusMetric implements PatternMetric
{
	public enum Metric
	{
		Cooccurrence, WeightedCooccurrence, InterpolatedBigrams
	}
	private final Metric metricType;
	private final CorpusCounts corpora;
	private final String domain;

	/**
	 *  Constructor
	 *  @param inCorpora     corpora used to obtain empirical evidence in the form of cooccurrence counts of pairs of entities
	 *  @param inMetricType  metric used to estimate relevance from counts
	 *  @param inDomain      domain when multiple corpora is available
	 */
	public CorpusMetric(CorpusCounts inCorpora, Metric inMetricType, String inDomain)
	{
		corpora = inCorpora;
		metricType = inMetricType;
		domain = inDomain;
	}

	/**
	 * Scores a list of patterns according to their empirically estimated relevance relative to a set of reference entities
	 *
	 * @param inReferenceEntities reference entities
	 * @param inPatterns          collection of patterns to score
	 * @return scores for each pattern
	 */
	@Override
	public Map<SemanticTree, Double> assess(Set<String> inReferenceEntities, Collection<SemanticTree> inPatterns)
	{
		Map<SemanticTree, Double> scores = new HashMap<>();

		// Iterate over patterns
		for (SemanticTree pattern : inPatterns)
		{
			// Iterate over entities in pattern
			Set<String> entities = pattern.getEntities();
			List<Double> patternScores = entities.stream()
					.map(e -> inReferenceEntities.stream()
							.mapToDouble(eRef -> score(eRef, e))
								.average().getAsDouble())
					.collect(Collectors.toList());

			// Calculate average over entity score
			OptionalDouble average = patternScores
					.stream()
					.mapToDouble(a -> a)
					.average();

			scores.put(pattern, average.isPresent() ? average.getAsDouble() : 0);
		}

		return scores;
	}

	/**
	 * Scores an entity according to its empirically estimated relevance relative to another entity acting as a reference
	 *
	 * @param inReferenceEntity reference entity
	 * @param inEntity          entity to assess
	 * @return score for the entity
	 */
	public Double score(String inReferenceEntity, String inEntity)
	{
		if (inReferenceEntity.equals(inEntity))
		{
			return 1.0;
		}
		switch (metricType)
		{
			case Cooccurrence:
				return calculateCooccurrence(inReferenceEntity, inEntity);
			case WeightedCooccurrence:
				return calculateWeightedCooccurrence(inReferenceEntity, inEntity);
			case InterpolatedBigrams:
			default:
				return calculateInterpolatedBigrams(inReferenceEntity, inEntity);
		}
	}


	/**
	 * Cooccurrence metric is the number of annotations of two entities in the same document occur divided by number of
	 * annotations of the second entity.
	 */
	private double calculateCooccurrence(String e1, String e2)
	{
		CorpusCounts.Counts counts = corpora.getCounts(e1, e2, domain);
		if (counts.freq == 0)
		{
			return 0.0;
		}
		return (double) counts.cooccur / (double) counts.freq;
	}

	/**
	 * Weighted cooccurrence metric is calculated like the cooccurence metric but each occurrence of both entities in
	 * the same document is weighted by the distance between their annotations.
	 */
	private double calculateWeightedCooccurrence(String e1, String e2)
	{
		return IntStream.range(0, 5)
				.mapToDouble(i -> {
					CorpusCounts.Counts counts = corpora.getCounts(e1, e2, domain, i);
					double lambda = 1 / (double) i;
					return counts.cooccur * lambda / (double) counts.freq;
				})
				.sum();
	}

	/**
	 * Weighted interpolated bigrams are calculated like the weighted cooccurence metric but considering only
	 * annotations of <e1,e2> in this specific order.
	 */
	private double calculateInterpolatedBigrams(String e1, String e2)
	{
		return IntStream.range(0, 5)
				.mapToDouble(i -> {
					CorpusCounts.Counts counts = corpora.getOrderedCounts(e1, e2, domain, i);
					double lambda = 1 / (double) i;
					return counts.cooccur * lambda / (double) counts.freq;
				})
				.sum();
	}

}
