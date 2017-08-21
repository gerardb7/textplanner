package edu.upf.taln.textplanning.similarity;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.utils.EmbeddingUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Computes similarity between word senses according to SenseEmbed distributional vectors
 */
public class SensEmbed implements EntitySimilarity
{
	private final ImmutableMap<String, double[]> vectors;
	private final static double avg_sim = 0.041157715074586806;
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

		//avg_sim = computeAverageSimilarity();
	}

	@Override
	public boolean isDefinedFor(Entity e)
	{
		return vectors.containsKey(e.getId());
	}

	@Override
	public boolean isDefinedFor(Entity e1, Entity e2)
	{
		return vectors.containsKey(e1.getId()) && vectors.containsKey(e2.getId());
	}

	@Override
	public OptionalDouble computeSimilarity(Entity e1, Entity e2)
	{
		if (e1 == e2 || e1.getId().equals(e2.getId()))
			return OptionalDouble.of(1.0);
		if (!isDefinedFor(e1, e2))
			return OptionalDouble.empty(); // todo consider throwing an exception or returning an optional

		double[] v1 = vectors.get(e1.getId());
		double[] v2 = vectors.get(e2.getId());

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
		return avg_sim;
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
						.map(r2 -> computeSimilarity(Entity.get(r1, r1, Candidate.Type.Other), Entity.get(r2, r2, Candidate.Type.Other)))
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
