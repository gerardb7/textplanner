package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunctionFactory;
import edu.upf.taln.textplanning.core.similarity.VectorsSIFSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static edu.upf.taln.textplanning.core.similarity.SimilarityFunctionFactory.FunctionType.SIF;
import static edu.upf.taln.textplanning.core.similarity.SimilarityFunctionFactory.VectorType.Binary_RandomAccess;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class BabelEmbeddingsCollector
{
	private final static Logger log = LogManager.getLogger();

	public static void collectVectors(Path babel_config, Path vectors_path, Path idf_file, Path output_file) throws Exception
	{
		BabelNetWrapper bn = new BabelNetWrapper(babel_config);

		log.info("Reading idf scores");
		final Stopwatch timer = Stopwatch.createStarted();
		AtomicLong counter = new AtomicLong(0);

		final Map<String, Double> weights = Arrays.stream(FileUtils.readTextFile(idf_file).split("\n"))
				.map(l -> l.split(" "))
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % 10000 == 0) log.info(i + " weights read");
				})
				.collect(toMap(a -> a[0], a -> Double.valueOf(a[1])));
		final Double default_weight = Collections.min(weights.values());
		log.info(weights.size() + " weights read in " + timer.stop());

		log.info("Collecting synset ids");
		timer.reset(); timer.start();
		counter.set(0);

		Iterable<String> iterable = bn::getSynsetIterator;
		List<String> ids =StreamSupport.stream(iterable.spliterator(), true)
				.limit(10000)
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % 1000 == 0) log.info(i + " ids collected");
				})
				.collect(toList());
		log.info(ids.size() + " ids collected in " + timer.stop());

		log.info("Creating embeddings for glosses");
		timer.reset(); timer.start();
		counter.set(0);

		final VectorsSIFSimilarity sim = (VectorsSIFSimilarity)SimilarityFunctionFactory.get(SIF, vectors_path,
				Binary_RandomAccess, s -> weights.getOrDefault(s, default_weight));
		final Map<String, double[]> vectors = ids.stream()
				.parallel()
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % 1000 == 0) log.info(i + " synsets processed");
				})
				.map(id -> Pair.of(id, bn.getGlosses(id)))
				.filter(p -> !p.getRight().isEmpty())
				.collect(Collectors.toMap(Pair::getLeft,
						p ->
						{
							final List<String> glosses = p.getRight();
							final List<String> tokens = glosses.stream()
									.flatMap(g -> Arrays.stream(g.split(" ")))
									.collect(toList());
							return sim.getVector(tokens);
						}));
		log.info(vectors.size() + " vectors created in " + timer.stop());

		log.info("Writting embeddings to file");
		timer.reset(); timer.start();
		counter.set(0);

		final String text = vectors.entrySet().stream()
				.parallel()
				.peek(id -> {
					long i = counter.incrementAndGet();
					if (i % 1000 == 0) log.info(i + " vectors converted to strings");
				})
				.map(e ->
				{
					StringBuilder b = new StringBuilder();
					b.append(e.getKey());
					b.append('\t');
					b.append(Arrays.stream(e.getValue())
							.mapToObj(Double::toString)
							.collect(Collectors.joining("\t")));
					return b.toString();
				})
				.collect(Collectors.joining("\n"));
		FileUtils.writeTextToFile(output_file, text);
		log.info("Embeddings written in " + timer.stop());
	}

	public static void main(String[] args) throws Exception
	{
		collectVectors(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]));
	}
}
