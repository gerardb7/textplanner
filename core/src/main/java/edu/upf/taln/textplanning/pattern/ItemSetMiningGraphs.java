package edu.upf.taln.textplanning.pattern;

import ca.pfv.spmf.algorithms.frequentpatterns.fpgrowth.AlgoFPMax;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import edu.upf.taln.textplanning.input.DocumentAccess;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.AbstractGraph;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A version of the itemset mining algorithm taking as input DAGs.
 */
public class ItemSetMiningGraphs
{
	public Set<AnnotatedTree> getPatterns(
			List<DirectedAcyclicGraph<AnnotatedEntity, DocumentAccess.LabelledEdge>> inContents)
	{
		// Convert graphs to itemsets
		List<Set<String>> itemSets = inContents.stream()
				.map(AbstractGraph::vertexSet)
				.map(g -> g.stream()
						.filter(ItemSetMiningGraphs::isItem) // filter annotations
						.map(AnnotatedEntity::getEntityLabel)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// Compile alphabet of item labels
		List<String> alphabet = itemSets.stream()
				.map(s -> s.stream()
						.collect(Collectors.toSet()))
				.flatMap(Set::stream)
				.distinct()
				.sorted()
				.collect(Collectors.toList());

		// Encode itemsets using indexes in alphabet list
		List<Set<Integer>> encodedItemSets = itemSets.stream()
				.map(s -> s.stream()
						.map(alphabet::indexOf)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// Run the mining algorithm
		Itemsets maximalSets;
		try
		{
			AlgoFPMax alg = new AlgoFPMax();
			maximalSets = alg.runAlgorithm(encodedItemSets, 3);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Mining algorithm failed: " + e);
		}

		// Decode itemsets returned by algorithm by converting indexes to actual items
		List<Set<String>> decodedMaximalSets = maximalSets.getLevels().stream()
				.flatMap(List::stream) // treat all levels as the same
				.map(Itemset::getItems)
				.map(s -> Arrays.stream(s)
						.mapToObj(alphabet::get)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// For each maximal itemset, find the shortest graph that contains it
		List<Integer> shortestGraphs = decodedMaximalSets.stream()
				.map(m -> itemSets.stream()
						.filter(s -> s.containsAll(m)) // find trees that contain this maximal set
						.map(s -> inContents.get(itemSets.indexOf(s))) // and get the shortest one
						.min((t1, t2) -> Integer.compare(t1.vertexSet().size(), t2.vertexSet().size())))
				.map(Optional::get)
				.map(inContents::indexOf)
				.collect(Collectors.toList()); // yes, we actually want to preserve duplicate trees

		// Find subtrees for each shortest tree and maximal set
		return shortestGraphs.stream()
				.map(i -> getTrees(inContents.get(i), decodedMaximalSets.get(i)))
				.flatMap(Set::stream)
				.collect(Collectors.toSet());
	}

	/**
	 * Decides whether an annotation can be considered an item based on its POS
	 *
	 * @param inAnn annotation to be evaluated
	 * @return true if it is an entity
	 */
	private static boolean isItem(AnnotatedEntity inAnn)
	{
		return inAnn.getAnnotation().getPOS().startsWith("NN") || // nouns
				inAnn.getAnnotation().getPOS().startsWith("VB") || // verbs
				inAnn.getAnnotation().getPOS().startsWith("JJ") || // nominal modifiers
				inAnn.getAnnotation().getPOS().startsWith("CD") || // cardinal numbers
				inAnn.getAnnotation().getPOS().startsWith("FW");   // and foreign words
	}

	/**
	 * Given a DAG and an itemset corresponding to a subset of its vertices, return subtrees spanning over all the
	 * itemset nodes and satisfying certain linguistic constraints.
	 *
	 * @param inGraph   the original tree
	 * @param inItemSet an itemset where each item corresponds to one or more nodes in the tree
	 * @return one or more subtrees
	 */
	private Set<AnnotatedTree> getTrees(DirectedAcyclicGraph<AnnotatedEntity, DocumentAccess.LabelledEdge> inGraph,
	                                    Set<String> inItemSet)
	{
		// Collect nodes which correspond to the items in the itemset
		Set<AnnotatedEntity> itemSetNodes = inItemSet.stream()
				.map(i -> inGraph.vertexSet().stream()
						.filter(n -> n.getEntityLabel().equals(i)))
				.flatMap(Function.identity())
				.collect(Collectors.toSet()); // removes duplicates

		// Collect all nodes in the DAG in the paths from the itemset nodes to the sources of the DAG
		Set<AnnotatedEntity> sources = new HashSet<>();
		Set<AnnotatedEntity> nodesInPaths = new HashSet<>();
		nodesInPaths.addAll(itemSetNodes);
		boolean anyAncestors = true;
		while (anyAncestors)
		{
			Set<AnnotatedEntity> ancestors = nodesInPaths.stream()
					.map(inGraph::incomingEdgesOf) // yes, we follow all paths
					.flatMap(Set::stream)
					.map(inGraph::getEdgeSource)
					.collect(Collectors.toSet());

			anyAncestors = !ancestors.isEmpty();

			nodesInPaths.addAll(ancestors);
			ancestors.stream()
					.filter(n -> inGraph.inDegreeOf(n) == 0)
					.forEach(sources::add);
		}

		// Determine subtrees for each source
		return sources.stream()
				.map(s -> new AnnotatedTree(s, 0.0)) // @Todo position information!
				.map(t -> {
					populateSubTree(inGraph, t.getRoot(), nodesInPaths);
					return t;
				})
				.collect(Collectors.toSet());
	}

	/**
	 * Creates a subtree with root @inNewRoot from a DAG
	 *
	 * @param inGraph           the DAG
	 * @param inSubtreeRoot     the root of the subtree
	 * @param inCompulsoryNodes set of vertices in the DAG which must be part of the subtree
	 */
	private void populateSubTree(DirectedAcyclicGraph<AnnotatedEntity, DocumentAccess.LabelledEdge> inGraph,
	                             OrderedTree.Node<AnnotatedEntity> inSubtreeRoot,
	                             Set<AnnotatedEntity> inCompulsoryNodes)
	{
		inGraph.outgoingEdgesOf(inSubtreeRoot.getData()).stream()
				.filter(e -> (inCompulsoryNodes.contains(inGraph.getEdgeTarget(e)) ||
						isArgument(e) ||
						isNegation(inGraph.getEdgeTarget(e)) ||
						isNumber(inGraph.getEdgeTarget(e)) ||
						isPlainAdverb(inGraph, inGraph.getEdgeTarget(e)) ||
						!isVerbWithRelative(inGraph, inGraph.getEdgeTarget(e)) ||
						isName(e)))
				.forEach(e -> populateSubTree(inGraph,
						inSubtreeRoot.addChild(inGraph.getEdgeTarget(e)), inCompulsoryNodes)); // recursive call
	}

	private boolean isArgument(DocumentAccess.LabelledEdge inEdge)
	{
		// Predicate to test if a node corresponds to a modifier
		String role = inEdge.getLabel();
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}

	private boolean isNegation(AnnotatedEntity inAnn)
	{
		String[] forms = {"no", "not"};
		String[] pos = {"RB", "JJ"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inAnn.getAnnotation().getPOS())) &&
				Arrays.stream(forms).anyMatch(f -> f.equals(inAnn.getAnnotation().getForm()));
	}

	private boolean isNumber(AnnotatedEntity inAnn)
	{
		String[] pos = {"CD"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inAnn.getAnnotation().getPOS()));
	}

	private boolean isPlainAdverb(DirectedAcyclicGraph<AnnotatedEntity, DocumentAccess.LabelledEdge> inGraph,
	                              AnnotatedEntity inAnn)
	{
		String[] pos = {"WRB"};
		return inGraph.outDegreeOf(inAnn) == 0 && Arrays.stream(pos).anyMatch(n -> n.equals(inAnn.getAnnotation().getPOS()));
	}

	private boolean isVerbWithRelative(DirectedAcyclicGraph<AnnotatedEntity, DocumentAccess.LabelledEdge> inGraph,
	                                   AnnotatedEntity inAnn)
	{
		if (!inAnn.getAnnotation().getPOS().startsWith("VB"))
		{
			return false;
		}
		if (inGraph.outDegreeOf(inAnn) != 1)
		{
			return false;
		}

		DocumentAccess.LabelledEdge e = inGraph.outgoingEdgesOf(inAnn).iterator().next();
		return inGraph.getEdgeTarget(e).getAnnotation().getPOS().equalsIgnoreCase("WDT");
	}

	private boolean isName(DocumentAccess.LabelledEdge inEdge)
	{
		return inEdge.getLabel().equals("NAME");
	}
}
