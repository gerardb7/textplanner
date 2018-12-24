package edu.upf.taln.textplanning.core.similarity;

import edu.upf.taln.textplanning.core.similarity.vectors.RandomAccessVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.TextVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Word2VecVectors;

import java.nio.file.Path;
import java.util.function.Function;

public class SimilarityFunctionFactory
{
	public enum VectorType {Text_Glove, Text_Word2vec, Binary_Word2vec, Binary_RandomAccess}
	public enum FunctionType {Cosine, SIF}

	public static SimilarityFunction get(FunctionType functionType, Path vectors_path, VectorType vectorType) throws Exception
	{
		return get(functionType, vectors_path, vectorType, null);
	}
	public static SimilarityFunction get(FunctionType functionType, Path vectors_path, VectorType vectorType,
	                                     Function<String, Double> weights) throws Exception
	{
		Vectors vectors;
		switch (vectorType)
		{
			case Text_Glove:
			case Text_Word2vec:
				vectors = new TextVectors(vectors_path, vectorType);
				break;
			case Binary_Word2vec:
				vectors = new Word2VecVectors(vectors_path);
				break;
			case Binary_RandomAccess:
			default:
				vectors = new RandomAccessVectors(vectors_path);
		}

		switch (functionType)
		{
			case SIF:
				if (weights == null)
					throw new Exception(FunctionType.SIF + " requires a weighting function");
				return new VectorsSIFSimilarity(vectors, weights);
			case Cosine:
			default:
				return new VectorsCosineSimilarity(vectors);
		}
	}
}
