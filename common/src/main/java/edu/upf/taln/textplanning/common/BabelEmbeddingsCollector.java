package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.common.MeaningDictionary.Info;
import edu.upf.taln.textplanning.core.similarity.vectors.SIFVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class BabelEmbeddingsCollector
{
	public static final ULocale language = ULocale.ENGLISH;
	public static final int NUM_DIMENSIONS = 300;
	public static final int LOGGING_STEP_SIZE = 100000;
	public static final String MEANINGS_FILENAME = "meanings.bin";
	private final static Logger log = LogManager.getLogger();

	public static void collectVectors(Path meanings_path, int chunk_size, Path vectors_path, Vectors.VectorType vectors_type,
	                                  Path idf_file, Path output_file) throws Exception
	{
		log.info("Collecting context vectors with " +  Runtime.getRuntime().availableProcessors() + " cores available");
		final Stopwatch gtimer = Stopwatch.createStarted();

		final List<Info> meanings = getMeanings(meanings_path, output_file);
		final Map<String, Double> weights = getWeights(idf_file);
		final Double default_weight = Collections.min(weights.values());

		Vectors word_vectors = Vectors.get(vectors_path, vectors_type, NUM_DIMENSIONS);
		SIFVectors sif_vectors = new SIFVectors(word_vectors, s -> weights.getOrDefault(s, default_weight));
		AtomicLong num_vectors = new AtomicLong(0);

		Lists.partition(meanings, chunk_size).forEach(chunk -> {
			// each chunk returned by Guava is a view of the main list
			try
			{
				final List<Optional<double[]>> vectors = getVectors(chunk, sif_vectors);
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

//		log.info("Calculating similarity scores");
//		timer.reset(); timer.start();
//		counter.set(0);
//		final Map<Pair<Integer, Integer>, Double> sim_values = IntStream.range(0, meanings.size())
//				.parallel()
//				.mapToObj(i -> IntStream.range(i + 1, meanings.size())
//						.filter(j -> i != j)
//						.mapToObj(j -> Pair.of(i, j))
//						.collect(toList()))
//				.flatMap(List::stream)
//				.peek(p -> {
//					long i = counter.incrementAndGet();
//					if (i % LOGGING_STEP_SIZE == 0) log.info(i + " pair scores computed");
//				})
//				.collect(toMap(p -> p, p ->
//				{
//					final String id1 = meanings.get(p.getLeft()).id;
//					final String id2 = meanings.get(p.getRight()).id;
//					final double[] v1 = vectors.get(id1);
//					final double[] v2 = vectors.get(id2);
//					if (v1 == null || v2 == null)
//						return Double.NaN;
//					return context_similarity_function.getFunction().score(v1, v2);
//				}));
//
//		sim_values.keySet().stream()
//				.filter(p -> !Double.isNaN(sim_values.get(p)))
//				.sorted(Comparator.comparingDouble(sim_values::get).reversed())
//				.forEach(p -> log.info(meanings.get(p.getLeft()) + ", " + meanings.get(p.getRight()) + " = " + sim_values.get(p)));
	}

	private static List<Info> getMeanings(Path meanings_path, Path output_file) throws Exception
	{
		log.info("Collecting meanings");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		AtomicBoolean reported = new AtomicBoolean(false);

		List<Info> meanings;
		if (meanings_path.toFile().exists() && meanings_path.toFile().isFile())
		{
			log.info("Reading from file " + meanings_path);
			//noinspection unchecked
			meanings = (List<Info>) Serializer.deserialize(meanings_path);
			log.info(meanings.size() + " with glosses read " + " in " + timer.stop());
		}
		else if (meanings_path.toFile().exists() && meanings_path.toFile().isDirectory())
		{
			MeaningDictionary bn = new BabelNetDictionary(meanings_path);
			AtomicLong total_ids = new AtomicLong(0);

			Iterable<Info> iterable = () -> bn.infoIterator(language);
			meanings = StreamSupport.stream(iterable.spliterator(), true)
					.parallel()
					.peek(l ->
					{
						if (!reported.getAndSet(true))
							log.info("Number of threads: " + Thread.activeCount());
					})
					.peek(id -> total_ids.incrementAndGet())
					.filter(i -> !i.glosses.isEmpty())
					.peek(id ->
					{
						long i = counter.incrementAndGet();
						if (i % LOGGING_STEP_SIZE == 0) log.info(i + " meanings collected");
					})
					.collect(toList());

			log.info(meanings.size() + " with glosses collected in " + timer.stop() + "(out of " + total_ids + " synsets queried)");
			final Path meanings_file = output_file.getParent().resolve(MEANINGS_FILENAME);
			Serializer.serialize(meanings, meanings_file);
			log.info("Meanings serialized to " + meanings_file);
		}
		else
			throw new Exception("A path must be provided to the BabelNet config folder or a meanings file: " + meanings_path);

		return meanings;
	}

	private static Map<String, Double> getWeights(Path weights_file)
	{
		log.info("Reading idf scores");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		AtomicBoolean reported = new AtomicBoolean(false);

		Map<String, Double> weights = Arrays.stream(FileUtils.readTextFile(weights_file).split("\n"))
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.map(l -> l.split(" "))
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % LOGGING_STEP_SIZE == 0) log.info(i + " weights read");
				})
				.collect(toMap(a -> a[0], a -> Double.valueOf(a[1])));
		log.info(weights.size() + " weights read in " + timer.stop());

		return weights;
	}

	private static List<Optional<double[]>> getVectors(List<Info> meanings, SIFVectors sif_vectors)
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
				.map(meaning -> String.join(" \n", meaning.glosses))
				.map(sif_vectors::getVector)
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
					b.append(meanings.get(i));
					b.append('\t');
					//noinspection OptionalGetWithoutIsPresent
					b.append(Arrays.stream(vectors.get(i).get())
							.mapToObj(Double::toString)
							.collect(Collectors.joining("\t")));
					return b.toString();
				})
				.collect(Collectors.joining("\n"));
		FileUtils.appendTextToFile(output_file, text);
		log.info(counter.get() + " vectors written in " + timer.stop());

		return counter.get();
	}

	public static void main(String[] args) throws Exception
	{
		// meanings_path, vectors_path, vectors_type, idf_file, output_file)
		collectVectors(Paths.get(args[0]), Integer.valueOf(args[1]), Paths.get(args[2]), Vectors.VectorType.valueOf(args[3]),  Paths.get(args[4]), Paths.get(args[5]));
	}
}
