package edu.upf.taln.textplanning.core.weighting;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class Context
{
	public final List<String> meanings;
	public final List<Optional<double[]>> meaning_context_vectors;
	public final List<Optional<double[]>> mention_context_vectors;
	public final BiFunction<double[], double[], Double> score_function;
	private final static Logger log = LogManager.getLogger();

	public Context( Collection<Candidate> meanings,
					Vectors meaning_context_vectors_producer,
	                SentenceVectors mention_context_vectors_producer,
	                Function<String, List<String>> context_function,
	                BiFunction<double[], double[], Double> score_function)
	{
		log.info("Setting up vectors for context-based weighting of meanings");
		final Stopwatch timer = Stopwatch.createStarted();

		log.info("Retrieving vector for meanings");
		this.meanings = meanings.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toList());

		meaning_context_vectors = this.meanings.stream()
				.map(meaning_context_vectors_producer::getVector)
				.collect(toList());

		{
			final long num_defined_vectors = meaning_context_vectors.stream()
					.filter(Optional::isPresent)
					.count();
			log.info(num_defined_vectors + " defined meaning context vectors out of " + meaning_context_vectors.size());
		}

		// Calculate context vectors just once per each context
		log.info("Calculating vector for contexts");
		final Map<String, List<String>> contexts = this.meanings.stream()
				.collect(Collectors.toMap(m -> m, context_function, (c1, c2) -> c1));
		final Map<List<String>, Optional<double[]>> vectors = contexts.values().stream()
				.distinct()
				.collect(Collectors.toMap(c -> c,
						mention_context_vectors_producer::getVector));

		mention_context_vectors = this.meanings.stream()
				.map(contexts::get)
				.map(vectors::get)
				.collect(toList());

		this.score_function = score_function;
		log.info("Set up completed in " + timer.stop());
	}

	public double weight(String item)
	{
		final int i = meanings.indexOf(item);
		final Optional<double[]> meaning_context_vector = meaning_context_vectors.get(i);
		final Optional<double[]> mention_context_vector = mention_context_vectors.get(i);
		if (!meaning_context_vector.isPresent() || !mention_context_vector.isPresent())
			return 0.0;
		return score_function.apply(meaning_context_vector.get(), mention_context_vector.get());
	}
}
