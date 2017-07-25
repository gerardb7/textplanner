package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.structures.AnnotatedWord;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A weighting function based on tf.idf score.
 * Augmented tf is obtained from a list of annotated trees analyzed from a collection of documents.
 * IDF is obtained from a corpus.
 */
public final class TFIDF implements WeightingFunction
{
	private final Corpus corpus;
	private final Map<String, Double> tfidf = new HashMap<>();

	public TFIDF(Corpus inCorpus)
	{
		corpus = inCorpus;
	}

	@Override
	public void setContents(Set<LinguisticStructure> contents)
	{
		tfidf.clear();

		// Collect frequency of annotated senses and forms in the contents
		Map<String, Long> freqs = contents.stream()
				.map(LinguisticStructure::vertexSet)
				.map(p -> p.stream()
						.filter(this::isNominal)
						.map(n -> {
							return n.getCandidates().stream()
									.map(Candidate::getEntity)
									.map(Entity::getReference)
									.collect(Collectors.toSet());
//							if (entities.isEmpty())
//								entities.add(n.getForm());
//							return entities;
						})
						.flatMap(Set::stream)
						.filter(i -> !ignoreItem(i))
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Calculate tf*idf values of the collection relative to the corpus
		for (String i : freqs.keySet())
		{
			long f = freqs.containsKey(i) ? freqs.get(i) : 0;
			double tf = 1 + Math.log(f); // logarithmically scaled
			//double tf = f; //0.5 + 0.5*(f/maxFreq); // augmented frequency
			double idf = Math.log(corpus.getNumDocs() / (1 + corpus.getEntityDocumentCount(i)));
			tfidf.put(i, tf * idf);
		}

		// Normalize values to [0..1]
		double maxTfidf = tfidf.values().stream().mapToDouble(d -> d).max().orElse(1.0);
		tfidf.keySet().forEach(e -> tfidf.replace(e, tfidf.get(e) / maxTfidf));

		// Set the tf*idf score of non-nominal items to the avg of nominal items
		double avgTfidf = tfidf.values().stream().mapToDouble(d -> d).average().orElse(0.0);
		contents.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(p -> p.stream()
						.filter(n -> !isNominal(n))
						.map(n -> {
							return n.getCandidates().stream()
									.map(Candidate::getEntity)
									.map(Entity::getReference)
									.collect(Collectors.toSet());
//							if (entities.isEmpty())
//								entities.add(n.getForm());
						})
						.flatMap(Set::stream)
						.filter(i -> !ignoreItem(i)))
				.forEach(e -> tfidf.put(e, avgTfidf));
	}

	@Override
	public double weight(String item)
	{
		if (ignoreItem(item))
			return 0.0;

		if (!tfidf.containsKey(item))
			throw new RuntimeException("Cannot calculate tfidf value for unseen item " + item);

		return tfidf.get(item);
	}

	public long getFrequency(String item)
	{
		return corpus.getEntityDocumentCount(item);
	}

	private boolean isNominal(AnnotatedWord n)
	{
		return n.getPOS().startsWith("N"); // nominals only
	}

	private boolean ignoreItem(String i)
	{
		return (i.equals("_") || i.equals("\"") || i.equals("\'") || i.equals(",") || i.equals(";") || i.equals("--") ||
				i.equals("-"));
	}
}
