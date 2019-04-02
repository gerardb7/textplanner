package edu.upf.taln.textplanning.core.similarity.vectors;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public abstract class Vectors
{

	public abstract boolean isDefinedFor(String item);
	public abstract int getNumDimensions();
	public abstract Optional<double[]> getVector(String item);

	protected static Optional<double[]> getUnknownVector(Function<String, Optional<double []>> f)
	{
		final List<String> unknown = Arrays.asList("UNKNOWN", "UUUNKKK", "UNK", "*UNKNOWN*", "<unk>");
		return 	unknown.stream()
				.map(f)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.findFirst();
	}

	// Text_Glove -> with header containing num dimensions, Text_Word2Vec -> without header
	public enum VectorType {Text_Glove, Text_Word2vec, Binary_Word2vec, Binary_RandomAccess, SenseGlosses, Random}
}
