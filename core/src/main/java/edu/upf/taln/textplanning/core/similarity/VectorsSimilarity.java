package edu.upf.taln.textplanning.core.similarity;

import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a binary
 * file and accessed with random access glove library: https://github.com/thomasjungblut/glove/blob/master/README.md
 */
public class VectorsSimilarity
{
	private final Vectors vectors;
	private final BiFunction<double [], double [], Double> sim_function;

	public VectorsSimilarity(Vectors vectors, BiFunction<double [], double [], Double> sim_function)
	{
		this.vectors = vectors;
		this.sim_function = sim_function;
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

		final double sim = sim_function.apply(v1, v2);

		if (Double.isNaN(sim))
			return OptionalDouble.empty();

		return OptionalDouble.of(sim);
	}
}
