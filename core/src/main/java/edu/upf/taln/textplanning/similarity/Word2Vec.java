package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.datastructures.Entity;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Computes similarity between word forms according to Word2Vec distributional vectors
 */
public class Word2Vec implements EntitySimilarity
{
	private final WordVectors vectors;
	private final static Logger log = LoggerFactory.getLogger(Word2Vec.class);

	public Word2Vec(Path inEmbeddingsPath) throws IOException
	{
		log.info("Loading Word2Vec vectors");
		Stopwatch timer = Stopwatch.createStarted();
		vectors = WordVectorSerializer.loadGoogleModel(inEmbeddingsPath.toFile(), true);
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(Entity inItem)
	{
		return vectors.hasWord(inItem.getEntityLabel());
	}

	@Override
	public boolean isDefinedFor(Entity inItem1, Entity inItem2)
	{
		return vectors.hasWord(inItem1.getEntityLabel()) && vectors.hasWord(inItem2.getEntityLabel());
	}

	@Override
	public double computeSimilarity(Entity inItem1, Entity inItem2)
	{
		if (inItem1.equals(inItem2))
		{
			return 1.0;
		}
		if (!vectors.hasWord(inItem1.getEntityLabel()) || !vectors.hasWord(inItem2.getEntityLabel()))
		{
			return 0.0;
		}

		return vectors.similarity(inItem1.getEntityLabel(), inItem2.getEntityLabel());
	}
}
