package edu.upf.taln.textplanning.core.similarity;

import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a binary
 * file and accessed with random access glove library: https://github.com/thomasjungblut/glove/blob/master/README.md
 */
public class VectorsSimilarity implements SimilarityFunction
{
	private final Vectors vectors;
	private final BiFunction<double [], double [], Double> sim_function;

	public VectorsSimilarity(Vectors vectors, BiFunction<double [], double [], Double> sim_function)
	{
		this.vectors = vectors;
		this.sim_function = sim_function;
	}

	@Override
	public OptionalDouble apply(String e1, String e2)
	{
		if (e1.equals(e2))
			return OptionalDouble.of(1.0);

		final Optional<double[]> ov1 = vectors.getVector(e1);
		final Optional<double[]> ov2 = vectors.getVector(e2);
		if (ov1.isEmpty() || ov2.isEmpty())
			return OptionalDouble.empty();

		double[] v1 = ov1.get();
		double[] v2 = ov2.get();

		final double sim = sim_function.apply(v1, v2);

		if (Double.isNaN(sim))
			return OptionalDouble.empty();

		return OptionalDouble.of(sim);
	}

	@Override
	public boolean isDefined(String item)
	{
		return vectors.isDefinedFor(item);
	}
}
