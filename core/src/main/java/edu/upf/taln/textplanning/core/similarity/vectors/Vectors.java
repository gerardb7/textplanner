package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public abstract class Vectors
{
	abstract public boolean isDefinedFor(String item);
	abstract public int getNumDimensions();
	abstract public Optional<double[]> getVector(String item);

	protected Optional<double[]> getUnknownVector()
	{
		final List<String> unknown = Arrays.asList("UNKNOWN", "UUUNKKK", "UNK", "*UNKNOWN*", "<unk>");
		return unknown.stream()
				.map(this::getVector)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.findFirst();
	}

	// Text_Glove -> with header containing num dimensions, Text_Word2Vec -> without header
	public enum VectorType {Text_Glove, Text_Word2vec, Binary_Word2vec, Binary_RandomAccess, SenseGlosses, Random}
}
