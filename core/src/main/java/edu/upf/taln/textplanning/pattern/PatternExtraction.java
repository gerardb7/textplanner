package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.*;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.SubGraph;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.DirectedGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pattern extraction strategy based on finding dense subgraphs (multitrees) in a semantic graph.
 * Determination of heaviest subgraphs approximated using greedy algorithm as suggested in:
 * "Event detection in activity networks" Rozenshtein, P., Anagnostopoulos, A., Gionis, A., Tatti, N. (2014)
  */
public class PatternExtraction
{
	private final static double lambda = 1.0;

	/**
	 * Creates a pattern extraction graph
	 * @param inContents list of annotated trees
	 * @param rankedEntities entities in trees and their scores
	 * @return a semantic graph
	 */
	public static SemanticGraph createPatternExtractionGraph(List<SemanticTree> inContents,
	                                                         Map<Entity, Double> rankedEntities)
	{
		// Create graph
		SemanticGraph graph = new SemanticGraph(Edge.class);
		Map<String, Set<Node>> ids = new HashMap<>();

		// Iterate triple in each tree and populate graph from them
		inContents.stream()
				.forEach(t -> {
					for (Edge e : t.edgeSet())
					{
						Node governor = t.getEdgeSource(e);
						Node dependent = t.getEdgeTarget(e);

						// Add governing tree node to graph
						Node govNode = createGraphNode(t, governor, rankedEntities);
						graph.addVertex(govNode); // does nothing if node existed
						ids.computeIfAbsent(govNode.id, v -> new HashSet<>()).add(governor);

						// Add dependent tree node to graph
						Node depNode = createGraphNode(t, dependent, rankedEntities);
						graph.addVertex(depNode); // does nothing if node existed
						ids.computeIfAbsent(depNode.id, v -> new HashSet<>()).add(dependent);

						// Add edge
						if (!govNode.id.equals(depNode.id))
						{
							try
							{
								Edge e2 = new Edge(e.role, e.isArg);
								graph.addEdge(govNode, depNode, e2);
							}
							catch (Exception ex)
							{
								throw new RuntimeException("Failed to add edge between " + govNode.id + " and " + depNode.id + ": " + ex);
							}
						}
						else
							throw new RuntimeException("Dependency between two nodes with same id " + depNode.id);
					}
				});

		return graph;
	}

	private static Node createGraphNode(SemanticTree t, Node n, Map<Entity, Double> rankedEntities)
	{
		String id = n.getEntity().getEntityLabel();
		Annotation a = ((AnnotatedEntity)n.getEntity()).getAnnotation();
		if (t.isPredicate(n) || id.equals("_")) // if a predicate, make node unique by appending ann id
			id += ":" + a.getId();
		double govWeight = rankedEntities.get(n.getEntity());

		return new Node(id, n.getEntity(), govWeight);
	}

	/**
	 * Extracts patterns from a semantic graph
	 * @return the set of extracted patterns
	 */
	public static Set<SemanticTree> extract(SemanticGraph g, int inNumPatterns)
	{
		// Work out average node weight, which will be used as weight for edges
		double avgWeight = g.vertexSet().stream().mapToDouble(v -> v.weight).average().orElse(1.0);

		List<Node> sortedNodes = g.vertexSet().stream()
				.sorted((v1, v2) -> Double.compare(v2.weight, v1.weight))// swapped v1 and v2 to obtain descending order
				.collect(Collectors.toList());

//		Map<String, List<Node>> ids = sortedNodes.stream()
//				.collect(Collectors.groupingBy(n -> n.entity));

		// Get highest ranked nodes
		List<Node> topEntities = sortedNodes.stream()
				.filter(v -> !g.isPredicate(v))
				.limit(inNumPatterns)
				.collect(Collectors.toList());

		// Create a pattern for each
		return topEntities.stream()
				.map(n -> getSemanticPatterns(n, g, avgWeight))
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
	}

	/**
	 * Starting from an initial node, extract patterns by greedily adding edges to directed subgraph, and then
	 * converting the subgraph to a set of trees.
	 * @return semantic patterns
	 */
	private static Set<SemanticTree> getSemanticPatterns(Node inInitialNode, SemanticGraph inBaseGraph, double inEdgeWeight)
	{
		PriorityQueue<Pair<SubGraph, Double>> rankedExpansions =
			new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight())); // swapped s1 and s2 to obtain descending order
		SubGraph subgraph = new SubGraph(inBaseGraph, inInitialNode);

		// calculate expansions, weight them and add to sorted queue
		getExpansions(subgraph).forEach(s -> rankedExpansions.add(Pair.of(s, calculateWeight(s, inEdgeWeight))));

		double q = inBaseGraph.edgeSet().size() * inEdgeWeight; // Q of an empty subgraph equals to the constant term D(V)
		boolean stop;
		do
		{
			Pair<SubGraph, Double> firstItem = rankedExpansions.poll();
			SubGraph bestExpansion = firstItem.getKey();
			double newValue = firstItem.getValue();
			double delta = newValue - q;
			stop = delta <= 0.0;
			if (!stop)
			{
				subgraph = bestExpansion;
				q = newValue;
				getExpansions(subgraph).forEach(s -> rankedExpansions.add(Pair.of(s, calculateWeight(s, inEdgeWeight))));
			}
		}
		while (!stop);

		return SemanticTree.createTrees(subgraph);
	}

	/**
	 * Returns the set of expansions of a subgraph relative to the graph it is being extracted from.
	 * New nodes marked as predicates are automatically expanded to incorporate all their arguments.
	 * @return a set of expanded subgraphs
	 */
	private static Set<SubGraph> getExpansions(SubGraph subgraph)
	{
		return subgraph.vertexSet().stream()
				.map(v -> getExpansions(subgraph, v)) // get expansions for each node
				.flatMap(List::stream)
				.map(e -> new SubGraph(subgraph, e)) // create new subgraph for each expansion
				.collect(Collectors.toSet());
	}

	private static List<List<Edge>> getExpansions(SubGraph subGraph, Node node)
	{
		DirectedGraph<Node, Edge> base = subGraph.getBase();
		List<List<Edge>> expansionList = new ArrayList<>();

		// Each edge leads to a candidate expansion
		base.edgesOf(node).stream()
				.filter(e -> !subGraph.containsEdge(e))
				.forEach(e -> {
					List<Edge> expansion = new ArrayList<>();
					expansion.add(e);

					// New nodes are automatically expanded to incorporate all their arguments
					Node otherNode = base.getEdgeSource(e) == node ? base.getEdgeTarget(e) : base.getEdgeSource(e);
					expandNode(subGraph, otherNode).stream()
							.filter(arg -> e != arg) // be careful not add e twice
							.forEach(expansion::add);
					expansionList.add(expansion);
				});

		return expansionList;
	}

	private static List<Edge> expandNode(SubGraph pattern, Node node)
	{
		return pattern.getBase().outgoingEdgesOf(node).stream()
				.filter(e -> e.isArg)
				.filter(e -> !pattern.containsEdge(e))
				.collect(Collectors.toList());
	}

	/**
	 * Calculates the cost of a pattern as a combination of node weights and edge distances
	 * @param pattern pattern to weight
	 * @param edgeWeight fixed weight assigned to each edge
	 * @return cost of the pattern
	 */
	private static double calculateWeight(SubGraph pattern, double edgeWeight)
	{
		double ws = pattern.vertexSet().stream()
				.mapToDouble(n -> n.weight)
				.sum();
		double ds = pattern.edgeSet().size() * edgeWeight; // assign edges the average node weight
		double dv = pattern.getBase().edgeSet().size() * edgeWeight;
		return lambda*ws - ds + dv;
	}
}
