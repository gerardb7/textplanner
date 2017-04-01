package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.SemanticPattern;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pattern extraction strategy based on finding dense subgraphs (multitrees) in a semantic graph.
 * Determination of heaviest subgraphs approximated using greedy algorithm as suggested in:
 * "Event detection in activity networks" Rozenshtein, P., Anagnostopoulos, A., Gionis, A., Tatti, N. (2014)
  */
public class PatternExtraction
{
	private final static double lambda = 1.0;

	public static List<SemanticPattern> extract(SemanticGraph inGraph, int inNumPatterns)
	{
		// Work out average node weight, which will be used as weight for edges
		double avgWeight = inGraph.vertexSet().stream().mapToDouble(v -> v.weight).average().orElse(1.0);

		List<Node> sortedNodes = inGraph.vertexSet().stream()
				.sorted((v1, v2) -> Double.compare(v2.weight, v1.weight))// swapped v1 and v2 to obtain descending order
				.collect(Collectors.toList());

//		Map<String, List<Node>> ids = sortedNodes.stream()
//				.collect(Collectors.groupingBy(n -> n.entity));

		// Get highest ranked nodes
		List<Node> topEntities = sortedNodes.stream()
				.filter(v -> !v.isPredicate)
				.limit(inNumPatterns)
				.collect(Collectors.toList());

		// Create a pattern for each
		return topEntities.stream()
				.map(n -> getSemanticPattern(n, inGraph, avgWeight))
				.collect(Collectors.toList());
	}

	/**
	 * Starting from an initial node, extract a patterns by greedily adding edges to a multitree.
	 * Whenever an edge points to a predicate, expand it automatically and incorporate its arguments to the pattern.
	 * @return a semantic pattern
	 */
	private static SemanticPattern getSemanticPattern(Node inInitialNode, SemanticGraph inBaseGraph, double inEdgeWeight)
	{
		PriorityQueue<Pair<SemanticPattern, Double>> rankedExpansions =
			new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight())); // swapped s1 and s2 to obtain descending order
		SemanticPattern pattern = new SemanticPattern(inBaseGraph, inInitialNode);

		// calculate expansions, weight them and add to sorted queue
		getExpansions(pattern).forEach(s -> rankedExpansions.add(Pair.of(s, calculateWeight(s, inEdgeWeight))));

		double q = inBaseGraph.edgeSet().size() * inEdgeWeight; // Q of an empty pattern equals to the constant term D(V)
		boolean stop;
		do
		{
			Pair<SemanticPattern, Double> firstItem = rankedExpansions.poll();
			SemanticPattern bestExpansion = firstItem.getKey();
			double newValue = firstItem.getValue();
			double delta = newValue - q;
			stop = delta <= 0.0;
			if (!stop)
			{
				pattern = bestExpansion;
				q = newValue;
				getExpansions(pattern).forEach(s -> rankedExpansions.add(Pair.of(s, calculateWeight(s, inEdgeWeight))));
			}
		}
		while (!stop);

		return pattern;
	}

	/**
	 * Returns the set of expansions of a pattern relative to the graph it is being extracted from.
	 * New nodes marked as predicates are automatically expanded to incorporate all their arguments.
	 * @return a set of expanded patterns
	 */
	private static Set<SemanticPattern> getExpansions(SemanticPattern pattern)
	{
		return pattern.vertexSet().stream()
				.map(v -> getExpansions(pattern, v)) // get expansions for each node
				.flatMap(List::stream)
				.map(e -> {
					// create new pattern for each expansion
					SemanticPattern expandedPattern = new SemanticPattern(pattern);
					e.forEach(expandedPattern::expand);
					return expandedPattern;
				})
				.collect(Collectors.toSet());
	}

	private static List<List<Edge>> getExpansions(SemanticPattern pattern, Node node)
	{
		SemanticGraph base = pattern.getBase();
		List<List<Edge>> expansionList = new ArrayList<>();

		// Each edge leads to a candidate expansion
		base.edgesOf(node).stream()
				.filter(e -> !pattern.containsEdge(e))
				.forEach(e -> {
					List<Edge> expansion = new ArrayList<>();
					expansion.add(e);

					// New nodes are automatically expanded to incorporate all their arguments
					Node otherNode = base.getEdgeSource(e) == node ? base.getEdgeTarget(e) : base.getEdgeSource(e);
					expandNode(pattern, otherNode).stream()
							.filter(arg -> e != arg) // be careful not add e twice
							.forEach(expansion::add);
					expansionList.add(expansion);
				});

		return expansionList;
	}

	private static List<Edge> expandNode(SemanticPattern pattern, Node node)
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
	private static double calculateWeight(SemanticPattern pattern, double edgeWeight)
	{
		double ws = pattern.vertexSet().stream()
				.mapToDouble(n -> n.weight)
				.sum();
		double ds = pattern.edgeSet().size() * edgeWeight; // assign edges the average node weight
		double dv = pattern.getBase().edgeSet().size() * edgeWeight;
		return lambda*ws - ds + dv;
	}
}
