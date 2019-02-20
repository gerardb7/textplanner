package edu.upf.taln.textplanning.core.similarity.vectors;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public interface Vectors
{
	boolean isDefinedFor(String item);
	int getNumDimensions();
	Optional<double[]> getVector(String item);

	default double[] getUnknownVector()
	{
		final List<String> unknown = Arrays.asList("UNKNOWN", "UUUNKKK", "UNK", "*UNKNOWN*", "<unk>");
		return unknown.stream()
				.map(this::getVector)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.findFirst()
				.orElse(new double[getNumDimensions()]);
	}

	// Text_Glove -> with header containing num dimensions, Text_Word2Vec -> without header
	public enum VectorType {Text_Glove, Text_Word2vec, Binary_Word2vec, Binary_RandomAccess, SenseGlosses, Random}
}
