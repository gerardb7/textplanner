package edu.upf.taln.textplanning.core.weighting;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.vectors.SentenceVectors;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContextWeighter implements WeightFunction, Serializable
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

		final List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());

		// Calculate context vectors just once per each context
		DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final double[] ws = meanings.parallelStream()
				.peek(i -> reporter.report())
				.mapToDouble(m ->
				{
					final Optional<double[]> glosses_vector = glosses_vectors.getVector(m);
					final List<String> context = context_function.apply(m);
					final Optional<double[]> context_vector = context_vectors.getVector(context);
					if (glosses_vector.isPresent() && context_vector.isPresent())
						return score_function.apply(glosses_vector.get(), context_vector.get());
					else
						return 0.0;
				}).toArray();

		IntStream.range(0, meanings.size())
				.forEach(i -> weights.put(meanings.get(i), ws[i]));

		// Make sure all weights are normalized
		assert this.weights.values().stream().map(Math::abs).allMatch(w -> w >= 0.0 && w <= 1.0);
		if (this.weights.values().stream().anyMatch(w -> w < 0.0))
			this.weights.keySet().forEach(i -> this.weights.replace(i, (this.weights.get(i) + 1.0)/2.0));

		final long num_weighted = this.weights.values().stream()
				.filter(w -> w != 0.0)
				.count();
		log.info(num_weighted + " meanings with weights out of " + meanings.size() + " (" + candidates.size() + " candidates)");
		log.info("Weights calculated in " + timer.stop());
	}

	@Override
	public Double apply(String item)
	{
		return weights.get(item);
	}

	@Override
	public boolean isDefined(String item)
	{
		return weights.containsKey(item);
	}
}
