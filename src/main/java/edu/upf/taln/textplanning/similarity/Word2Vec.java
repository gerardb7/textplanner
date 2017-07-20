package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

//import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
//import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

/**
 * Computes similarity between word forms according to word distributional vectors
 */
public class Word2Vec
{
//	private final WordVectors vectors;
	private final static Logger log = LoggerFactory.getLogger(Word2Vec.class);

	public Word2Vec(Path inEmbeddingsPath) throws IOException
	{
		log.info("Loading word vectors");
		Stopwatch timer = Stopwatch.createStarted();
//		vectors = WordVectorSerializer.readWord2VecModel(inEmbeddingsPath.toAbsolutePath().toString());
		log.info("Loading took " + timer.stop());
	}

	public boolean isDefinedFor(String item)
	{
//		return vectors.hasWord(item);
		return false;
	}

	public boolean isDefinedFor(String item1, String item2)
	{
//		return vectors.hasWord(item1) &&
//				vectors.hasWord(item2);
		return false;
	}

	public double computeSimilarity(String item1, String item2)
	{

		if (item1.equals(item2))
			return 1.0;
		if (!isDefinedFor(item1, item2))
			return 0.0;

		return 0.0;
		//return vectors.similarity(item1, item2);
	}
}
