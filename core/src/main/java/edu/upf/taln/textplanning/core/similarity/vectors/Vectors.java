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
				.orElse(new Random().doubles(getNumDimensions()).toArray());
	}

	enum VectorType {Text_Glove, Text_Word2vec, Binary_Word2vec, Binary_RandomAccess}

	static Vectors get(Path location, VectorType type, int num_dimensions) throws Exception
	{
		switch (type)
		{
			case Text_Glove:
			case Text_Word2vec:
				return new TextVectors(location, type);
			case Binary_Word2vec:
				return new Word2VecVectors(location);
			case Binary_RandomAccess:
			default:
				return new RandomAccessVectors(location,num_dimensions);
		}
	}
}
