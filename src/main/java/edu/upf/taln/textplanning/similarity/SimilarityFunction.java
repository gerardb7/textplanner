package edu.upf.taln.textplanning.similarity;

import java.util.Optional;

/**
 * Base class for similarity functions comparing pairs of items
 */
public interface SimilarityFunction
{
	boolean isDefinedFor(String e);
	boolean isDefinedFor(String e1, String e2);

	/**
	 * @return a value between 0.0 and 1.0 or an empty optional.
	 */
	default Optional<Double> getSimilarity(String e1, String e2)
	{
		if (e1.equals(e2))
			return Optional.of(1.0);
		if (!isDefinedFor(e1, e2))
			return Optional.empty();

		return Optional.of(computeSimilarity(e1, e2)).filter(s -> !Double.isNaN(s));
	}


	// to be implemented by subclasses
	double computeSimilarity(String e1, String e2);

}
