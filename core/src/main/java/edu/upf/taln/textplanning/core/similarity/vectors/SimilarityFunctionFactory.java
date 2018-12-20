package edu.upf.taln.textplanning.core.similarity.vectors;

import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.similarity.VectorsCosineSimilarity;

import java.nio.file.Path;

public class SimilarityFunctionFactory
{
	public enum Format {Text_Glove, Text_Word2vec, Binary_Word2vec, Binary_RandomAccess}

	public static SimilarityFunction get(Path vectors_path, Format format) throws Exception
	{
		Vectors vectors = null;
		switch (format)
		{
			case Text_Glove:
			case Text_Word2vec:
				vectors = new TextVectors(vectors_path, format);
				break;
			case Binary_Word2vec:
				vectors = new Word2VecVectors(vectors_path);
				break;
			case Binary_RandomAccess:
				vectors = new RandomAccessVectors(vectors_path);
		}

		return new VectorsCosineSimilarity(vectors);
	}
}
