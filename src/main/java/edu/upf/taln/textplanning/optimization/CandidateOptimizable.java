package edu.upf.taln.textplanning.optimization;

import cc.mallet.optimize.Optimizable;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.Mention;
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
	private final Map<Mention, List<Integer>> mentionsToCandidates; // integer values are indexes in this.candidates
	private final Map<Entity, List<Integer>> entitiesToCandidates;

	public CandidateOptimizable(Function function, List<Candidate> candidates, Corpus corpus)
	{
		this.f = function;
		this.candidates.addAll(candidates);

		// Initialize parameter vector with corpus priors
		// todo consider using a softmax for the prior distribution
		params = candidates.stream()
				.mapToDouble(c -> {
					// mention form to consider is this one unless it has been marked to have a coreferent
					Mention mention = c.getMention().getCoref().orElse(c.getMention());
					String form = mention.getSurfaceForm();

					// get number of times form is found in the corpus
					long total_count = corpus.getFormCount(form);
					if (total_count == 0)
						return 0.0;

					// get number of times form is annotated with entity in the corpus
					long entity_count = corpus.getFormEntityCount(form, c.getEntity().getId());

					// return ratio of annotations with this sense respect to total
					return (double) entity_count / (double) total_count;
				})
				.toArray();

		// Create a dictionary mapping mentions to all its candidates
		Map<Mention, List<Candidate>> mention_dict = candidates.stream()
				.collect(groupingBy(Candidate::getMention, toList()));
		mentionsToCandidates = mention_dict.keySet().stream()
				.collect(toMap(m -> m, m -> mention_dict.get(m).stream()
						.map(candidates::indexOf)
						.collect(toList())));


		// Create a dictionary mapping entities to all the mentions of which they are candidates
		Map<Entity, List<Candidate>> entity_dict = candidates.stream()
				.collect(groupingBy(Candidate::getEntity, toList()));
		entitiesToCandidates = entity_dict.keySet().stream()
				.collect(toMap(m -> m, m -> entity_dict.get(m).stream()
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
				.map(candidates::get)
				.sorted(comparingDouble(Candidate::getValue).reversed())
				.collect(toList());
	}

	// @return list of entities ranked according to the average value of their candidate instances
	public Map<Entity, Double> getRankedEntities()
	{
		rankCandidates();
		return entitiesToCandidates.keySet().stream()
				.map(e -> Pair.of(e, entitiesToCandidates.get(e).stream()
						.map(candidates::get)
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
						.map(candidates::get)
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
