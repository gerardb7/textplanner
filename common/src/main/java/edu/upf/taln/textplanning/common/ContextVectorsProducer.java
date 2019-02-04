package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import edu.upf.taln.textplanning.common.MeaningDictionary.Info;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.upf.taln.textplanning.core.utils.DebugUtils.LOGGING_STEP_SIZE;
import static java.util.stream.Collectors.toList;

public class ContextVectorsProducer
{
	private static final String separator = " ";
	private final static Logger log = LogManager.getLogger();

	// Utility method to create context vectors out of a file with meanings, an idf file and a set of word vectors
	public static void createVectors(Path meanings_path, int chunk_size, Path output_file, ResourcesFactory resources) throws Exception
	{
		log.info("Collecting context vectors with " +  Runtime.getRuntime().availableProcessors() + " cores available");
		final Stopwatch gtimer = Stopwatch.createStarted();

		final List<Info> meanings = getMeanings(meanings_path);
		final SentenceVectors sentence_vectors = resources.getSentenceVectors();

		AtomicLong num_vectors = new AtomicLong(0);

		Lists.partition(meanings, chunk_size).forEach(chunk -> {
			// each chunk returned by Guava is a view of the main list
			try
			{
				final List<Optional<double[]>> vectors = getVectors(chunk, sentence_vectors);
				final long num_vectors_chunk = writeVectors(vectors, chunk, output_file);
				num_vectors.addAndGet(num_vectors_chunk);
				System.gc(); // it would be nice to have more memory available
				log.debug("Using " + Runtime.getRuntime().totalMemory() + " out of " + Runtime.getRuntime().maxMemory());
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});

		log.info(num_vectors.get() + " vectors written to " + output_file);
		log.info("Collection completed in " + gtimer.stop());
	}



	private static List<Info> getMeanings(Path meanings_path) throws Exception
	{
		log.info("Collecting meanings");
		final Stopwatch timer = Stopwatch.createStarted();

		if (meanings_path.toFile().exists() && meanings_path.toFile().isFile())
		{
			log.info("Reading from file " + meanings_path);
			//noinspection unchecked
			List<Info> meanings = (List<Info>) Serializer.deserialize(meanings_path);
			log.info(meanings.size() + " with glosses read " + " in " + timer.stop());
			return meanings;
		}
		else
			throw new Exception("Invalid path to meanings file: " + meanings_path);
}



	private static List<Optional<double[]>> getVectors(List<Info> meanings, SentenceVectors sentence_vectors)
	{
		log.info("Creating embeddings for meanings");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		AtomicBoolean reported = new AtomicBoolean(false);

		final List<Optional<double[]>> vectors = meanings.stream()
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.peek(meaning -> {
					long i = counter.incrementAndGet();
					if (i % LOGGING_STEP_SIZE == 0) log.info(i + " meanings processed");
				})
				.map(meaning -> meaning.glosses.stream()
						.flatMap(g -> Arrays.stream(g.split("\\s")))
						.collect(toList()))
				.map(sentence_vectors::getVector)
				.collect(Collectors.toList());

		log.info(vectors.size() + " vectors created in " + timer.stop());
		return vectors;
	}

	private static long writeVectors(List<Optional<double[]>> vectors, List<Info> meanings, Path output_file)
	{
		log.info("Writing vectors to file " + output_file);
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		AtomicBoolean reported = new AtomicBoolean(false);

		final String text = IntStream.range(0, vectors.size())
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.filter(i -> vectors.get(i).isPresent())
				.peek(i -> {
					long c = counter.incrementAndGet();
					if (c % LOGGING_STEP_SIZE == 0) log.info(c + " vectors converted to strings");
				})
				.mapToObj(i ->
				{
					StringBuilder b = new StringBuilder();
					b.append(meanings.get(i).id);
					b.append(separator);
					//noinspection OptionalGetWithoutIsPresent
					b.append(Arrays.stream(vectors.get(i).get())
							.mapToObj(Double::toString)
							.collect(Collectors.joining(separator)));
					return b.toString();
				})
				.collect(Collectors.joining("\n", "", "\n"));
		FileUtils.appendTextToFile(output_file, text);
		log.info(counter.get() + " vectors written in " + timer.stop());

		return counter.get();
	}
}
