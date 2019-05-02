package edu.upf.taln.textplanning.core.bias;

import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.bias.corpora.Corpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A bias function for items based on tf.idf score.
 * Based on corpus with annotations of the items to weight.
 */
public final class TFIDF
{
	private final Map<String, Double> tfidf = new HashMap<>();
	private final static Logger log = LogManager.getLogger();

	public TFIDF(Collection<Candidate> contents, Corpus corpus)
	{
		tfidf.clear();
		final Set<String> references = contents.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.collect(Collectors.toSet());

		// Collect frequencies for selected items
		Map<String, Long> freqs = references.stream()
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
		log.debug(   "Corpus has frequencies for " + num_defined + " references out of " + freqs.keySet().size() +
					" (" + ratio + ")");

		// Normalize values to [0..1]
		double maxTfidf = tfidf.values().stream().mapToDouble(d -> d).max().orElse(1.0);
		tfidf.keySet().forEach(e -> tfidf.replace(e, tfidf.get(e) / maxTfidf));

		// Set the tf*idf score of excluded items to the avg of selected items
		double avgTfidf = tfidf.values().stream().mapToDouble(d -> d).average().orElse(0.0);
		references.forEach(i -> tfidf.put(i, avgTfidf));
	}

	public double weight(String meaning)
	{
		assert tfidf.containsKey(meaning);

		return tfidf.get(meaning);
	}
}
