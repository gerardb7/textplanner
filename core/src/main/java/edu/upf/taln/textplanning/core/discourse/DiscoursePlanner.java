package edu.upf.taln.textplanning.core.discourse;

import edu.upf.taln.textplanning.core.similarity.SemanticTreeSimilarity;
import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;
import edu.upf.taln.textplanning.core.structures.SemanticTree;
import edu.upf.taln.textplanning.core.utils.DebugUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Discourse planner: sorts a set of graphs according to their average variable rank and pairwise similarity.
 */
public class DiscoursePlanner
{
	private static class DiscourseGraph extends SimpleWeightedGraph<Integer, DefaultWeightedEdge>
	{
		DiscourseGraph() { super(DefaultWeightedEdge.class); }
	}

	private final SemanticTreeSimilarity sim;
	private final static Logger log = LogManager.getLogger();


	public DiscoursePlanner(SemanticTreeSimilarity sim)
	{
		this.sim = sim;
	}

	/**
	 * Orders a set of graphs by taking into account both their pair-wise similarity and their average relevance
	 * scores.
	 */
	public List<SemanticSubgraph> structureSubgraphs(Collection<SemanticSubgraph> graphs)
	{
		if (graphs.isEmpty())
			return new ArrayList<>();

		// Convert graphs to lists
		SemanticTree[] trees = graphs.stream()
				.map(SemanticTree::new)
				.toArray(t -> new SemanticTree[graphs.size()]);

		// Weight graphs by averaging their node weights
		double[] rank = Arrays.stream(trees)
				.map(SemanticTree::getAverageWeight)
				.sorted(Comparator.reverseOrder())
				.mapToDouble(d -> d)// unboxing
				.toArray();

		// Create graph with indexes to trees as vertices
		DiscourseGraph g = new DiscourseGraph();
		int n = trees.length;
		IntStream.range(0, n).forEach(g::addVertex);

		// Weight edges according to similarity function
		IntStream.range(0, n).forEach(i ->
				IntStream.range(0, n).forEach(j ->
				{
					if (i == j)
						return;
					if (g.containsEdge(i, j))
						return;
					DefaultWeightedEdge e = g.addEdge(i, j);
					double s = sim.getSimilarity(trees[i], trees[j]);
					g.setEdgeWeight(e, s);
				}));

		// Create auto-sorted list of open edges based on similarity of edges and relevance of the subgraph-nodes they
		// point to
		Comparator<DefaultWeightedEdge> comparator = (e1, e2) -> {
			double s1 = g.getEdgeWeight(e1); // similarity between graphs
			double r1 = rank[g.getEdgeTarget(e1)]; // relevance of target subgraph
			double w1 = (s1 + r1)/2.0; // final weight
			double s2 = g.getEdgeWeight(e2);
			double r2 = rank[g.getEdgeTarget(e2)];
			double w2 = (s2 + r2)/2.0;

			return Double.compare(w2, w1); // Descending order
		};
		PriorityQueue<DefaultWeightedEdge> openEdges = new PriorityQueue<>(comparator);

		// start greedy exploration from highest ranked subgraph
		List<Integer> visitedNodes = new ArrayList<>();
		int currentNode = 0; // start with highest ranked node
		visitedNodes.add(0);
		boolean continueExploring = !g.vertexSet().isEmpty(); // if empty, don't bother

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
			}
			else
			{
				continueExploring = false;
			}
		}

		if (visitedNodes.size() != g.vertexSet().size())
			log.error("Exploration failed to visit all nodes in structuring graph");

		List<SemanticSubgraph> sorted_graphs = visitedNodes.stream()
				.map(i -> trees[i])
				.map(SemanticTree::asGraph)
				.collect(Collectors.toList());
		log.debug("Sorted graphs:\n" + DebugUtils.printSubgraphs(sorted_graphs));

		return sorted_graphs;
	}
}
