package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.amr.GraphList;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A weighting function for senses based on tf.idf score.
 * Augmented tf is obtained from a collection of structures with annotated senses.
 * IDF is obtained from a sense-annotated corpus.
 */
public final class TFIDF implements WeightingFunction
{
	private final Corpus corpus;
	private final boolean nominal_only;
	private final Map<String, Double> tfidf = new HashMap<>();
	private final static Logger log = LogManager.getLogger(TFIDF.class);

	public TFIDF(Corpus inCorpus, boolean nominal_only)
	{
		corpus = inCorpus;
		this.nominal_only = nominal_only;
	}

	@Override
	public void setContents(GraphList graphs)
	{
		tfidf.clear();

		// Collect frequencies for nominal meanings
		Map<String, Long> freqs = graphs.getCandidates().stream()
				.filter(c -> !nominal_only || c.getMention().isNominal())
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Calculate tf*idf values of the collection relative to the corpus
		int num_defined = 0;
		for (String ref : freqs.keySet())
		{
			long f = freqs.get(ref);
			double tf = 1 + Math.log(f); // logarithmically scaled
			//double tf = f; //0.5 + 0.5*(f/maxFreq); // augmented frequency
			OptionalInt df = corpus.getMeaningDocumentCount(ref);
			int N = corpus.getNumDocs();
			double idf = Math.log(N / df.orElse(0) + 1); // smooth all df values by adding 1
			tfidf.put(ref, tf * idf);

			if (df.isPresent())
				++num_defined;
		}

		NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMAN);
		format.setMaximumFractionDigits(2);
		String ratio = format.format((double) num_defined / (double) freqs.keySet().size());
		log.info(   "Corpus has frequencies for " + num_defined + " references out of " + freqs.keySet().size() +
					" (" + ratio + ")");

		// Normalize values to [0..1]
		double maxTfidf = tfidf.values().stream().mapToDouble(d -> d).max().orElse(1.0);
		tfidf.keySet().forEach(e -> tfidf.replace(e, tfidf.get(e) / maxTfidf));

		// Set the tf*idf score of non-nominal items to the avg of nominal items
		// todo check if setting tf*idf for non-nominal senses to avg of nominal senses produces expected results
		double avgTfidf = tfidf.values().stream().mapToDouble(d -> d).average().orElse(0.0);
		graphs.getCandidates().stream()
				.filter(c -> !c.getMention().isNominal())
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.forEach(m -> tfidf.put(m, avgTfidf));
	}

	@Override
	public double weight(String item)
	{
		if (!tfidf.containsKey(item))
			throw new RuntimeException("Cannot calculate tfidf value for unseen item " + item);

		return tfidf.get(item);
	}
}
