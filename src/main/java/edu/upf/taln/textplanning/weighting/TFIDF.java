package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.structures.AnnotatedWord;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.*;
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
	private final Map<String, Double> tfidf = new HashMap<>();
	private final static Logger log = LoggerFactory.getLogger(TFIDF.class);

	public TFIDF(Corpus inCorpus)
	{
		corpus = inCorpus;
	}

	@Override
	public void setContents(Collection<LinguisticStructure> contents)
	{
		tfidf.clear();



		// Collect frequency of senses of nominal mentions in the contents
		Map<String, Long> freqs = contents.stream()
				.map(LinguisticStructure::vertexSet)
				.map(p -> p.stream()
						.filter(this::isNominal) // candidate mention's are nominal if their head (this word) is
						.map(n -> n.getCandidates().stream()
								.map(Candidate::getEntity)
								.map(Entity::getReference)
								.collect(Collectors.toSet()))
						.flatMap(Set::stream)
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Calculate tf*idf values of the collection relative to the corpus
		int num_defined = 0;
		for (String ref : freqs.keySet())
		{
			long f = freqs.get(ref);
			double tf = 1 + Math.log(f); // logarithmically scaled
			//double tf = f; //0.5 + 0.5*(f/maxFreq); // augmented frequency
			double idf = Math.log(corpus.getNumDocs() / (1 + corpus.getEntityDocumentCount(ref)));
			tfidf.put(ref, tf * idf);

			if (corpus.hasEntityDocument(ref))
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
		contents.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(p -> p.stream()
						.filter(n -> !isNominal(n))
						.map(n -> n.getCandidates().stream()
								.map(Candidate::getEntity)
								.map(Entity::getReference)
								.collect(Collectors.toSet()))
						.flatMap(Set::stream))
				.forEach(e -> tfidf.put(e, avgTfidf));
	}

	@Override
	public double weight(String item)
	{
		if (!tfidf.containsKey(item))
			throw new RuntimeException("Cannot calculate tfidf value for unseen item " + item);

		return tfidf.get(item);
	}

	private boolean isNominal(AnnotatedWord n)
	{
		return n.getPOS().startsWith("N"); // nominals only
	}
}
