package edu.upf.taln.textplanning.core.weighting;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ContextWeighter implements Function<String, Double>, Serializable
{
	public final Map<String, Double> weights = new HashMap<>();
	private final static Logger log = LogManager.getLogger();
	private final static long serialVersionUID = 1L;

	public ContextWeighter(Collection<Candidate> candidates,
	                       Vectors glosses_vectors,
	                       SentenceVectors context_vectors,
	                       Function<String, List<String>> context_function,
	                       BiFunction<double[], double[], Double> score_function)
	{
		log.info("Calculating meaning weights using gloss and context vectors");
		final Stopwatch timer = Stopwatch.createStarted();

		final Set<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toSet());

		// Calculate context vectors just once per each context
		meanings.forEach(m -> {
					final Optional<double[]> glosses_vector = glosses_vectors.getVector(m);
					final List<String> context = context_function.apply(m);
					final Optional<double[]> context_vector =context_vectors.getVector(context);
					if (glosses_vector.isPresent() && context_vector.isPresent())
						weights.put(m, score_function.apply(glosses_vector.get(), context_vector.get()));
					else
						weights.put(m, 0.0);

				});

		// Make sure all weights are normalized
		assert weights.values().stream().map(Math::abs).allMatch(w -> w >= 0.0 && w <= 1.0);
		if (weights.values().stream().anyMatch(w -> w < 0.0))
			weights.keySet().forEach(i -> weights.replace(i, (weights.get(i) + 1.0)/2.0));


		log.info(weights.size() + " meanings with weights out of " + candidates.size());
		log.info("Set up completed in " + timer.stop());
	}

	@Override
	public Double apply(String item)
	{
		return weights.get(item);
	}
}
