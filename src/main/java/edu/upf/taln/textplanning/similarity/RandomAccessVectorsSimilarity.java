package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import de.jungblut.distance.CosineDistance;
import de.jungblut.glove.GloveRandomAccessReader;
import de.jungblut.glove.impl.GloveBinaryRandomAccessReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a binary
 * file and accessed with random access glove library: https://github.com/thomasjungblut/glove/blob/master/README.md
 */
public class RandomAccessVectorsSimilarity implements SimilarityFunction
{
	private final GloveRandomAccessReader db;
	private final CosineDistance cos = new CosineDistance();
	private final static Logger log = LogManager.getLogger();

	public RandomAccessVectorsSimilarity(Path vectors_path) throws IOException
	{
		log.info("Loading vectors from binary files " + vectors_path.getFileName().toString());
		Stopwatch timer = Stopwatch.createStarted();
		db = new GloveBinaryRandomAccessReader(vectors_path);
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String e)
	{
		return db.contains(e);
	}
	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return db.contains(e1) && db.contains(e2);
	}

	@Override
	public double computeSimilarity(String e1, String e2)
	{
		try
		{
			return cos.measureDistance(db.get(e1), db.get(e2));
		}
		catch (Exception e)
		{
			return Double.NaN;
		}
	}
}
