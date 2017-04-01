package edu.upf.taln.textplanning;

import Jama.Matrix;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.datastructures.*;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.SemanticPattern;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.pattern.PatternExtraction;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Text planner class. Given a set of references to entities and a set of documents, generates a text plan
 * containing contents in the documents relevant to the references.
 * Immutable class.
 */
public final class TextPlanner
{
	private final WeightingFunction weighting;
	private final EntitySimilarity similarity; // used to calculate semantic similarity
	private final static Logger log = LoggerFactory.getLogger(TextPlanner.class);

	public static class Options
	{
		public int numPatterns = 10; // Number of patterns to return
		public double relevanceLowerBound = 0.1; // Entities with relevance below this value have their score set to 0
		public double simLowerBound = 0.1; // Pairs of entities with similarity below this value have their score set to 0
		public double dampingFactor = 0.1; // damping factor to control balance between relevance bias and similarity
		public double rankingStopThreshold = 0.0001; // stopping threshold for the main ranking algorithm
		public boolean generateStats = false;
		public String stats = "";
	}

	/**
	 * @param inWeightingFunction functions used to weight contents
	 * @param inSimilarityFunction similarity function for pairs of entities
	 */
	public TextPlanner(WeightingFunction inWeightingFunction, EntitySimilarity inSimilarityFunction)
	{
		weighting = inWeightingFunction;
		similarity = inSimilarityFunction;
	}

	/**
	 * Generates a text plan from some contents encoded as annotated trees
	 * @param inContents initial set of contents
	 * @return list of patterns
	 */
	public List<SemanticPattern> planText(List<AnnotatedTree> inContents, Options inOptions)
	{
		try
		{
			log.info("***Planning started***");

			// 1- Collect entities in trees
			List<OrderedTree.Node<AnnotatedEntity>> nodes = inContents.stream()
					.map(AnnotatedTree::getPreOrder)
					.flatMap(List::stream)
					.collect(Collectors.toList());
			List<Entity> entities = nodes.stream()
					.map(OrderedTree.Node::getData)
					.distinct()
					.collect(Collectors.toList());

			// 2- Create ranking matrix
			log.info("**Creating ranking matrix**");
			Stopwatch timer = Stopwatch.createStarted();
			weighting.setCollection(inContents);
			Matrix rankingMatrix = PowerIterationRanking.createRankingMatrix(entities, weighting, similarity, inOptions);
			log.info("Creation of ranking matrix took " + timer.stop());

			// 3- Rank entities using biased semantic ranking and power iteration method
			log.info("**Power iteration ranking**");
			timer.reset(); timer.start();
			Matrix finalDistribution = PowerIterationRanking.run(rankingMatrix, inOptions.rankingStopThreshold);
			log.info("Power iteration ranking took " + timer.stop());
			double[] ranking = finalDistribution.getColumnPackedCopy();

			// 4- Create pattern extraction graph from ranking and cost function
			log.info("**Extracting patterns**");
			timer.reset(); timer.start();
			Map<Entity, Double> rankedEntities = IntStream.range(0, ranking.length)
					.boxed()
					.collect(Collectors.toMap(entities::get, i -> ranking[i]));
			SemanticGraph patternGraph =
					createPatternExtractionGraph(inContents, rankedEntities);

			List<SemanticPattern> patterns = PatternExtraction.extract(patternGraph, inOptions.numPatterns);
			log.info("Pattern extraction took " + timer.stop());

			// 5- Generate stats (optional)
			if (inOptions.generateStats)
			{
				log.info("**Generating stats**");
				timer.reset();
				timer.start();
				String stats = StatsReporter.reportStats(inContents, weighting, similarity);
				log.info(stats);
				log.info("Stats generation took " + timer.stop());
			}

			return patterns;
		}
		catch (Exception e)
		{
			log.error("***Planning failed***");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a pattern extraction graph
	 * @param inContents list of annotated trees
	 * @param rankedEntities entities in trees and their scores
	 * @return a semantic graph
	 */
	public static SemanticGraph createPatternExtractionGraph(List<AnnotatedTree> inContents,
	                                                         Map<Entity, Double> rankedEntities)
	{
		// Create graph
		SemanticGraph graph = new SemanticGraph(Edge.class);
		Map<String, Set<OrderedTree.Node<AnnotatedEntity>>> ids = new HashMap<>();

		// Iterate triple in each tree and populate graph from them
		inContents.stream()
				.map(AnnotatedTree::getDependencies)
				.flatMap(List::stream)
				.forEach(d -> {
					OrderedTree.Node<AnnotatedEntity> governor = d.getLeft();
					OrderedTree.Node<AnnotatedEntity> dependent = d.getMiddle();
					String role = d.getRight();

					// Add governing tree node to graph
					Node<String> govNode = createGraphNode(governor, rankedEntities);
					graph.addVertex(govNode); // does nothing if node existed
					ids.computeIfAbsent(govNode.id, v -> new HashSet<>()).add(governor);

					// Add dependent tree node to graph
					Node<String> depNode = createGraphNode(dependent, rankedEntities);
					graph.addVertex(depNode); // does nothing if node existed
					ids.computeIfAbsent(depNode.id, v -> new HashSet<>()).add(dependent);

					// Add edge
					if (!govNode.id.equals(depNode.id))
					{
						try
						{
							Edge e = new Edge(role, AnnotatedTree.isArgument(dependent));
							graph.addEdge(govNode, depNode, e);
						}
						catch (Exception e)
						{
							throw new RuntimeException("Failed to add edge between " + govNode.id + " and " + depNode.id + ": " + e);
						}
					}
					else
						log.warn("Dependency between two nodes with same id " + depNode.id);
				});

		return graph;
	}

	private static Node<String> createGraphNode(OrderedTree.Node<AnnotatedEntity> inNode, Map<Entity, Double> rankedEntities)
	{
		AnnotatedEntity e = inNode.getData();
		boolean isPredicate = AnnotatedTree.isPredicate(inNode);
		String id = e.getEntityLabel();
		if (isPredicate || id.equals("_")) // if a predicate, make node unique by appending ann id
			id += ":" + e.getAnnotation().getId();
		double govWeight = rankedEntities.get(e);

		return new Node<>(id, e.getEntityLabel(), govWeight, isPredicate, generateConLLForPredicate(inNode));
	}

	/**
	 * Generates ConLL for a predicate and all its arguments and adjuncts.
	 * @param inNode node annotating entity
	 * @return conll
	 */
	private static String generateConLLForPredicate(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		if (!AnnotatedTree.isPredicate(inNode))
			return "";

		// Create a subtree consisting of the node and its direct descendents
		AnnotatedTree subtree = new AnnotatedTree(inNode.getData());
		inNode.getChildrenData().forEach(a -> subtree.getRoot().addChild(a));

		// Generate conll
		ConLLAcces conll = new ConLLAcces();
		return conll.writeTrees(Collections.singletonList(subtree));
	}

}
