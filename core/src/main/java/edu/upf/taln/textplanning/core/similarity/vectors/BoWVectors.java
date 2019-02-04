package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BoWVectors implements SentenceVectors
{
	private final Vectors word_vectors;

	public BoWVectors(Vectors word_vectors)
	{
		this.word_vectors = word_vectors;
	}

	// A meaningful vector can be produced if at least on the tokens in which item is divided has a word vector
	@Override
	public boolean isDefinedFor(String item)
	{
		return Arrays.stream(item.split("\\s")).anyMatch(word_vectors::isDefinedFor);
	}

	@Override
	public int getNumDimensions()
	{
		return word_vectors.getNumDimensions();
	}

	@Override
	public double[] getUnknownVector()
	{
		return word_vectors.getUnknownVector();
	}

	// calculates arithmetic average of vectors of items
	public Optional<double[]> getVector(List<String> items)
	{
		final List<double[]> vectors = items.stream()
				.map(word_vectors::getVector)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());

		if (vectors.isEmpty())
			return Optional.empty();
		else if (vectors.size() == 1)
			return Optional.of(vectors.get(0));

		final double[] average = IntStream.range(0, getNumDimensions())
				.mapToDouble(i -> vectors.stream()
						.mapToDouble(v -> v[i])
						.average().orElse(0.0))
				.toArray();
		return Optional.of(average);
	}
}
