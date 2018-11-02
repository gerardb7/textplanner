package edu.upf.taln.textplanning.core.similarity;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.VectorsTypes.Format;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a text
 * file and loaded as whole into memory.
 */
public class TextVectorsSimilarity implements SimilarityFunction
{

	private final Map<String, double[]> vectors;
	//private final static double avg_sim = 0.041157715074586806;
	private final static Logger log = LogManager.getLogger();

	public static TextVectorsSimilarity create(Path vectors_path, Format format)
	{
		try
		{
			return new TextVectorsSimilarity(vectors_path, format);
		}
		catch (Exception e)
		{
			log.error("Cannot load vectors from " +  vectors_path + ": " + e);
			return null;
		}
	}

	private TextVectorsSimilarity(Path vectors_path, Format format) throws Exception
	{
		log.info("Loading vectors from text file");
		Stopwatch timer = Stopwatch.createStarted();
		vectors = readVectorsFromFile(vectors_path, format);
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String e) {	return vectors.containsKey(e); }
	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return vectors.containsKey(e1) && vectors.containsKey(e2);
	}

	@Override
	public double computeSimilarity(String e1, String e2)
	{
		double[] v1 = vectors.get(e1);
		double[] v2 = vectors.get(e2);

		double dotProduct = 0.0;
		double normA = 0.0;
		double normB = 0.0;
		for (int i = 0; i < v1.length; i++)
		{
			dotProduct += v1[i] * v2[i];
			normA += Math.pow(v1[i], 2);
			normB += Math.pow(v2[i], 2);
		}

		double magnitude = Math.sqrt(normA) * Math.sqrt(normB); // will normalize with magnitude in order to ignore it
		return dotProduct / magnitude; // range (-1,1)
	}


	/**
	 * Reads a text file containing distributional vectors.
	 */
	public static Map<String, double[]> readVectorsFromFile(Path vectors_file, Format format) throws Exception
	{
		if (format != Format.Text_Glove && format != Format.Text_Word2vec)
			throw new Exception("Format " + format + " not supported");

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
			++line_counter;
			try
			{
				// Determine number of vectors and dimensions
				String[] columns = line.split(" ");
				if (first_line)
				{
					if (format == Format.Text_Glove)
					{
						num_lines = Integer.parseInt(columns[0]);
						num_dimensions = Integer.parseInt(columns[1]);

						//skip to next line
						line = br.readLine();
						if (line == null)
							line = "";
						columns = line.split(" ");
						++line_counter;
					}
					else // if (format == Format.Text_Word2vec)
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
				log.info(line_counter + " lines parsed out of " + num_lines);

		}
		log.info("Parsing complete: " + vectors.size() + " vectors read from " + (num_lines - 1) + " lines");

		return vectors;
	}
}
