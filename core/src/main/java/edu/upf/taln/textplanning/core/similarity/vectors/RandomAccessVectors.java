package edu.upf.taln.textplanning.core.similarity.vectors;

import com.google.common.base.Stopwatch;
import de.jungblut.glove.GloveRandomAccessReader;
import de.jungblut.glove.impl.GloveBinaryRandomAccessReader;
import de.jungblut.math.DoubleVector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

public class RandomAccessVectors implements Vectors
{
	private final GloveRandomAccessReader db;
	private final static Logger log = LogManager.getLogger();

	public RandomAccessVectors(Path vectors_path) throws IOException
	{
		log.info("Loading vectors from " + vectors_path);
		Stopwatch timer = Stopwatch.createStarted();
		db = new GloveBinaryRandomAccessReader(vectors_path);
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return db.contains(item);
	}

	@Override
	public double[] getVector(String item)
	{
		try
		{
			final DoubleVector vector = db.get(item);
			if (vector == null)
				return null;
			else
				return vector.toArray();
		}
		catch (IOException e)
		{
			log.error("Error reading vector: " + e);
			return null;
		}
	}
}
