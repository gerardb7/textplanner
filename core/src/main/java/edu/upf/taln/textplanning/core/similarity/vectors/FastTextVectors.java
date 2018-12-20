package edu.upf.taln.textplanning.core.similarity.vectors;

import com.github.jfasttext.JFastText;
import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class FastTextVectors implements Vectors
{
	private final JFastText jft = new JFastText();
	private final static Logger log = LogManager.getLogger();

	public FastTextVectors(Path model_path)
	{
		log.info("Loading model from " + model_path);
		Stopwatch timer = Stopwatch.createStarted();
		jft.loadModel(model_path.toString());
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return true;
	}

	@Override
	public double[] getVector(String item)
	{
		return jft.getVector(item).stream()
				.mapToDouble(Float::doubleValue)
				.toArray();
	}
}
