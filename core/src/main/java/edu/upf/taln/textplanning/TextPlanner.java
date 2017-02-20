package edu.upf.taln.textplanning;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.metrics.PatternMetric;
import edu.upf.taln.textplanning.pattern.PatternExtractor;
import edu.upf.taln.textplanning.similarity.EntitySharingSimilarity;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import edu.upf.taln.textplanning.similarity.TreeEditSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedWeightedSubgraph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Text planner class. Given a set of references to entities and a set of documents, generates a text plan
 * containing contents in the documents relevant to the references.
 * Immutable class.
 */
public final class TextPlanner
{
	private final List<Pair<PatternMetric, Double>> metrics = new ArrayList<>();
	private final PatternExtractor extractor;
	private final SalientEntitiesMiner entityMiner;
	private final ItemSimilarity wordFormSim; // used to calculate shallow similarity
	private final ItemSimilarity wordSenseSim; // used to calculate semantic similarity
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static class Options
	{
		public int numPatterns = 0; // If 0, returns ranked list of all input patterns
		public int numSalientEntities = 3; // number of salient entities to be used when no entities are specified
		public double dampingFactor = 0.1; // damping factor to control balance between relevance bias and similarity
		public double salienceStopThreshold = 0.01; // stopping threshold for the algorithm ranking entities by salience
		public double rankingStopThreshold = 0.01; // stopping threshold for the main ranking algorithm
		public boolean useGraph = true; // if true, contents are selected using rankingSimilarity graph
		public boolean generateStats = false;
		public String stats = "";

		public enum SimilarityType
		{
			EntitySharing, Semantic
		}

		public SimilarityType rankingSimilarity = SimilarityType.Semantic;
		public SimilarityType selectionSimilarity = SimilarityType.EntitySharing;
	}

	/**
	 * @param inMetrics metrics used to score patterns
	 * @param inDeterminer performs pattern determination
	 * @param inWordFormSim similarity metric for pairs of word forms
	 * @param inWordSenseSim similarity metric for pairs of word senses
	 */
	public TextPlanner(List<Pair<PatternMetric, Double>> inMetrics, PatternExtractor inDeterminer,
	                   ItemSimilarity inWordFormSim, ItemSimilarity inWordSenseSim, SalientEntitiesMiner inEntityMiner)
	{
		entityMiner = inEntityMiner;
		metrics.addAll(inMetrics);
		extractor = inDeterminer;
		wordFormSim = inWordFormSim;
		wordSenseSim = inWordSenseSim;
	}

	/**
	 * Generates a text plan using the most salient entities in a plan
	 *
	 * @param inContents contents to summarize
	 * @param inOptions options for text planner
	 * @return the plan in conll format
	 */
	public List<SemanticTree> planText(List<SemanticTree> inContents, Options inOptions)
	{
		return planText(new HashSet<>(), inContents, inOptions);
	}

	/**
	 * Generates a text plan about some entities from some contents encoded as semantic trees
	 *
	 * @param inReferences focal entities of the plan
	 * @param inContents initial set of contents
	 * @return list of patterns ranked by relevance and, optionally, according to local coherence constraints.
	 */
	public List<SemanticTree> planText(Set<String> inReferences, List<SemanticTree> inContents, Options inOptions)
	{
		try
		{
			// 1- Extract patterns
			log.info("***Starting planning***");
			log.info("**Pattern extraction**");
			Stopwatch timer = Stopwatch.createStarted();
			Set<SemanticTree> patterns = extractor.getPatterns(inContents);
			log.info("Pattern extraction took " + timer.stop());

			// 2- If no references are specified, mine documents for most salient ones
			if (inReferences.isEmpty())
			{
				log.info("**Salient entities mining**");
				timer.reset(); timer.start();
				inReferences.addAll(entityMiner.getSalientEntities(patterns, inOptions.numSalientEntities,
						inOptions.salienceStopThreshold, inOptions.dampingFactor));
				log.info("Mining for salient entities took " + timer.stop());
			}

			// 3- Score them with relevance values and get initial distribution
			log.info("**Scoring patterns and creating relevance matrix**");
			timer = Stopwatch.createStarted();

			Map<SemanticTree, Double> scoredPatterns = patterns.stream()
					.collect(Collectors.toMap(p -> p, p -> 0.0));
			for (Pair<PatternMetric, Double> metric : metrics)
			{
				Map<SemanticTree, Double> metricScores = metric.getLeft().assess(inReferences, patterns);
				for (SemanticTree pattern : scoredPatterns.keySet())
				{
					scoredPatterns.put(pattern, scoredPatterns.get(pattern) + metricScores.get(pattern) * metric.getRight());
				}
			}

			List<Pair<SemanticTree, Double>> sortedPatterns = scoredPatterns.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
					.map(e -> Pair.of(e.getKey(), e.getValue()))
					.collect(Collectors.toList());
			List<SemanticTree> sortedPatternList =
					sortedPatterns.stream().map(Pair::getLeft).collect(Collectors.toList());

			// 4- Create relevance matrix
			List<Double> scores = patterns.stream()
					.map(scoredPatterns::get)
					.collect(Collectors.toList());
			Matrix relevanceMatrix = createRelevanceMatrix(sortedPatternList, scores);

			log.info("Scoring patterns took " + timer.stop());
			log.info("Relevance scoring:");
			IntStream.range(0, sortedPatterns.size()).forEach(i ->
					log.info("\t" + i + " -> " + sortedPatterns.get(i).getValue() + " -> " + sortedPatterns.get(i).getKey()));

			log.info("**Creating initial distribution and ranking matrix");
			timer.reset();
			timer.start();

			// 5- Create a similarity matrix
			PatternSimilarity rankSim = inOptions.rankingSimilarity == Options.SimilarityType.EntitySharing ?
					new EntitySharingSimilarity() : new TreeEditSimilarity(wordFormSim, wordSenseSim);
			Matrix similarityMatrix = createSimilarityMatrix(sortedPatternList, rankSim);
			log.info("Creating matrices took " + timer.stop());

			// 6- Create joint stochastic matrix for relevance bias and similarity
			double d = inOptions.dampingFactor;
			Matrix rankingMatrix = relevanceMatrix.times(d).plus(similarityMatrix.times(1-d)).transpose();

			// Create initial distribution
			double[] initialDist = IntStream.range(0, patterns.size())
					.mapToDouble(i -> 1.0 / (double)patterns.size())
					.toArray();
			Matrix intialDistribution = new Matrix(initialDist, 1);

			// 7- Rank patterns using biased semantic ranking and power iteration method
			log.info("**Power iteration ranking**");
			timer.reset();
			timer.start();
			Matrix finalDistribution = PowerIteration.run(intialDistribution, rankingMatrix, inOptions.rankingStopThreshold);
			log.info("Power iteration ranking took " + timer.stop());
			IntStream.range(0, finalDistribution.getColumnDimension())
					.forEach(i -> log.info("\t" + i + " -> " + finalDistribution.getColumnPackedCopy()[i]));

			// 8-  Get best patterns according to final distribution
			log.info("**Selecting contents**");
			timer.reset();
			timer.start();
			PatternSimilarity selSim = inOptions.selectionSimilarity == Options.SimilarityType.EntitySharing ?
					new EntitySharingSimilarity() : new TreeEditSimilarity(wordFormSim, wordSenseSim);
			List<SemanticTree> bestPatterns =
					inOptions.useGraph ? getRankedGraphPatterns(sortedPatternList, finalDistribution, inOptions.numPatterns, selSim)
							: getRankPatterns(sortedPatternList, finalDistribution, inOptions.numPatterns);
			log.info("Selecting contents took " + timer.stop());

			// 9- Generate stats
			if (inOptions.generateStats)
			{
				log.info("**Generating stats**");
				timer.reset();
				timer.start();
//			inOptions.stats = StatsReporter.reportStats(inPatterns, new HashSet<>(inReferences), corpora, rankSim);
				log.info("Stats generation took " + timer.stop());
			}

			return bestPatterns;
		}
		catch (Exception e)
		{
			log.error("***Planning failed***");
			throw new RuntimeException(e);
		}
	}


	/**
	 * Creates a stochastic matrix for a set of patterns where probabilities are based on the a distribution of
	 * relevance scores.
	 * @param inPatterns list of patterns
	 * @param inScores relevance distribution
	 * @return a relevance matrix
	 */
	public static Matrix createRelevanceMatrix(List<SemanticTree> inPatterns,
	                                           List<Double> inScores)
	{
		// Create stochastic matrix using relevance metric
		int n = inPatterns.size();
		Matrix stochasticMatrix = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					double rj = inScores.get(j);
					stochasticMatrix.set(i, j, rj);
				}));

		// Transform into probability (stochastic) matrix by normalizing relevance scores against the sum of each row
		IntStream.range(0, n).forEach(i ->
		{
			double accum = Arrays.stream(stochasticMatrix.getArray()[i]).sum();
			IntStream.range(0, n).forEach(j -> stochasticMatrix.set(i, j, stochasticMatrix.get(i, j) / accum));
		});

		return stochasticMatrix;
	}


	/**
	 * Creates a stochastic matrix for a set of patterns where probabilities between pairs of patterns are based on
	 * their similarity according to some metric.
	 * @param inPatterns list of patterns
	 * @param inSimilarity similarity metric for pairs of patterns
	 * @return a similarity matrix
	 */
	public static Matrix createSimilarityMatrix(List<SemanticTree> inPatterns,
	                                            PatternSimilarity inSimilarity)
	{
		// Create stochastic matrix using similarity metric
		int n = inPatterns.size();
		Matrix stochasticMatrix = new Matrix(n, n);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					double sij = inSimilarity.getSimilarity(inPatterns.get(i), inPatterns.get(j));
					stochasticMatrix.set(i, j, sij);
					log.info("\tSimilarity " + i + "-" + j +"=" + stochasticMatrix.get(i,j));
				}));

		// Accumulate scores for each item
		List<Double> averages = IntStream.range(0, n)
				.mapToObj(i -> IntStream.range(0, n)
						.filter(j -> i != j) // exclude similarity to itself
						.mapToDouble(j -> stochasticMatrix.get(i, j))
						.average().orElse(0.0))
				.collect(Collectors.toList());

		// Set similarity of item pairs below their respective average to 0
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n)
						.filter(j -> i != j) // exclude similarity to itself
						.forEach(j ->
						{
							if (stochasticMatrix.get(i, j) < averages.get(i))
								stochasticMatrix.set(i, j, 0.0);
						}));

		// Transform into probability (stochastic) matrix by normalizing similarity values against the sum of each row
		IntStream.range(0, n).forEach(i ->
		{
			double accum = Arrays.stream(stochasticMatrix.getArray()[i]).sum();
			IntStream.range(0, n).forEach(j -> stochasticMatrix.set(i, j, stochasticMatrix.get(i, j) / accum));
		});

		IntStream.range(0, n)
				.forEach(i -> IntStream.range(0, n)
						.forEach(j -> log.info("\tTransition " + i + "-" + j +"=" + stochasticMatrix.get(i,j))));

		return stochasticMatrix;
	}

	/**
	 * Returns the best chosen according to a ranking
	 *
	 * @param inPatterns     complete list of available patterns
	 * @param inDistribution ranking of patterns
	 * @param inNumPatterns  number of patterns to return
	 * @return sequence of patterns
	 */
	private List<SemanticTree> getRankPatterns(
			List<SemanticTree> inPatterns, Matrix inDistribution, int inNumPatterns)
	{
		// Create a map with indexes of patterns in the distribution vector as keys, and their relevance as value
		double[] staticDistribution = inDistribution.getColumnPackedCopy();
		Map<Integer, Double> indexAndScores = IntStream.range(0, staticDistribution.length)
				.mapToObj(Integer::new)
				.collect(Collectors.toMap(Function.identity(), i -> staticDistribution[i]));

		// Sort the map by relevance values and select inNumPatterns maximum entries
		Comparator<Map.Entry<Integer, Double>> byValue = (entry1, entry2) -> entry1.getValue().compareTo(
				entry2.getValue());

		return indexAndScores.entrySet().stream()
				.sorted(byValue)//.reversed())
				.limit(inNumPatterns == 0 ? inPatterns.size() : inNumPatterns)
				.map(entry -> Lists.newArrayList(inPatterns).get(entry.getKey()))
				.collect(Collectors.toList());
	}

	/**
	 * Returns the best patterns according to a ranking and a query graph
	 *
	 * @param inPatterns     complete list of available patterns
	 * @param inDistribution ranking of patterns
	 * @param inNumPatterns  number of patterns to return
	 * @param inSimilarity   similarity metric between pairs of patterns
	 * @return sequence of patterns
	 */
	private List<SemanticTree> getRankedGraphPatterns(
			List<SemanticTree> inPatterns, Matrix inDistribution, int inNumPatterns, PatternSimilarity inSimilarity)
	{
		// Create a map patterns as keys and their final score as value
		double[] staticDistribution = inDistribution.getColumnPackedCopy();
		Map<SemanticTree, Double> scoredPatterns = IntStream.range(0, staticDistribution.length)
				.mapToObj(Integer::new)
				.collect(Collectors.toMap(i -> Lists.newArrayList(inPatterns).get(i),
						i -> staticDistribution[i]));

		// Create new weighted graph with ranking
		SimpleDirectedWeightedGraph<SemanticTree, DefaultWeightedEdge> graph =
				createPlanningGraph(scoredPatterns, inSimilarity);

		// Create list of visited nodes
		List<SemanticTree> visitedNodes = new ArrayList<>();

		// Determine the number of connected components of graph
		ConnectivityInspector<SemanticTree, DefaultWeightedEdge> inspector =
				new ConnectivityInspector<>(graph);
		List<Set<SemanticTree>> components = inspector.connectedSets();
		log.info("Query graph has " + components.size() + " connected components");

		// Sort components by maximum relevance of their patterns: pattern with max relevance will be shown first
		List<Pair<Set<SemanticTree>, Double>> sortedComponents = components.stream()
				.map(c -> Pair.of(c, c.stream()
						.mapToDouble(scoredPatterns::get)
						.max().orElse(0.0)))
				.sorted((a, b) -> Double.compare(b.getRight(), a.getRight())) // descending order
				.collect(Collectors.toList());

		// Explore each connected subgraph separately
		sortedComponents.stream()
				.peek(c -> log.info("Selecting from graph component " + sortedComponents.indexOf(c) + 1))
				.map(Pair::getLeft)
				.forEach(c -> {
					DirectedWeightedSubgraph<SemanticTree, DefaultWeightedEdge> subGraph =
							new DirectedWeightedSubgraph<>(graph, c, null);

					// Start exploration with highest ranked pattern
					SemanticTree maxPattern = scoredPatterns.entrySet().stream()
							.filter(e -> c.contains(e.getKey()))
							.max(Map.Entry.comparingByValue())
							.orElseThrow(() -> new RuntimeException("Cannot determine best patterns from empty list")).getKey();
					log.info("Highest scored vertex in component is " + maxPattern);

					// Explore!
					visitedNodes.addAll(greedyExploration(maxPattern, subGraph, inPatterns));
				});

		if (inNumPatterns == 0 || visitedNodes.size() <= inNumPatterns)
			return visitedNodes;
		else
			return visitedNodes.subList(0, inNumPatterns);
	}

	/**
	 * Creates a graph to be used to obtain the text plan
	 * @param inScoredPatterns patterns and their scores
	 * @param inSimilarity a similarity metric between pairs of patterns
	 * @return a directed graph where edges are weighted according to similarity metric
	 */
	public static SimpleDirectedWeightedGraph<SemanticTree, DefaultWeightedEdge>
		createPlanningGraph(Map<SemanticTree, Double> inScoredPatterns, PatternSimilarity inSimilarity)
	{
		// Add all patterns as nodes
		SimpleDirectedWeightedGraph<SemanticTree, DefaultWeightedEdge> graph =
				new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

		inScoredPatterns.forEach((k, v) -> graph.addVertex(k));

		// Add edges between each pair of patterns weighted according to their semantic similarity
		graph.vertexSet().stream()
				.forEach(v1 -> graph.vertexSet().stream()
						.filter(v2 -> !v1.equals(v2))
						.forEach(v2 -> {
							if (!graph.containsEdge(v1, v2))
							{
								double sim = inSimilarity.getSimilarity(v1, v2);
								double score = inScoredPatterns.get(v2); // weight of TARGET node!
								if (sim > 0.0)
								{
									DefaultWeightedEdge e = graph.addEdge(v1, v2);
									//log.info("\tAdded edge with similarity " + sim + " and score " + score +
									//		" from " + inScoredPatterns.indexOf(v1) + " to " + inPatterns.indexOf(v2));
									graph.setEdgeWeight(e, sim * score);
								}
							}
						}));

		return graph;
	}

	/**
	 * Greedy exploration of a weighted graph
	 * @param inInitialNode initial node where exploration starts
	 * @param inGraph a weighted directed simple graph
	 * @return list of nodes visited during greedy exploration
	 */
	private static List<SemanticTree> greedyExploration(SemanticTree inInitialNode,
	                                                DirectedWeightedSubgraph<SemanticTree, DefaultWeightedEdge> inGraph,
													List<SemanticTree> inPatterns)
	{
		SemanticTree node = inInitialNode;
		List<SemanticTree> visitedNodes = new ArrayList<>();
		visitedNodes.add(inInitialNode);

		// Create auto-sorted list of open edges
		Comparator<DefaultWeightedEdge> comparator = (e1, e2) -> Double.compare(
				inGraph.getEdgeWeight(e2), inGraph.getEdgeWeight(e1)); // Descending order
		PriorityQueue<DefaultWeightedEdge> openEdges = new PriorityQueue<>(comparator);

		boolean continueExploring = true;
		while (continueExploring)
		{
			// Expand set of open edges with the out links of the current node
			openEdges.addAll(inGraph.edgesOf(node));

			// Discard any edges leading to visited nodes
			openEdges.removeIf(e -> visitedNodes.contains(inGraph.getEdgeTarget(e)));

			if (!openEdges.isEmpty())
			{
				// Determine open edge with highest score from
				DefaultWeightedEdge maxEdge = openEdges.poll();
				node = inGraph.getEdgeTarget(maxEdge);
				// Mark node as visited
				visitedNodes.add(node);
				log.info("\tPattern selected with score " + inGraph.getEdgeWeight(maxEdge) + ": " + inPatterns.indexOf(node));
			}
			else
			{
				continueExploring = false;
			}
		}

		if (visitedNodes.size() != inGraph.vertexSet().size())
		{
			log.warn("Exploration failed to visit all nodes in graph component");
		}

		return visitedNodes;
	}
}
