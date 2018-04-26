package edu.upf.taln.textplanning.optimization;

import cc.mallet.optimize.Optimizable;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

/**
 * Creates and maintains a vector of parameter values, where parameters correspond to candidate meanings for a given
 * mention.
 * This class also acts as a wrapper to specific functions that can be passed to optimization algorithms in Mallet.
 */
public class CandidateOptimizable implements Optimizable.ByGradientValue
{
	private final Function f; // function to optimize
	private final List<Candidate> candidates = new ArrayList<>(); // vector of candidates/parameters
	private final double[] params; // vector of parameter values
	private final Map<Mention, List<Integer>> mentionsToCandidates; // integer values are indexes in this.candidates

	CandidateOptimizable(Function function, List<Candidate> candidates, Corpus corpus)
	{
		this.f = function;
		this.candidates.addAll(candidates);

		// Initialize parameter vector with corpus priors
		// todo consider using a softmax for the prior distribution
		params = candidates.stream()
				.mapToDouble(c -> {
					// mention form to consider is this one unless it has been marked to have a coreferent
					String form = c.getMention().getSurfaceForm();

					// get number of times form is found in the corpus
					OptionalInt total_count = corpus.getFormCount(form);
					if (!total_count.isPresent())
						return 0.0;

					// get number of times form is annotated with meaning in the corpus
					OptionalInt meaning_count = corpus.getFormMeaningCount(form, c.getMeaning().getReference());
					if (!meaning_count.isPresent())
						return 0.0;

					// return ratio of annotations with this sense respect to total
					return (double) meaning_count.getAsInt() / (double) total_count.getAsInt();
				})
				.toArray();

		// Create a dictionary mapping mentions to all its candidates
		Map<Mention, List<Candidate>> mention_dict = candidates.stream()
				.collect(groupingBy(Candidate::getMention, toList()));
		mentionsToCandidates = mention_dict.keySet().stream()
				.collect(toMap(m -> m, m -> mention_dict.get(m).stream()
						.map(candidates::indexOf)
						.collect(toList())));
	}

	@Override
	public double getValue()
	{
		double[] softmax = getSoftMaxParameterValues();
		return f.getValue(softmax);
	}

	@Override
	public void getValueGradient(double[] b)
	{
		f.getValueGradient(params, b);
	}

	Map<Candidate, Double> rankCandidates()
	{
		return IntStream.range(0, params.length)
				.mapToObj(i -> Pair.of(candidates.get(i), params[i]))
				.collect(toMap(Pair::getKey, Pair::getValue));
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

	private double[] getSoftMaxParameterValues()
	{
		double[] softmax_params = new double[params.length];
		for (List<Integer> indexes : mentionsToCandidates.values())
		{
			double[] candidate_values = indexes.stream()
					.mapToDouble(i -> this.params[i])
					.toArray();
			double[] dist = getSoftMaxDistribution(candidate_values);
			IntStream.range(0 , dist.length)
					.forEach(i -> softmax_params[indexes.get(i)] = dist[i]);
		}

		return softmax_params;
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
