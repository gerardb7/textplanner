package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.*;
import java.util.stream.IntStream;

public class RandomVectors implements Vectors, SentenceVectors
{
	private final Map<String, double []> vectors = new HashMap<>();
	private static final Random rand = new Random();
	private static final int num_dimensions = 300;
	private static final String unknown = "unknown";

	@Override
	public boolean isDefinedFor(String item)
	{
		return true;
	}

	@Override
	public boolean isDefinedFor(List<String> tokens)
	{
		return true;
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		return Optional.of(vectors.computeIfAbsent(item, RandomVectors::generateNewVector));
	}

	@Override
	public Optional<double[]> getVector(List<String> tokens)
	{
		return Optional.of(vectors.computeIfAbsent(String.join("", tokens), RandomVectors::generateNewVector));
	}


	@Override
	public int getNumDimensions()
	{
		return 300;
	}


	@Override
	public double[] getUnknownVector()
	{
		return vectors.computeIfAbsent(unknown, RandomVectors::generateNewVector);
	}

	@SuppressWarnings("unused")
	private static double[] generateNewVector(String item)
	{
		return IntStream.range(0, num_dimensions)
				.mapToDouble(i -> rand.nextDouble())
				.toArray();
	}
}
