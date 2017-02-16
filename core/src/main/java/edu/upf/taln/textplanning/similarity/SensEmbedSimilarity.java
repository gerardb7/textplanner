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
public class SensEmbedSimilarity implements ItemSimilarity
{
	private final ImmutableMap<String, double[]> vectors;
	private final static Logger log = LoggerFactory.getLogger(SensEmbedSimilarity.class);

	public SensEmbedSimilarity(Path inEmbeddingsPath) throws Exception
	{
		log.info("Loading SenseEmbed vectors");
		Stopwatch timer = Stopwatch.createStarted();
		Map<String, List<double[]>> embeddings = EmbeddingUtils.parseEmbeddingsFile(inEmbeddingsPath, true);
		if (embeddings.values().stream().anyMatch(l -> l.size() != 1))
			throw new Exception("Embedding file must contain a single vector per entry");
		Map<String, double[]> avgEmbeddings = embeddings.entrySet().stream()
				.map(e -> Pair.of(e.getKey(), e.getValue().get(0)))
				.collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

		ImmutableMap.Builder<String, double[]> builder = ImmutableMap.builder();
		vectors = builder.putAll(avgEmbeddings).build();
		log.info("Loading took " + timer.stop());
	}

	@Override
	public boolean isDefinedFor(String inEntry1, String inEntry2)
	{
		if (inEntry1 == null || inEntry2 == null)
		{
			return false;
		}
		String e1 = normalizeEntry(inEntry1);
		String e2 = normalizeEntry(inEntry2);
		return vectors.containsKey(e1) || vectors.containsKey(e2);
	}

	public double computeSimilarity(String inEntry1, String inEntry2)
	{
		if (inEntry1.equals(inEntry2))
			return 1.0;
		String e1 = normalizeEntry(inEntry1);
		String e2 = normalizeEntry(inEntry2);
		if (!vectors.containsKey(e1) || !vectors.containsKey(e2))
			return 0.0;

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

		double cosineSimilarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)); // range (-1,1)
		double distanceMetric = Math.acos(cosineSimilarity) / Math.PI; // range (0,1)
		return 1.0 - distanceMetric;
	}

	private String normalizeEntry(String inEntry)
	{
		// Normalize BabelNet ids
		String e = inEntry;
		if (e.startsWith("s"))
		{
			e = "bn:" + e.substring(1, e.length());
		}
		if (!e.startsWith("bn:"))
		{
			e = "bn:" + e;
		}

		return e;
	}
}
