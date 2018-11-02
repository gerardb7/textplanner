package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
//import org.deeplearning4j.models.word2vec.Word2Vec;

import java.nio.file.Path;

/**
 * Efficient loading of glove binary vectors based on:
 * https://github.com/spennihana/FasterWordEmbeddings
 *
 */
public class Word2VecVectorsSimilarity implements SimilarityFunction
{
//	private final Word2Vec vectors;
	private final static Logger log = LogManager.getLogger();

	public static Word2VecVectorsSimilarity create(Path vectors_path)
	{
		try
		{
			return new Word2VecVectorsSimilarity(vectors_path);
		}
		catch (Exception e)
		{
			log.error("Cannot load vectors from " +  vectors_path + ": " + e);
			return null;
		}
	}

	private Word2VecVectorsSimilarity(Path vectors_file)
	{
		log.info("Loading vectors from binary files " + vectors_file.getFileName().toString());
		Stopwatch timer = Stopwatch.createStarted();
//		vectors = WordVectorSerializer.readWord2VecModel(vectors_file.toFile());
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String e)
	{
		return false; //vectors.hasWord(e);
	}

	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return isDefinedFor(e1) && isDefinedFor(e2);
	}

	@Override
	public double computeSimilarity(String e1, String e2)
	{
		return 0.0; //vectors.similarity(e1, e2);
	}
}
