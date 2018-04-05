package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.OptionalDouble;

public class Word2VecSimilarity implements MeaningSimilarity
{
	private final Word2Vec word2Vec;
	private final static double avg_sim = 0.041157715074586806;
	private final static Logger log = LoggerFactory.getLogger(SensEmbed.class);

	public Word2VecSimilarity(Path embeddings)
	{
		Stopwatch timer = Stopwatch.createStarted();
		word2Vec = WordVectorSerializer.readWord2VecModel(embeddings.toFile());
		log.info("Word2Vec vectors read in " + timer.stop());

	}
	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return word2Vec.hasWord(e1) && word2Vec.hasWord(e2);
	}

	@Override
	public OptionalDouble computeSimilarity(String e1, String e2)
	{
		if (e1.equals(e2))
			return OptionalDouble.of(1.0);

		double s = word2Vec.similarity(e1, e2);
		if (s == Double.NaN)
			return OptionalDouble.empty();
		else
			return OptionalDouble.of(s);
	}

	@Override
	public double getAverageSimiliarity()
	{
		return avg_sim;
	}
}
