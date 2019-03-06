package edu.upf.taln.textplanning.core.similarity.vectors;

import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class TextVectors extends Vectors
{
	private final Map<String, double[]> vectors;
	private final static Logger log = LogManager.getLogger();

	public TextVectors(Path vectors_path, Vectors.VectorType vectorType) throws Exception
	{
		log.info("Loading vectors from " + vectors_path);
		Stopwatch timer = Stopwatch.createStarted();
		vectors = readVectorsFromFile(vectors_path, vectorType);
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return vectors.containsKey(item);
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		final Optional<double[]> v = Optional.ofNullable(vectors.get(item));
		if (v.isPresent())
			return v;
		else
			return getUnknownVector((i) -> Optional.ofNullable(vectors.get(i)));
	}

	@Override
	public int getNumDimensions()
	{
		return vectors.values().iterator().next().length;
	}

	/**
	 * Reads a text file containing distributional vectors.
	 */
	public static Map<String, double[]> readVectorsFromFile(Path vectors_file, Vectors.VectorType vectorType) throws Exception
	{
		if (vectorType != VectorType.Text_Glove && vectorType != VectorType.Text_Word2vec)
			throw new Exception("VectorType " + vectorType + " not supported");

		int num_lines = 0;
		int num_dimensions = 0;
		int line_counter = 0;

		HashMap<String, double[]> vectors = new HashMap<>();
		FileInputStream fs = new FileInputStream(vectors_file.toFile());
		InputStreamReader isr = new InputStreamReader(fs, StandardCharsets.UTF_8);
		BufferedReader br = new BufferedReader(isr);
		String line;
		boolean first_line = true;

		while ((line = br.readLine()) != null)
		{
			try
			{
				// Determine number of vectors and dimensions
				String[] columns = line.split(" ");
				if (first_line)
				{
					if (vectorType == VectorType.Text_Glove)
					{
						num_lines = Integer.parseInt(columns[0]);
						num_dimensions = Integer.parseInt(columns[1]);

						//skip to next line
						line = br.readLine();
						if (line == null)
							line = "";
						columns = line.split(" ");
					}
					else // if (vectorType == VectorType.Text_Word2vec)
					{
						try (LineNumberReader count = new LineNumberReader(new FileReader(vectors_file.toFile())))
						{
							while (count.skip(Long.MAX_VALUE) > 0) { }
							num_lines = count.getLineNumber() + 1;
						}
						num_dimensions = columns.length - 1;
					}

					first_line = false;
				}

				if (columns.length != num_dimensions + 1)
					throw new Exception("Cannot parse line " + line_counter + ": \"" + line + "\"");

				// For performance reasons, let's avoid regular expressions
				String item = columns[0];
				double[] vector = new double[num_dimensions];
				for (int i = 0; i < num_dimensions; ++i)
				{
					vector[i] = Double.parseDouble(columns[i + 1]);
				}

				if (vectors.containsKey(item))
					log.warn("Duplicate key " + item);
				vectors.put(item, vector);
			}
			catch(Exception e)
			{
				log.error(e);
			}

			if (++line_counter % 100000 == 0)
				log.info(line_counter + " lines parsed out of " + (num_lines -1));

		}
		log.info("Parsing complete: " + vectors.size() + " vectors read from " + (num_lines - 1) + " lines");

		return vectors;
	}
}
