package edu.upf.taln.textplanning.core.weighting;

import com.easemob.TextualSim;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.vectors.SIFVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class Context
{
	public final TextualSim sif;
	public final List<String> meanings;
	public final List<double[]> meaning_context_vectors;
	public final List<double[]> mention_context_vectors;
	private final static Logger log = LogManager.getLogger();

	public Context( Collection<Candidate> contents,
					Vectors all_meaning_context_vectors,
	                SIFVectors all_mention_context_vectors,
	                Function<String, String> context_function)
	{
		log.info("Setting up vectors for context-based meaning weighting");
		final Stopwatch timer = Stopwatch.createStarted();
		sif = all_mention_context_vectors.getFunction();
		meanings = contents.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toList());
		meaning_context_vectors = meanings.stream()
				.map(c -> all_meaning_context_vectors.getVector(c).orElse(all_mention_context_vectors.getUnknownVector()))
				.collect(toList());
		mention_context_vectors = meanings.stream()
				.map(context_function)
				.map(c -> all_mention_context_vectors.getVector(c).orElse(all_mention_context_vectors.getUnknownVector()))
				.collect(toList());
		log.info("Set up completed in " + timer.stop());
	}

	public double weight(String item)
	{
		final int i = meanings.indexOf(item);
		final double[] meaning_context_vector = meaning_context_vectors.get(i);
		final double[] mention_context_vector = mention_context_vectors.get(i);
		return sif.score(meaning_context_vector, mention_context_vector);
	}
}
