package edu.upf.taln.textplanning.core.similarity;

import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a binary
 * file and accessed with random access glove library: https://github.com/thomasjungblut/glove/blob/master/README.md
 */
public class VectorsCosineSimilarity
{
	private final Vectors vectors;

	public VectorsCosineSimilarity(Vectors vectors)
	{
		this.vectors = vectors;
	}

	public OptionalDouble of(String e1, String e2)
	{
		final Optional<double[]> ov1 = vectors.getVector(e1);
		final Optional<double[]> ov2 = vectors.getVector(e2);
		if (!ov1.isPresent() || !ov2.isPresent())
			return OptionalDouble.empty();

		if (e1.equals(e2))
			return OptionalDouble.of(1.0);

		double[] v1 = vectors.getVector(e1).orElse(vectors.getUnknownVector());
		double[] v2 = vectors.getVector(e2).orElse(vectors.getUnknownVector());

		double dotProduct = 0.0;
		double normA = 0.0;
		double normB = 0.0;
		for (int i = 0; i < v1.length; i++)
		{
			dotProduct += v1[i] * v2[i];
			normA += Math.pow(v1[i], 2);
			normB += Math.pow(v2[i], 2);
		}

		double magnitude = Math.sqrt(normA) * Math.sqrt(normB); // will normalize with magnitude in order to ignore it

		// correct for floating-point rounding errors
		magnitude = Math.max(magnitude, dotProduct);

		// prevent NaNs
		if (magnitude == 0.0d)
			return OptionalDouble.of(1.0);

		return OptionalDouble.of(dotProduct / magnitude); // range (-1,1)
	}
}
