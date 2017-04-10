package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A weighting function based on tf.idf score.
 * Augmented tf is obtained from a list of annotated trees analyzed from a collection of documents.
 * IDF is obtained from a corpus.
 */
public class TFIDF implements WeightingFunction
{
	private final Corpus corpus;
	private final Map<String, Double> tfidf = new HashMap<>();

	public TFIDF(Corpus inCorpus)
	{
		corpus = inCorpus;
	}

	@Override
	public void setCollection(List<SemanticTree> inCollection)
	{
		tfidf.clear();

		// Collect frequency of entities in the collection
		Map<String, Long> freqs = inCollection.stream()
				.map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity)
						.map(Entity::getEntityLabel)
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		long maxFreq = freqs.values().stream().mapToLong(Long::valueOf).max().orElse(1);

		// Calculate tf*idf values of the collection relative to the corpus
		for (String label : freqs.keySet())
		{
			long f = freqs.containsKey(label) ? freqs.get(label) : 0;
			double tf = 0.5 + 0.5*(f/maxFreq); // augmented frequency
			double idf = Math.log(corpus.getNumDocs() / (1 + corpus.getFrequency(label)));
			tfidf.put(label, tf * idf);
		}

		// Normalize values to [0..1]
		double maxTfidf = tfidf.values().stream().mapToDouble(Double::valueOf).max().orElse(1.0);
		tfidf.keySet().forEach(e -> tfidf.replace(e, tfidf.get(e) / maxTfidf));
	}

	@Override
	public double weight(Entity inEntity)
	{
		String label = inEntity.getEntityLabel();
		if (!tfidf.containsKey(label))
			throw new RuntimeException("Cannot calculate tfidf value for unseen entity " + label);

		// @todo think of better treatment of '_' tokens
		if (label.equals("_"))
			return 0.0;

		return tfidf.get(label);
	}
}
