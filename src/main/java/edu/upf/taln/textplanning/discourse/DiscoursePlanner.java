package edu.upf.taln.textplanning.discourse;

import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Discourse planner class. In its current version orders a set of patterns according to their relevance and
 * similarity using a greedy algorithm.
 */
public class DiscoursePlanner
{
	private static SimpleWeightedGraph<Integer, DefaultWeightedEdge> g;

	/**
	 * Orders a set of patterns by taking into account both their pair-wise similarity and their average relevance
	 * scores.
	 *
	 * @param patterns list of patterns to structure
	 * @param entitySim similarity function between pairs of entities in the patterns
	 * @return list of patterns
	 */
	public static List<SemanticTree> structurePatterns(List<SemanticTree> patterns, ItemSimilarity entitySim)
	{
		// Weight patterns by averaging their node weights
		//noinspection RedundantTypeArguments
		List<Pair<SemanticTree, Double>> rankedPatterns = patterns.stream()
				.map(p -> Pair.of(p, p.vertexSet().stream()
						.map(Node::getEntity)
						.mapToDouble(Entity::getWeight)
						.average().orElse(0.0)))
				.sorted(Comparator.comparing(Pair<SemanticTree, Double>::getRight).reversed())
				.collect(Collectors.toList());

		// Create graph with patterns as vertices
		g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
		int n = patterns.size();
		IntStream.range(0, n).forEach(g::addVertex);

		// Weight edges according to similarity function
		PatternSimilarity sim = new PatternSimilarity(entitySim);
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					if (i == j)
						return;
					if (g.containsEdge(i, j))
						return;
					DefaultWeightedEdge e = g.addEdge(i, j);
					double s = sim.getSimilarity(rankedPatterns.get(i).getKey(), rankedPatterns.get(j).getKey());
					g.setEdgeWeight(e, s);
				}));

		// Create auto-sorted list of open edges based on similarity of edges and relevance of the pattern-nodes they
		// point to
		Comparator<DefaultWeightedEdge> comparator = (e1, e2) -> {
			double s1 = g.getEdgeWeight(e1); // similarity between patterns
			double r1 = rankedPatterns.get(g.getEdgeTarget(e1)).getRight(); // relevance of target pattern
			double w1 = (s1 + r1)/2.0; // final weight
			double s2 = g.getEdgeWeight(e2);
			double r2 = rankedPatterns.get(g.getEdgeTarget(e2)).getRight();
			double w2 = (s2 + r2)/2.0;

			return Double.compare(w2, w1); // Descending order
		};
		PriorityQueue<DefaultWeightedEdge> openEdges = new PriorityQueue<>(comparator);

		// start greedy exploration from highest ranked pattern
		List<Integer> visitedNodes = new ArrayList<>();
		int currentNode = 0; // start with highest ranked node
		visitedNodes.add(0);
		boolean continueExploring = true;

		while (continueExploring)
		{
			// Expand set of open edges with the out links of the current node
			openEdges.addAll(g.edgesOf(currentNode));

			// Discard any edges leading to visited nodes
			openEdges.removeIf(e -> visitedNodes.contains(g.getEdgeTarget(e)));

			if (!openEdges.isEmpty())
			{
				DefaultWeightedEdge maxEdge = openEdges.poll(); // Ger edge with highest weight
				currentNode = g.getEdgeTarget(maxEdge);
				// Mark node as visited
				visitedNodes.add(currentNode);
//				log.info("\tPattern selected with score " + inGraph.getEdgeWeight(maxEdge) + ": " + inPatterns.indexOf(node));
			}
			else
			{
				continueExploring = false;
			}
		}

		if (visitedNodes.size() != g.vertexSet().size())
		{
			throw new RuntimeException("Exploration failed to visit all nodes in structuring graph");
		}

		return visitedNodes.stream()
				.map(rankedPatterns::get)
				.map(Pair::getLeft)
				.collect(Collectors.toList());
	}
}
