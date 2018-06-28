package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import de.jungblut.distance.CosineDistance;
import de.jungblut.glove.GloveRandomAccessReader;
import de.jungblut.glove.impl.GloveBinaryRandomAccessReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a binary
 * file and accessed with random access glove library: https://github.com/thomasjungblut/glove/blob/master/README.md
 */
public class RandomAccessVectorsSimilarity implements SimilarityFunction
{
	private final GloveRandomAccessReader db;
	private final CosineDistance cos = new CosineDistance();
	private AtomicLong num_defined = new AtomicLong(0);
	private AtomicLong num_undefined = new AtomicLong(0);
	private final static Logger log = LogManager.getLogger(TextVectorsSimilarity.class);

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
	public OptionalDouble computeSimilarity(String e1, String e2)
	{
		try
		{
			final OptionalDouble v = OptionalDouble.of(cos.measureDistance(db.get(e1), db.get(e2)));
			num_defined.incrementAndGet();
			return v;
		}
		catch (Exception e)
		{
			num_undefined.incrementAndGet();
			return OptionalDouble.empty();
		}
	}
}
