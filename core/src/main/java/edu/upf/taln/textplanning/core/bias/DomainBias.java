package edu.upf.taln.textplanning.core.bias;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.similarity.vectors.Vectors;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class DomainBias implements BiasFunction
{
	private static final int num_top_meanings = 3; // number of most similar domain meanings that will be used to calculate bias
	public final Map<String, Double> bias_values = new HashMap<>();
	private final static Logger log = LogManager.getLogger();


	public DomainBias(Collection<Candidate> candidates, Set<String> domain, Vectors glosses_vectors,
	                  BiFunction<double[], double[], Double> sim)
	{
		log.info("Calculating domain bias values using gloss vectors");
		final Stopwatch timer = Stopwatch.createStarted();

		// Determine glosses vector(s) representing domain
		final List<double[]> domain_vectors = domain.stream()
				.map(glosses_vectors::getVector)
				.flatMap(Optional::stream)
				.collect(toList());
//		final double[] domain_vector = IntStream.range(0, glosses_vectors.getNumDimensions())
//				.mapToDouble(i -> domain_vectors.stream()
//						.mapToDouble(v -> v[i])
//						.average().orElse(0.0))
//				.toArray();

		// Get list of meanings to be evaluated
		final List<String> meanings = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());

		// Calculate distance between glosses vectors of each meaning and the domain
		DebugUtils.ThreadReporter reporter = new DebugUtils.ThreadReporter(log);
		final double[] ws = meanings.parallelStream()
				.peek(i -> reporter.report())
				.map(glosses_vectors::getVector)
				.mapToDouble(v1 -> v1.map(doubles ->
						domain_vectors.stream()
								// average top similarity values
								.map(v2 -> sim.apply(doubles, v2))
								.sorted(Collections.reverseOrder())
								.limit(num_top_meanings)
								.mapToDouble(d -> d)
								.average()
								.orElse(0.0))
						.orElse(0.0))
				.toArray();

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
