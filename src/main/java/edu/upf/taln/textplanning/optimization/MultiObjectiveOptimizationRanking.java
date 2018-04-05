package edu.upf.taln.textplanning.optimization;

import cc.mallet.optimize.ConjugateGradient;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.similarity.MeaningSimilarity;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Ranks mentions in the text and their candidate senses according to multiple optimization functions.
 */
public class MultiObjectiveOptimizationRanking
{
	private final WeightingFunction weighting;
	private final MeaningSimilarity similarity;
	private final double min_meaning_weight;
	private final double meaning_similarity_threshold;
	private final Corpus corpus;
	private final static Logger log = LoggerFactory.getLogger(MultiObjectiveOptimizationRanking.class);

	public MultiObjectiveOptimizationRanking(WeightingFunction weighting, MeaningSimilarity similarity, Corpus corpus,
	                                         double min_meaning_weight, double meaning_similarity_threshold)
	{
		this.weighting = weighting;
		this.similarity = similarity;
		this.corpus = corpus;
		this.min_meaning_weight = min_meaning_weight;
		this.meaning_similarity_threshold = meaning_similarity_threshold;
	}

	public Map<Candidate, Double> optimize(List<Candidate> candidates)
	{
		// Set up functions to be optimized and parameters
		log.info("Setting up objectives");
		Coherence coherence = new Coherence(candidates, similarity, meaning_similarity_threshold);
		SimpleType type = new SimpleType(candidates);
		Salience salience = new Salience(candidates, weighting, min_meaning_weight);
		MultiObjectiveFunction multi = new MultiObjectiveFunction(coherence, type, salience);
		CandidateOptimizable optimizable = new CandidateOptimizable(multi, candidates, corpus);

		// Optimize functions
		ConjugateGradient gradient = new ConjugateGradient(optimizable);
		boolean converged = false;
		try
		{
			log.info("Optimizing");
			Stopwatch timer = Stopwatch.createStarted();
			converged = gradient.optimize();
			log.info("Optimization completed in " + timer.stop());
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Optimization algorithm has thrown an exception: " + e);
		}
		if (!converged)
			log.warn("Optimization algorithm failed to converge");

		// Update candidates with optimized values. This automatically updates the candidates in the structures too
		return optimizable.rankCandidates();
	}
}
