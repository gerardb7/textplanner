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

public class RandomAccessFileVectors extends Vectors
{
	private final GloveRandomAccessReader db;
	private final int num_dimensions;
	private final static Logger log = LogManager.getLogger();

	public RandomAccessFileVectors(Path vectors_path, int num_dimensions) throws IOException
	{
		this.num_dimensions = num_dimensions;

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
			return Optional.ofNullable(db.get(item)).map(DoubleVector::toArray).or(this::getUnknownVector);
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
