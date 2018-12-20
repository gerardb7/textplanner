package edu.upf.taln.textplanning.core.similarity;

import edu.upf.taln.textplanning.core.similarity.vectors.RandomAccessVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a binary
 * file and accessed with random access glove library: https://github.com/thomasjungblut/glove/blob/master/README.md
 */
public class VectorsCosineSimilarity implements SimilarityFunction
{
	private final Vectors vectors;
	private final static Logger log = LogManager.getLogger();

	public VectorsCosineSimilarity(Vectors vectors)
	{
		this.vectors = vectors;
	}

	@Override
	public boolean isDefinedFor(String e)
	{
		return vectors.isDefinedFor(e);
	}
	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return vectors.isDefinedFor(e1) && vectors.isDefinedFor(e2);
	}

	@Override
	public double computeSimilarity(String e1, String e2)
	{
		double[] v1 = vectors.getVector(e1);
		double[] v2 = vectors.getVector(e2);

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
			return 1.0;

		return dotProduct / magnitude; // range (-1,1)
	}
}
