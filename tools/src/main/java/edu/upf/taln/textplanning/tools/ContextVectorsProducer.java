package edu.upf.taln.textplanning.tools;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import edu.upf.taln.textplanning.common.FileUtils;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary.Info;
import edu.upf.taln.textplanning.common.InitialResourcesFactory;
import edu.upf.taln.textplanning.common.Serializer;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class ContextVectorsProducer
{
	private static final String separator = " ";
	private final static Logger log = LogManager.getLogger();

	// Utility method to create context vectors out of a file with meanings, an idf file and a set of word vectors
	public static void createVectors(Path meanings_path, int chunk_size, Path output_file, InitialResourcesFactory resources,
	                                 boolean glosses_only) throws Exception
	{
		log.info("Collecting context vectors with " +  Runtime.getRuntime().availableProcessors() + " cores available");
		final Stopwatch gtimer = Stopwatch.createStarted();

		final List<Info> meanings = getMeanings(meanings_path);
		final SentenceVectors sentence_vectors = resources.getSentenceVectors();
		getMeaningStats(meanings, sentence_vectors);

		AtomicLong num_vectors = new AtomicLong(0);

		Lists.partition(meanings, chunk_size).forEach(chunk -> {
			// each chunk returned by Guava is a view of the main list
			try
			{
				final List<Optional<double[]>> vectors = getVectors(chunk, sentence_vectors, glosses_only);
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


	private static void getMeaningStats(List<Info> meanings, SentenceVectors sentence_vectors)
	{
		final long num_meanings = meanings.size();
		final long num_meanings_with_lemmas = meanings.stream()
				.filter(i -> !i.lemmas.isEmpty())
				.count();
		final long num_lemmas = meanings.stream()
				.mapToLong(i -> i.lemmas.size())
				.sum();
		final long num_empty_lemmas = meanings.stream()
				.mapToLong(i -> i.lemmas.stream()
						.filter(String::isEmpty)
						.count())
				.sum();
		final int num_tokens_lemmas = meanings.stream()
				.mapToInt(i -> i.lemmas.stream()
						.mapToInt(l -> l.split("\\s").length)
						.sum())
				.sum();
		final double avg_tokens_lemmas = meanings.stream()
				.map(i -> i.lemmas.stream()
						.filter(l -> !l.isEmpty())
						.mapToInt(l -> l.split("_").length)
						.average())
				.filter(OptionalDouble::isPresent)
				.mapToDouble(OptionalDouble::getAsDouble)
				.average().orElse(0.0);
		final Set<List<String>> meanings_lemmas_tokens = meanings.stream()
				.map(i -> i.lemmas)
				.collect(toSet());
		final long num_meanings_with_defined_vectors_lemmas = meanings_lemmas_tokens.stream()
				.filter(sentence_vectors::isDefinedFor)
				.count();

		final long num_meanings_with_glosses = meanings.stream()
				.filter(i -> !i.glosses.isEmpty())
				.count();
		final long num_glosses = meanings.stream()
				.mapToLong(i -> i.glosses.size())
				.sum();
		final long num_empty_glosses = meanings.stream()
				.mapToLong(i -> i.glosses.stream()
						.filter(String::isEmpty)
						.count())
				.sum();
		final int num_tokens_glosses = meanings.stream()
				.mapToInt(i -> i.glosses.stream()
						.mapToInt(l -> l.split("\\s").length)
						.sum())
				.sum();
		final double avg_tokens_glosses = meanings.stream()
				.map(i -> i.glosses.stream()
						.filter(l -> !l.isEmpty())
						.mapToInt(l -> l.split("\\s").length)
						.average())
				.filter(OptionalDouble::isPresent)
				.mapToDouble(OptionalDouble::getAsDouble)
				.average().orElse(0.0);
		final Set<List<String>> meanings_glosses_tokens = meanings.stream()
				.map(i -> i.glosses.stream()
						.flatMap(l -> Arrays.stream(l.split("\\s")))
						.collect(toList()))
				.collect(toSet());
		final long num_meanings_with_defined_vectors_glosses = meanings_glosses_tokens.stream()
				.filter(sentence_vectors::isDefinedFor)
				.count();

		final long num_meanings_with_lemmas_or_glosses = meanings.stream()
				.filter(i -> !i.lemmas.isEmpty() || !i.glosses.isEmpty())
				.count();
		final Set<List<String>> meanings_tokens = meanings.stream()
				.map(i ->
				{
					final List<String> tokens = i.glosses.stream()
							.flatMap(l -> Arrays.stream(l.split("\\s")))
							.collect(toList());
					tokens.addAll(i.lemmas);
					return tokens;
				})
				.collect(toSet());
		final long num_meanings_with_defined_vectors = meanings_tokens.stream()
				.filter(sentence_vectors::isDefinedFor)
				.count();

		log.info("Stats:\n\tnum meanings " + num_meanings +
				"\n\t num_meanings_with_lemmas " + num_meanings_with_lemmas +
				"\n\t num_lemmas " + num_lemmas +
				"\n\t num_empty_lemmas " + num_empty_lemmas +
				"\n\t num_tokens_lemmas (split by '_') " + num_tokens_lemmas +
				"\n\t avg_tokens_lemmas (split by '_') " + avg_tokens_lemmas +
				"\n\t num_meanings_with_defined_vectors_lemmas " + num_meanings_with_defined_vectors_lemmas +
				"\n" +
				"\n\t num_meanings_with_glosses " + num_meanings_with_glosses +
				"\n\t num_glosses " + num_glosses +
				"\n\t num_empty_glosses " + num_empty_glosses +
				"\n\t num_tokens_glosses " + num_tokens_glosses +
				"\n\t avg_tokens_glosses " + avg_tokens_glosses +
				"\n\t num_meanings_with_defined_vectors_glosses " + num_meanings_with_defined_vectors_glosses +
				"\n" +
				"\n\t num_meanings_with_lemmas_or_glosses " + num_meanings_with_lemmas_or_glosses +
				"\n\t num_meanings_with_defined_vectors " + num_meanings_with_defined_vectors
		);
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
			log.info(meanings.size() + " meanings read " + " in " + timer.stop());
			return meanings;
		}
		else
			throw new Exception("Invalid path to meanings file: " + meanings_path);
	}

	private static List<Optional<double[]>> getVectors(List<Info> meanings, SentenceVectors sentence_vectors, boolean glosses_only)
	{
		log.info("Creating embeddings for meanings");
		final Stopwatch timer = Stopwatch.createStarted();
//		AtomicLong counter = new AtomicLong(0);
		AtomicBoolean reported = new AtomicBoolean(false);

		final List<Optional<double[]>> vectors = meanings.stream()
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
//				.peek(meaning -> {
//					long i = counter.incrementAndGet();
//					if (i % LOGGING_STEP_SIZE == 0) log.info(i + " meanings processed");
//				})
				.map(meaning -> getMeaningTokens(meaning, glosses_only))
				.filter(token_list -> !token_list.isEmpty())
				.filter(sentence_vectors::isDefinedFor)
				.map(sentence_vectors::getVector)
				.collect(Collectors.toList());

		log.info(vectors.size() + " vectors created in " + timer.stop());
		return vectors;
	}

	private static List<String> getMeaningTokens(Info meaning, boolean glosses_only)
	{
		// we keep duplicate tokens as repetitions should be reflected in the resulting context vector
		final List<String> tokens = meaning.glosses.stream()
				.flatMap(g -> Arrays.stream(g.split("\\s")))
				.collect(toList());
		if (!glosses_only)
			meaning.lemmas.stream()
					.flatMap(g -> Arrays.stream(g.split("\\s")))
					.forEach(tokens::add);

		return tokens;
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
					if (c % DebugUtils.LOGGING_STEP_SIZE == 0) log.info(c + " vectors converted to strings");
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
