package edu.upf.taln.textplanning.optimization;

import cc.mallet.optimize.ConjugateGradient;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.List;
import java.util.Map;

/**
 * Ranks mentions in the text and their candidate senses according to multiple optimization functions.
 */
public class MultiObjectiveOptimizationRanking
{
	private final WeightingFunction weighting;
	private final SimilarityFunction similarity;
	private final double meaning_similarity_threshold;
	private final Corpus corpus;
	private final static Logger log = LogManager.getLogger();

	public MultiObjectiveOptimizationRanking(WeightingFunction weighting, SimilarityFunction similarity, Corpus corpus,
	                                         double meaning_similarity_threshold)
	{
		this.weighting = weighting;
		this.similarity = similarity;
		this.corpus = corpus;
		this.meaning_similarity_threshold = meaning_similarity_threshold;
	}

	public Map<Candidate, Double> optimize(List<Candidate> candidates)
	{
		// Set up functions to be optimized and parameters
		log.info("Setting up objectives");
		Coherence coherence = new Coherence(candidates, similarity, meaning_similarity_threshold);
		SimpleType type = new SimpleType(candidates);
		Salience salience = new Salience(candidates, weighting);
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
