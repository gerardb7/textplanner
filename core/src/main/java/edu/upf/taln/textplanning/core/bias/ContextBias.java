package edu.upf.taln.textplanning.core.bias;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContextBias implements BiasFunction, Serializable
{
	public final Map<String, Double> bias_values = new HashMap<>();
	private final static Logger log = LogManager.getLogger();
	private final static long serialVersionUID = 1L;

	public ContextBias(Collection<Candidate> candidates,
	                   Vectors glosses_vectors,
	                   SentenceVectors context_vectors,
	                   ContextFunction context_function,
	                   BiFunction<double[], double[], Double> sim)
	{
		log.info("Calculating context bias values using gloss and context vectors");
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
					final List<String> context = context_function.getContext(m);
					final Optional<double[]> context_vector = context_vectors.getVector(context);
					if (glosses_vector.isPresent() && context_vector.isPresent())
						return sim.apply(glosses_vector.get(), context_vector.get());
					else
						return 0.0;
				}).toArray();

		IntStream.range(0, meanings.size())
				.forEach(i -> bias_values.put(meanings.get(i), ws[i]));

		// Make sure all bias_values are normalized
		assert this.bias_values.values().stream().map(Math::abs).allMatch(w -> w >= 0.0 && w <= 1.0);
		if (this.bias_values.values().stream().anyMatch(w -> w < 0.0))
			this.bias_values.keySet().forEach(i -> this.bias_values.replace(i, (this.bias_values.get(i) + 1.0)/2.0));

		final long num_weighted = this.bias_values.values().stream()
				.filter(w -> w != 0.0)
				.count();
		log.info(num_weighted + " meanings with bias values out of " + meanings.size() + " (" + candidates.size() + " candidates)");
		log.info("Bias values calculated in " + timer.stop());
	}

	@Override
	public Double apply(String item)
	{
		return bias_values.get(item);
	}

	@Override
	public boolean isDefined(String item)
	{
		return bias_values.containsKey(item);
	}
}
