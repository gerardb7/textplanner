package edu.upf.taln.textplanning.core.similarity.vectors;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

//import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
//import org.deeplearning4j.models.word2vec.Word2Vec;

/**
 *
 */
public class Word2VecVectors extends Vectors
{
//	private final Word2Vec vectors;
	private final static Logger log = LogManager.getLogger();

	public Word2VecVectors(Path vectors_file)
	{
		log.info("Loading vectors from " + vectors_file);
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
	public Optional<double[]> getVector(String item)
	{
		return Optional.empty();
	}

	@Override
	public int getNumDimensions()
	{
		return 0;
	}
}
