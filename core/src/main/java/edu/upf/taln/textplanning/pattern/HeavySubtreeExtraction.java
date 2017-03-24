package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.SubTree;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pattern extraction strategy based on finding densest subtrees in a semantic graph.
 * Determination of heaviest subtrees approximated using greedy algorithm as suggested in:
 * "Event detection in activity networks" Rozenshtein, P., Anagnostopoulos, A., Gionis, A., Tatti, N. (2014)
 */
public class HeavySubtreeExtraction
{
	private final static double lambda = 1.0;

	public static List<SubTree> extract(SemanticGraph inGraph, int inNumPatterns)
	{
		// Get highest ranked nodes
		return inGraph.vertexSet().stream()
				.sorted((v1, v2) -> Double.compare(v1.weight, v2.weight))
				.limit(inNumPatterns)
				.map(n -> getDenseSubtrees(n, inGraph))
				.collect(Collectors.toList());
	}

	/**
	 * Starting from an initial node, extract a dense subtree by greedily adding edges.
	 * Whenever an edge points to a predicate, automatically incorporate its arguments to the subtree.
	 * @return a dense subtree
	 */
	private static SubTree getDenseSubtrees(Node inInitialNode, SemanticGraph inBaseGraph)
	{
		PriorityQueue<Pair<SubTree, Double>> rankedExpansions =
			new PriorityQueue<>((s1, s2) -> Double.compare(s1.getRight(), s2.getRight()));
		SubTree subtree =	new SubTree(inBaseGraph, inInitialNode);

		// calculate expansions, weight them and add to sorted queue
		getExpansions(subtree).forEach(s -> rankedExpansions.add(Pair.of(s, calculateWeight(s))));

		double q = inBaseGraph.edgeSet().size(); // Q of an empty subtree equals to the constant term D(V)
		boolean stop;
		do
		{
			SubTree bestExpansion = rankedExpansions.poll().getKey();
			double newValue = calculateWeight(bestExpansion); // weight is recalculated for whole subtree, not very efficient
			double delta = newValue - q;
			stop = delta <= 0.0;
			if (!stop)
			{
				subtree = bestExpansion;
				q = newValue;
				getExpansions(subtree).forEach(s -> rankedExpansions.add(Pair.of(s, calculateWeight(s))));
			}
		}
		while (!stop);

		return subtree;
	}

	/**
	 * Returns the set of expansions of a subtree relative to the graph it is part of.
	 * New nodes marked as predicates are automatically expanded to incorporate all their arguments.
	 * @return the set of expansions as new trees expanding the original subtree
	 */
	private static Set<SubTree> getExpansions(SubTree subtree)
	{
		SemanticGraph base = subtree.getBase();
		return subtree.vertexSet().stream()
				.map(v -> base.outgoingEdgesOf(v).stream()
						.filter(e -> !subtree.containsEdge(e)) // exclude edges already in subtree
						.map(e -> {
							// If edge target is a predicate, expand it to include all its args
							Set<Edge> expandedList = new HashSet<>();
							if (base.getEdgeTarget(e).isPredicate)
							{
								base.outgoingEdgesOf(base.getEdgeTarget(e)).stream() // only outgoing links
										.filter(e2 -> e != e2)
										.filter(e2 -> !subtree.containsEdge(e2))
										.filter(e2 -> e2.isArg) // Add args only
										.forEach(expandedList::add);
							}

							return expandedList;
						})  // at this point we have extensions: sets of new edges. Create a new subtree for each
						.map(e -> {
							SubTree expandedTree = new SubTree(subtree);
							e.forEach(expandedTree::addChild);
							return expandedTree;
						}))
				.flatMap(Function.identity())
				.collect(Collectors.toSet());
	}

	/**
	 * Calculates the cost of a subtree as a combination of node weights and edge distances
	 * @return cost of the subtree
	 */
	private static double calculateWeight(SubTree subtree)
	{
		double ws = subtree.vertexSet().stream()
				.mapToDouble(n -> n.weight)
				.sum();
		int ds = subtree.edgeSet().size(); // assumes all edge distances to be 1
		int dv = subtree.getBase().edgeSet().size();
		return lambda*ws - ds + dv;
	}
}
