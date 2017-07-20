package edu.upf.taln.textplanning.optimization;

import cc.mallet.optimize.Optimizable;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.weighting.TFIDF;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.*;

/**
 * Creates and maintains a vector of parameter values, where parameters correspond to {mention, candidate entity} pairs.
 * This class also acts as a wrapper to specific functions that can be passed to optimization algorithms in Mallet.
 */
public class CandidateOptimizable implements Optimizable.ByGradientValue
{
	private final Function f; // function to optimize
	private final List<Candidate> candidates = new ArrayList<>(); // vector of candidates/parameters
	private final double[] params; // vector of parameter values
	private final Map<Mention, List<Candidate>> mentionsToCandidates;
	private final Map<Entity, List<Candidate>> entitiesToCandidates;

	public CandidateOptimizable(Function function, List<Candidate> candidates, TFIDF frequency)
	{
		this.f = function;
		this.candidates.addAll(candidates);

		// Initialize parameter vector with frequency priors
		params = candidates.stream()
				.map(Candidate::getEntity)
				.map(Entity::getId)
				.mapToDouble(frequency::getFrequency).toArray();

		// Create a dictionary mapping mentions to all its candidates
		mentionsToCandidates = candidates.stream()
				.collect(groupingBy(Candidate::getMention, toList()));

		// Create a dictionary mapping entities to all the mentions of which they are candidates
		entitiesToCandidates = candidates.stream()
				.collect(groupingBy(Candidate::getEntity, toList()));
	}

	@Override
	public double getValue()
	{
		Map<Candidate, Double> softMaxDistributions = getSoftMaxDistributions();
		double[] dist = candidates.stream()
				.mapToDouble(softMaxDistributions::get)
				.toArray();
		return f.getValue(dist);
	}

	@Override
	public void getValueGradient(double[] b)
	{
		f.getValueGradient(params, b);
	}

	public Candidate getEntityMentionPair(int i) { return candidates.get(i); }

	public void rankCandidates()
	{
		IntStream.range(0, params.length).forEach(i -> candidates.get(i).setValue(params[i]));
	}

	public List<Candidate> getRankedCandidates()
	{
		rankCandidates();
		return new ArrayList<>(candidates);
	}

	// @return list of candidates for a mention m ranked according to their current parameter values
	public List<Candidate> getRankedCandidates(Mention m)
	{
		rankCandidates();
		return this.mentionsToCandidates.get(m).stream()
				.sorted(comparingDouble(Candidate::getValue).reversed())
				.collect(toList());
	}

	// @return list of entities ranked according to the average value of their candidate instances
	public Map<Entity, Double> getRankedEntities()
	{
		rankCandidates();
		return entitiesToCandidates.keySet().stream()
				.map(e -> Pair.of(e, entitiesToCandidates.get(e).stream()
						.mapToDouble(Candidate::getValue)
						.average().orElse(0.0)))
				.sorted(comparingDouble((ToDoubleFunction<Pair<Entity, Double>>) Pair::getRight).reversed())
				.collect(toMap(Pair::getLeft, Pair::getRight));
	}

	// @return list of mentions ranked according to the maximum value of their candidates
	public Map<Mention, Double> getRankedMentions()
	{
		rankCandidates();
		return mentionsToCandidates.keySet().stream()
				.map(m -> Pair.of(m, mentionsToCandidates.get(m).stream()
						.mapToDouble(Candidate::getValue)
						.max().orElse(0.0))) // important, max!
				.sorted(comparingDouble((ToDoubleFunction<Pair<Mention, Double>>) Pair::getRight).reversed())
				.collect(toMap(Pair::getLeft, Pair::getRight));
	}

	@Override
	public int getNumParameters()
	{
		return params.length;
	}

	@Override
	public void getParameters(double[] doubles)
	{
		IntStream.range(0, doubles.length).forEach(i -> doubles[i] = params[i]);
	}

	@Override
	public double getParameter(int i)
	{
		return params[i];
	}

	@Override
	public void setParameters(double[] doubles)
	{
		IntStream.range(0, doubles.length).forEach(i -> params[i] = doubles[i]);
	}

	@Override
	public void setParameter(int i, double v)
	{
		params[i] = v;
	}

	protected Map<Candidate, Double> getSoftMaxDistributions()
	{
		// Work out softmax probability values for each parameter/pair
		return mentionsToCandidates.values().stream()
				.flatMap(candidates ->
				{
					double[] params = candidates.stream()
							.mapToInt(this.candidates::indexOf)
							.mapToDouble(i -> this.params[i])
							.toArray();
					double[] dist = getSoftMaxDistribution(params);
					return IntStream.range(0, candidates.size())
							.mapToObj(i -> Pair.of(candidates.get(i), dist[i]));
				})
				.collect(toMap(Pair::getLeft, Pair::getRight));
	}

	private static double[] getSoftMaxDistribution(double[] p)
	{
		double[] exps = Arrays.stream(p)
				.map(Math::exp)
				.toArray();
		double sum = Arrays.stream(exps).sum();
		return Arrays.stream(exps)
				.map(e -> e / sum)
				.toArray();
	}
}
