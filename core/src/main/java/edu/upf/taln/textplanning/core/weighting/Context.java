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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class Context
{
	public final List<String> meanings;
	public final List<double[]> meaning_context_vectors;
	public final List<double[]> mention_context_vectors;
	public final BiFunction<double[], double[], Double> score_function;
	private final static Logger log = LogManager.getLogger();

	public Context( Collection<Candidate> contents,
					Vectors all_meaning_context_vectors,
	                SentenceVectors all_mention_context_vectors,
	                Function<String, List<String>> context_function,
	                BiFunction<double[], double[], Double> score_function)
	{
		log.info("Setting up vectors for context-based weighting of meanings");
		final Stopwatch timer = Stopwatch.createStarted();

		log.info("Retrieving vector for meanings");
		meanings = contents.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toList());

		meaning_context_vectors = meanings.stream()
				.map(c -> all_meaning_context_vectors.getVector(c).orElse(all_mention_context_vectors.getUnknownVector()))
				.collect(toList());

		// Calculate context vectors just once per each context
		log.info("Calculating vector for contexts");
		final Map<String, List<String>> contexts = meanings.stream()
				.collect(Collectors.toMap(m -> m, context_function, (c1, c2) -> c1));
		final Map<List<String>, double[]> vectors = contexts.values().stream()
				.distinct()
				.collect(Collectors.toMap(c -> c,
						c -> all_mention_context_vectors.getVector(c).orElse(all_mention_context_vectors.getUnknownVector())));

		mention_context_vectors = meanings.stream()
				.map(contexts::get)
				.map(vectors::get)
				.collect(toList());

		this.score_function = score_function;
		log.info("Set up completed in " + timer.stop());
	}

	public double weight(String item)
	{
		final int i = meanings.indexOf(item);
		final double[] meaning_context_vector = meaning_context_vectors.get(i);
		final double[] mention_context_vector = mention_context_vectors.get(i);
		return score_function.apply(meaning_context_vector, mention_context_vector);
	}
}
