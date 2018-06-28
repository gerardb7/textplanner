package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.utils.VectorsTextFileUtils;
import edu.upf.taln.textplanning.utils.VectorsTextFileUtils.Format;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Map;

/**
 * Computes similarity between items according to precomputed distributional vectors (embeddings) stored in a text
 * file and loaded as whole into memory.
 */
public class TextVectorsSimilarity implements SimilarityFunction
{

	private final Map<String, double[]> vectors;
	//private final static double avg_sim = 0.041157715074586806;
	private final static Logger log = LogManager.getLogger(TextVectorsSimilarity.class);

	public TextVectorsSimilarity(Path vectors_path, Format format) throws Exception
	{
		log.info("Loading vectors from text file");
		Stopwatch timer = Stopwatch.createStarted();
		vectors = VectorsTextFileUtils.readVectorsFromFile(vectors_path, format);
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
}
