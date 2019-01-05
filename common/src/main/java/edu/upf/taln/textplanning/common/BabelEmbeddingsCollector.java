package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
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

import static edu.upf.taln.textplanning.core.similarity.vectors.Vectors.VectorType.Binary_RandomAccess;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class BabelEmbeddingsCollector
{
	public static final ULocale language = ULocale.ENGLISH;
	private final static long limit = 1000;
	public static final int step_size = 10000; // for logging purposes
	public static final String meanings_file = "meanings.bin";
	private final static Logger log = LogManager.getLogger();

	public static void collectVectors(Path babel_config, Path vectors_path, Path idf_file, Path output_file) throws Exception
	{
		log.info("Collecting context vectors with " +  Runtime.getRuntime().availableProcessors() + " cores available");
		log.info("Reading idf scores");
		final Stopwatch gtimer = Stopwatch.createStarted();
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);
		AtomicBoolean reported = new AtomicBoolean(false);

		final Map<String, Double> weights = Arrays.stream(FileUtils.readTextFile(idf_file).split("\n"))
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.map(l -> l.split(" "))
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % step_size == 0) log.info(i + " weights read");
				})
				.collect(toMap(a -> a[0], a -> Double.valueOf(a[1])));
		final Double default_weight = Collections.min(weights.values());
		log.info(weights.size() + " weights read in " + timer.stop());

		Vectors word_vectors = Vectors.get(vectors_path, Binary_RandomAccess, 300);
		SIFVectors sif_vectors = new SIFVectors(word_vectors, s -> weights.getOrDefault(s, default_weight));
		MeaningDictionary bn = new BabelNetDictionary(babel_config);

		log.info("Collecting meanings");
		timer.reset(); timer.start();
		counter.set(0);
		AtomicLong total_ids = new AtomicLong(0);
		reported.set(false);

		Iterable<Info> iterable = () -> bn.infoIterator(language);
		final List<Info> meanings = StreamSupport.stream(iterable.spliterator(), true)
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.peek(id -> total_ids.incrementAndGet())
				.filter(i -> !i.glosses.isEmpty())
				.peek(id ->
				{
					long i = counter.incrementAndGet();
					if (i % step_size == 0) log.info(i + " meanings collected");
				})
//				.limit(limit)
				.collect(toList());
		log.info(meanings.size() + " with glosses collected in " + timer.stop() + "(out of " + total_ids + " synsets queried)");
		final Path meanings_path = output_file.getParent().resolve(meanings_file);
		Serializer.serialize(meanings, meanings_path);
//		final List<Info> meanings = (List<Info>) Serializer.deserialize(meanings_path);

		log.info("Creating embeddings for meanings");
		timer.reset(); timer.start();
		counter.set(0);
		reported.set(false);

		final List<Optional<double[]>> vectors = meanings.stream()
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.peek(meaning -> {
					long i = counter.incrementAndGet();
					if (i % step_size == 0) log.info(i + " meanings processed");
				})
				.map(meaning -> String.join(" \n", meaning.glosses))
				.map(sif_vectors::getVector)
				.collect(Collectors.toList());

		log.info(vectors.size() + " vectors created in " + timer.stop());

		log.info("Writing vectors to file");
		timer.reset(); timer.start();
		counter.set(0);
		reported.set(false);

		final String text = IntStream.range(0, vectors.size())
				.parallel()
				.peek(l -> {
					if (!reported.getAndSet(true))
						log.info("Number of threads: " + Thread.activeCount());
				})
				.filter(i -> vectors.get(i).isPresent())
				.peek(i -> {
					long c = counter.incrementAndGet();
					if (c % step_size == 0) log.info(c + " vectors converted to strings");
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
		FileUtils.writeTextToFile(output_file, text);
		log.info(counter.get() + " vectors written in " + timer.stop());
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
//					if (i % step_size == 0) log.info(i + " pair scores computed");
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

	public static void main(String[] args) throws Exception
	{
		collectVectors(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
	}
}
