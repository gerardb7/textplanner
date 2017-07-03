package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import edu.upf.taln.textplanning.utils.EmbeddingUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes similarity between word senses according to SenseEmbed distributional vectors
 */
public class SensEmbed implements ItemSimilarity
{
	private final ImmutableMap<String, double[]> vectors;
	private final static Logger log = LoggerFactory.getLogger(SensEmbed.class);

	public SensEmbed(Path inEmbeddingsPath) throws Exception
	{
		log.info("Loading SenseEmbed vectors");
		Stopwatch timer = Stopwatch.createStarted();

		// If file contains merged sense vectors, ignore vectors for words, if any.
		// If file contains word-sense pairs, do not ignore words.
		Map<String, List<double[]>> embeddings = EmbeddingUtils.parseEmbeddingsFile(inEmbeddingsPath, true, true);
		embeddings.entrySet().stream()
				.filter(e -> e.getValue().size() != 1)
				.forEach(e -> log.error("Sense " + e.getKey() + " has " + e.getValue() + " vectors"));
		Map<String, double[]> avgEmbeddings = embeddings.entrySet().stream()
				.map(e -> Pair.of(e.getKey(), e.getValue().get(0)))
				.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

		ImmutableMap.Builder<String, double[]> builder = ImmutableMap.builder();
		vectors = builder.putAll(avgEmbeddings).build();
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String i)
	{
		return vectors.containsKey(i);
	}

	@Override
	public boolean isDefinedFor(String item1, String item2)
	{
		return vectors.containsKey(item1) && vectors.containsKey(item2);
	}

	@Override
	public double computeSimilarity(String item1, String item2)
	{
		if (item1.equals(item2))
			return 1.0;
		if (!isDefinedFor(item1, item2))
			return 0.0;

		double[] v1 = vectors.get(item1);
		double[] v2 = vectors.get(item2);

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
		return Math.max(0.0, cosineSimilarity);
		//double distanceMetric = Math.acos(cosineSimilarity) / Math.PI; // range (0,1)
		//return (cosineSimilarity + 1.0) / 2.0;
	}
}
