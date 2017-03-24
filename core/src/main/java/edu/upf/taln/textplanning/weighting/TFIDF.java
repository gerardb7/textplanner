package edu.upf.taln.textplanning.weighting;

import edu.upf.taln.textplanning.corpora.Corpus;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.OrderedTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A weighting function based on tf.idf score.
 * Augmented tf is obtained from a list of semantic trees analyzed from a collection of documents.
 * IDF is obtained from a corpus.
 */
public class TFIDF implements WeightingFunction
{
	private final Corpus corpus;
	private final Map<String, Long> freqs = new HashMap<>();
	private long maxFreq = 0;

	public TFIDF(Corpus inCorpus)
	{
		corpus = inCorpus;
	}

	@Override
	public void setCollection(List<AnnotatedTree> inCollection)
	{
		freqs.clear();
		freqs.putAll(inCollection.stream()
				.map(AnnotatedTree::getPreOrder)
				.map(p -> p.stream()
						.map(OrderedTree.Node::getData)
						.map(Entity::getEntityLabel)
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));

		maxFreq = freqs.values().stream().mapToLong(Long::valueOf).max().orElse(1);
	}

	@Override
	public double weight(Entity inEntity)
	{
		String label = inEntity.getEntityLabel();
		long f = freqs.containsKey(label) ? freqs.get(label) : 0;
		double tf = 0.5 + 0.5*(f/maxFreq); // augmented frequency
		double idf = Math.log(corpus.getNumDocs() / (1 + corpus.getFrequency(label)));

		return tf * idf;
	}
}
