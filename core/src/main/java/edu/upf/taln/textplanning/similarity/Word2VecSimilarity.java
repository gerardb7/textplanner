package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Computes similarity between word forms according to Word2Vec distributional vectors
 */
public class Word2VecSimilarity implements ItemSimilarity
{
	private final WordVectors vectors;
	private final static Logger log = LoggerFactory.getLogger(Word2VecSimilarity.class);

	public Word2VecSimilarity(Path inEmbeddingsPath) throws IOException
	{
		log.info("Loading Word2Vec vectors");
		Stopwatch timer = Stopwatch.createStarted();
		vectors = WordVectorSerializer.loadGoogleModel(inEmbeddingsPath.toFile(), true);
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String inEntry1, String inEntry2)
	{
		return !(inEntry1 == null || inEntry2 == null) && vectors.hasWord(inEntry1) && vectors.hasWord(inEntry2);
	}

	@Override
	public double computeSimilarity(String inEntry1, String inEntry2)
	{
		if (inEntry1.equals(inEntry2))
		{
			return 1.0;
		}
		if (!vectors.hasWord(inEntry1) || !vectors.hasWord(inEntry2))
		{
			return 0.0;
		}

		return vectors.similarity(inEntry1, inEntry2);
	}
}
