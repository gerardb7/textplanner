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
	public final Corpus corpus;
	private final Map<Entity, Double> tfidf = new HashMap<>();

	public TFIDF(Corpus inCorpus)
	{
		corpus = inCorpus;
	}

	@Override
	public void setCollection(List<SemanticTree> inCollection)
	{
		tfidf.clear();

		// Collect frequency of entities in the collection
		Map<Entity, Long> freqs = inCollection.stream()
				.map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity)
						.filter(e -> !ignoreEntity(e))
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				// Entity objects are tested for equality according to their unique labels
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		//long maxFreq = freqs.values().stream().mapToLong(Long::valueOf).max().orElse(1);

		// Calculate tf*idf values of the collection relative to the corpus
		for (Entity e : freqs.keySet())
		{
			long f = freqs.containsKey(e) ? freqs.get(e) : 0;
			double tf = 1 + Math.log(f); // logarithmically scaled
			//double tf = f; //0.5 + 0.5*(f/maxFreq); // augmented frequency
			double idf = Math.log(corpus.getNumDocs() / (1 + corpus.getFrequency(e)));
			tfidf.put(e, tf * idf);
		}

		// Normalize values to [0..1]
		double maxTfidf = tfidf.values().stream().mapToDouble(Double::valueOf).max().orElse(1.0);
		tfidf.keySet().forEach(e -> tfidf.replace(e, tfidf.get(e) / maxTfidf));
	}

	@Override
	public double weight(Entity e)
	{
		if (ignoreEntity(e))
			return 0.0;

		if (!tfidf.containsKey(e))
			throw new RuntimeException("Cannot calculate tfidf value for unseen entity " + e);

		return tfidf.get(e);
	}

	private boolean ignoreEntity(Entity e)
	{
		String l = e.getEntityLabel();
		return (l.equals("_") || l.equals("\"") || l.equals("\'") || l.equals(",") || l.equals(";") || l.equals("--") ||
				l.equals("-"));
	}
}
