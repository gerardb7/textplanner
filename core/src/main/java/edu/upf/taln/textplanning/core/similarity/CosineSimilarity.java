package edu.upf.taln.textplanning.core.similarity;

import java.util.function.BiFunction;

public class CosineSimilarity implements BiFunction<double [], double [], Double>
{
	@Override
	public Double apply(double[] v1, double[] v2)
	{

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

		return dotProduct / magnitude; // range (-1,1)
	}
}
