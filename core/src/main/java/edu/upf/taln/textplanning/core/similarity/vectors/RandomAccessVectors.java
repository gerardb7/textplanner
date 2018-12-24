package edu.upf.taln.textplanning.core.similarity.vectors;

import com.google.common.base.Stopwatch;
import de.jungblut.glove.GloveRandomAccessReader;
import de.jungblut.glove.impl.GloveBinaryRandomAccessReader;
import de.jungblut.math.DoubleVector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class RandomAccessVectors implements Vectors
{
	private final GloveRandomAccessReader db;
	private int num_dimensions = 300; // wild guess
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
	public Optional<double[]> getVector(String item)
	{
		try
		{
			return Optional.ofNullable(db.get(item)).map(DoubleVector::toArray);
			//v.ifPresent(a -> num_dimensions = a.length);
		}
		catch (IOException e)
		{
			log.error("Error reading vector: " + e);
			return Optional.empty();
		}
	}

	@Override
	public int getNumDimensions()
	{
		return num_dimensions;
	}


}
