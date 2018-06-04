package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.utils.VectorsTextFileUtils;
import edu.upf.taln.textplanning.utils.VectorsTextFileUtils.Format;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

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

		//avg_sim = computeAverageSimilarity();
	}

	@Override
	public boolean isDefinedFor(String e) {	return vectors.containsKey(e); }
	@Override
	public boolean isDefinedFor(String e1, String e2)
	{
		return vectors.containsKey(e1) && vectors.containsKey(e2);
	}

	@Override
	public OptionalDouble computeSimilarity(String e1, String e2)
	{
		if (e1.equals(e2))
			return OptionalDouble.of(1.0);
		if (!isDefinedFor(e1, e2))
			return OptionalDouble.empty(); // todo consider throwing an exception or returning an optional

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
		double cosineSimilarity = dotProduct / magnitude; // range (-1,1)
		// Negative values indicate senses cooccur less than expected by chance
		// They seem to be unreliable, so replace with 0 (see https://web.stanford.edu/~jurafsky/slp3/15.pdf page 7)
		return OptionalDouble.of(Math.max(0.0, cosineSimilarity));
		//double distanceMetric = Math.acos(cosineSimilarity) / Math.PI; // range (0,1)
		//return (cosineSimilarity + 1.0) / 2.0;
	}

	@Override
	public double getAverageSimiliarity()
	{
		return 0.0;
	}

	@SuppressWarnings("unused")
	private double computeAverageSimilarity()
	{
		log.info("Computing average similarity for " + vectors.keySet().size() + " vectors");
		AtomicInteger counter = new AtomicInteger(0);
		int num_pairs = vectors.keySet().size() * vectors.keySet().size();
		Stopwatch timer = Stopwatch.createStarted();

		double average = vectors.keySet().stream()
				.map(r1 -> vectors.keySet().stream()
						.filter(r2 -> !r1.equals(r2))
						.map(r2 -> computeSimilarity(r1, r2))
						.mapToDouble(OptionalDouble::getAsDouble)
						.peek(v -> {
							if (counter.incrementAndGet() % 100000 == 0)
								log.info(counter.get() + " out of " + num_pairs);
						})
						.average())
				.mapToDouble(OptionalDouble::getAsDouble)
				.average()
				.orElse(0.0);

		log.info("Average similarity is " + average + ", computed in " + timer.stop());
		return average;
	}
}
