package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A weighting function for items based on tf.idf score.
 * Based on corpus with annotations of the items to weight.
 */
public final class TFIDF implements WeightingFunction
{
	private final Corpus corpus;
	private final Predicate<String> filter;
	private final Map<String, Double> tfidf = new HashMap<>();
	private final static Logger log = LogManager.getLogger();

	public TFIDF(Corpus inCorpus, Predicate<String> filter)
	{
		corpus = inCorpus;
		this.filter = filter;
	}

	public void setContents(Collection<Candidate> contents)
	{
		tfidf.clear();
		final Set<String> references = contents.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toSet());

		// Collect frequencies for selected items
		Map<String, Long> freqs = references.stream()
				.filter(filter)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Calculate tf*idf values of the collection relative to the corpus
		int num_defined = 0;
		for (String item : freqs.keySet())
		{
			long f = freqs.get(item);
			double tf = 1 + Math.log(f); // logarithmically scaled
			//double tf = f; //0.5 + 0.5*(f/maxFreq); // augmented frequency
			OptionalInt df = corpus.getMeaningDocumentCount(item);
			int N = corpus.getNumDocs();
			double idf = Math.log((double)N / df.orElse(0) + 1); // smooth all df values by adding 1
			tfidf.put(item, tf * idf);

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

		// Set the tf*idf score of excluded items to the avg of selected items
		double avgTfidf = tfidf.values().stream().mapToDouble(d -> d).average().orElse(0.0);
		references.stream()
				.filter(i -> !filter.test(i))
				.forEach(i -> tfidf.put(i, avgTfidf));
	}

	@Override
	public double weight(String item)
	{
		if (!tfidf.containsKey(item))
			throw new RuntimeException("Cannot calculate tfidf value for unseen item " + item);

		return tfidf.get(item);
	}
}
