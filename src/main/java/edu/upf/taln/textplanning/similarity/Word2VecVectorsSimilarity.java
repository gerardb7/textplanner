package edu.upf.taln.textplanning.similarity;

import java.nio.file.Path;
import java.util.OptionalDouble;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

/**
 * Efficient loading of glove binary vectors based on:
 * https://github.com/spennihana/FasterWordEmbeddings
 *
 */
public class Word2VecVectorsSimilarity implements SimilarityFunction
{
	private final Word2Vec vectors;
	private final static Logger log = LogManager.getLogger(Word2VecVectorsSimilarity.class);


	public Word2VecVectorsSimilarity(Path vectors_file)
	{
		log.info("Loading vectors from binary files " + vectors_file.getFileName().toString());
		Stopwatch timer = Stopwatch.createStarted();
		vectors = WordVectorSerializer.readWord2VecModel(vectors_file.toFile());
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String e)
	{
		return vectors.hasWord(e);
	}

	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return isDefinedFor(e1) && isDefinedFor(e2);
	}

	@Override
	public OptionalDouble computeSimilarity(String e1, String e2)
	{
		if (!isDefinedFor(e1, e2))
			return OptionalDouble.empty();
		else if (e1.equals(e2))
			return OptionalDouble.of(1.0);

		return OptionalDouble.of(vectors.similarity(e1, e2));
	}
}
