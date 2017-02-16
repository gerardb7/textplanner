package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class implements a ranking of entities based on their frequency in a set of trees and a metric of semantic
 * similarity between pairs of entities.
 */
public class SalientEntitiesMiner
{
	private final ItemSimilarity sim;
	private final static Logger log = LoggerFactory.getLogger(SalientEntitiesMiner.class);

	/**
	 * Constructor
	 *
	 * @param inSimilarity a similarity metric between entities
	 */
	public SalientEntitiesMiner(ItemSimilarity inSimilarity)
	{
		sim = inSimilarity;
	}

	/**
	 * Work out the most salient entities based on their frequency in a set of patterns and the frequency of semantically
	 * similar entities.
	 *
	 * @param inPatterns               set of patterns obtained from documents
	 * @param inNumEntities         number of top-ranked entities to return
	 * @param inStoppingThreshold   a stopping threshold for the entity sharing similarity
	 * @param inDamping             damping factor
	 * @return top entities
	 */
	public List<String> getSalientEntities(Set<SemanticTree> inPatterns, int inNumEntities,
	                                       double inStoppingThreshold, double inDamping)
	{
		// Collect entities
		List<AnnotationInfo> annotations = inPatterns.stream()
				.map(SemanticTree::getPreOrder)
				.flatMap(List::stream)
				.map(SemanticTree.Node::getData)
				.map(Pair::getLeft)
				.filter(SalientEntitiesMiner::isEntity)
				.collect(Collectors.toList());

		Map<String, AnnotationInfo> entities = annotations.stream()
				.collect(Collectors.toMap(AnnotationInfo::getReference, Function.identity(), (k1, k2) -> k1));

		// Get their frequencies
		Map<String, Long> frequencies = annotations.stream()
				.map(AnnotationInfo::getReference)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Sort entities by frequencies
		List<Pair<String, Long>> sortedEntities = frequencies.entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
				.map(e -> Pair.of(e.getKey(), e.getValue()))
				.collect(Collectors.toList());

		log.info("Frequencies of entities in collection:");
		IntStream.range(0, sortedEntities.size()).forEach(i ->
				log.info("\t" + i + " -> " + entities.get(sortedEntities.get(i).getLeft()).toString() + " -> " + sortedEntities.get(i).getValue()));

		// Create frequency and similarity matrices
		Matrix frequencyMatrix = createFrequencyMatrix(sortedEntities);
		Matrix similarityMatrix = createSimilarityMatrix(sortedEntities.stream()
				.map(Pair::getLeft)
				.collect(Collectors.toList()));

		// Create joint stochastic matrix for frequency bias and similarity
		Matrix rankingMatrix = frequencyMatrix.times(inDamping).plus(similarityMatrix.times(1- inDamping)).transpose();

		// Create initial distribution
		int n = sortedEntities.size();
		double[] initialDist = IntStream.range(0, n)
				.mapToDouble(i -> 1.0 / (double)n)
				.toArray();
		Matrix initialDistribution = new Matrix(initialDist, 1);

		// Obtain final distribution
		Matrix finalDistribution = PowerIteration.run(initialDistribution, rankingMatrix, inStoppingThreshold);
		log.info("Final ranking of entities:");
		IntStream.range(0, finalDistribution.getColumnDimension()).forEach(i ->
				log.info("\t" + i + " -> " + entities.get(sortedEntities.get(i).getLeft()).toString() + " -> " + finalDistribution.getColumnPackedCopy()[i]));

		// return inNumEntities top-ranked entities
		double[] staticDistribution = finalDistribution.getColumnPackedCopy();
		List<String> salientEntities = IntStream.range(0, staticDistribution.length)
				.mapToObj(i -> Pair.of(i, staticDistribution[i]))
				.sorted(Collections.reverseOrder(Comparator.comparing(Pair::getValue))) // Sort in reverse (descending) order
				.limit(inNumEntities)
				.map(Pair::getLeft)
				.map(sortedEntities::get)
				.map(Pair::getLeft)
				.collect(Collectors.toList());

		log.info("Selected entities:");
		salientEntities.stream().forEach(e ->
				log.info("\t" + entities.get(e).toString()));

		return salientEntities;
	}

	/**
	 * Decides whether an annotation can be considered an item based on its POS
	 *
	 * @param inAnn annotation to be evaluated
	 * @return true if it is an entity
	 */
	private static boolean isEntity(AnnotationInfo inAnn)
	{
		return inAnn.getReference() != null &&
				(inAnn.getPOS().startsWith("NN") || // nouns
				inAnn.getPOS().startsWith("VB") || // verbs
				inAnn.getPOS().startsWith("JJ") || // nominal modifiers
				inAnn.getPOS().startsWith("CD") || // cardinal numbers
				inAnn.getPOS().startsWith("FW"));   // and foreign words
	}

	/**
	 * Creates a stochastic matrix for a set of entities where probabilities are based on their frequencies.
	 * @param inFrequencies list of pairs of entities and their frequencies in a collection
	 * @return a frequency matrix
	 */
	private Matrix createFrequencyMatrix(List<Pair<String, Long>> inFrequencies)
	{
		// Create frequency matrix
		int n = inFrequencies.size();
		Matrix frequencyMatrix = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					double fj = inFrequencies.get(j).getRight();
					frequencyMatrix.set(i, j, fj);
				}));

		// Transform into probability (stochastic) matrix by normalizing relevance scores against the sum of each row
		IntStream.range(0, n).forEach(i ->
		{
			double accum = Arrays.stream(frequencyMatrix.getArray()[i]).sum();
			IntStream.range(0, n).forEach(j -> frequencyMatrix.set(i, j, frequencyMatrix.get(i, j) / accum));
		});

		return frequencyMatrix;
	}

	/**
	 * Creates a stochastic matrix for a set of entities where probabilities between pairs of entities are based on
	 * their similarity.
	 * @param inEntities list of entities
	 * @return a similarity matrix
	 */
	private Matrix createSimilarityMatrix(List<String> inEntities)
	{
		int n = inEntities.size();
		Matrix similarityMatrix = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					double sij = sim.computeSimilarity(inEntities.get(i), inEntities.get(j));
					similarityMatrix.set(i, j, sij);
				}));

		// Calculate average similarity for each entity to other entities
		List<Double> averages = IntStream.range(0, n)
				.mapToObj(i -> IntStream.range(0, n)
						.filter(j -> i != j) // exclude similarity to itself
						.mapToDouble(j -> similarityMatrix.get(i, j))
						.average().orElse(0.0))
				.collect(Collectors.toList());

		// Set similarity of entity pairs below their respective average to 0
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n)
						.filter(j -> i != j) // exclude similarity to itself
						.forEach(j ->
						{
							if (similarityMatrix.get(i, j) < averages.get(i))
								similarityMatrix.set(i, j, 0.0);
						}));

		// Normalize similarity values for each row
		IntStream.range(0, n).forEach(i ->
		{
			double accum = Arrays.stream(similarityMatrix.getArray()[i]).sum();
			IntStream.range(0, n).forEach(j -> similarityMatrix.set(i, j, similarityMatrix.get(i, j) / accum));
		});

		return similarityMatrix;
	}
}
