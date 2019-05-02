package edu.upf.taln.textplanning.core.bias;

import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public class DomainBias implements BiasFunction
{
	private final Set<String> domain;
	private final SimilarityFunction sim;
	private final static Logger log = LogManager.getLogger();


	public DomainBias(Set<String> domain, SimilarityFunction sim)
	{
		this.domain = domain;
		this.sim = sim;

		final List<String> non_defined = domain.stream()
				.filter(not(sim::isDefined))
				.collect(Collectors.toList());
		if (!non_defined.isEmpty())
		{
			log.error("Similarity function undefined for " + domain);
			this.domain.removeAll(non_defined);
		}
	}

	@Override
	public boolean isDefined(String item)
	{
		return sim.isDefined(item);
	}

	@Override
	public Double apply(String item)
	{
		return domain.stream()
				.map(d -> sim.apply(item, d))
				.flatMapToDouble(OptionalDouble::stream)
				.average()
				.orElse(0.0);
	}
}
