package edu.upf.taln.textplanning.optimization;

import cc.mallet.optimize.ConjugateGradient;
import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.similarity.CandidateSimilarity;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Ranks mentions in the text and their candidate senses according to multiple optimization functions.
 */
public class MultiObjectiveOptimizationRanking
{
	private final static Logger log = LoggerFactory.getLogger(MultiObjectiveOptimizationRanking.class);

	public static void optimize(List<Candidate> candidates, Corpus corpus, CandidateSimilarity similarity,
	                            WeightingFunction relevance, double similarity_lower_bound, double relevance_lower_bound)
	{
		// Set up functions to be optimized and parameters
		Coherence coherence = new Coherence(candidates, similarity, similarity_lower_bound);
		SimpleType type = new SimpleType(candidates);
		Salience salience = new Salience(candidates, relevance, relevance_lower_bound);
		MultiObjectiveFunction multi = new MultiObjectiveFunction(coherence, type, salience);
		CandidateOptimizable optimizable = new CandidateOptimizable(multi, candidates, corpus);

		// Optimize functions
		ConjugateGradient gradient = new ConjugateGradient(optimizable);
		boolean converged = false;
		try
		{
			converged = gradient.optimize();
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Optimization algorithm has thrown an exception: " + e);
		}
		if (!converged)
			log.warn("Optimization algorithm failed to converge");

		// Update candidates with optimized values. This automatically updates the candidates in the structures too
		optimizable.rankCandidates();
	}
}
