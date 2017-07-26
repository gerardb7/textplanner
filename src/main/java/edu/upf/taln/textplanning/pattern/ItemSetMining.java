package edu.upf.taln.textplanning.pattern;

import ca.pfv.spmf.algorithms.frequentpatterns.fpgrowth.AlgoFPMax;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import edu.upf.taln.textplanning.structures.*;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Select trees that cover frequent patterns of annotations in a collection of annotated trees
 * Patterns are found using an algorithm for mining maximal sets (of annotations).
 * For each pattern found, the smallest input tree where the pattern occurs is returned.
 */
public class ItemSetMining
{
	private final boolean debug = true;
	private final static Logger log = LoggerFactory.getLogger(ItemSetMining.class);

	public Set<ContentPattern> getPatterns(List<ContentGraph> inContents)
	{
		// Collect sets of relevant nodes from trees
		List<Set<Entity>> annSets = inContents.stream()
				.map(s -> s.vertexSet().stream()
						.filter(e -> isItem(s.getAnchors(e).get(0).getHead()))
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// Now convert annotation sets to item sets by picking their labels
		List<Set<String>> itemSets = annSets.stream()
				.map(as -> as.stream()
						.map(Entity::getId)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// Compile alphabet of item labels
		List<String> alphabet = itemSets.stream()
				.flatMap(Set::stream)
				.distinct()
				.sorted()
				.collect(Collectors.toList());

		// Encode itemsets using indexes in the alphabet list, as requested by the mining algorithm implementation
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
			maximalSets = alg.runAlgorithm(encodedItemSets, 2); // Minimal support set to 2
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

		// Filter item sets
		List<Set<String>> filteredMaximalSets = decodedMaximalSets.stream()
				.filter(s -> s.size() > 1) // only interested in sets of size 2 or greater
				.filter(ItemSetMining::hasVerb)         // sets must contain at least one verb
				.filter(ItemSetMining::hasNominalSense) // sets must contain at least a nominal sense
				.collect(Collectors.toList());

		// Work out the average position of each maximal set from the position of each of its supporting patterns
		List<Double> avgPositions = filteredMaximalSets.stream()
				.map(ms -> itemSets.stream()
						.filter(is -> is.containsAll(ms))  // find sets that contain this maximal set
						.map(is -> inContents.get(itemSets.indexOf(is))) // find corresponding tree
						.mapToDouble(s -> 0.0) // todo this needs to be fixed
						.average().orElse(0.0))
				.collect(Collectors.toList());

		// For each maximal itemset, find the smallest graph that contains it
		List<ContentGraph> shortestTrees = filteredMaximalSets.stream()
				.map(m -> itemSets.stream()
						.filter(s -> s.containsAll(m)) // find sets that contain this maximal set
						.map(is -> inContents.get(itemSets.indexOf(is))) // find corresponding graph
						.min(Comparator.comparingInt(t -> t.vertexSet().size()))) // min length
				.map(Optional::get)
				.collect(Collectors.toList()); // yes, we do want to preserve duplicate graphs

		//noinspection ConstantConditions
		if (debug)
		{
			Map<String, String> refsAndForms = inContents.stream()
					.map(ContentGraph::vertexSet)
					.flatMap(Set::stream)
					.collect(Collectors.toMap(Entity::getId, n -> "", (n1, n2) -> n1)); // todo fix code to get forms

			List<String> setStrings = filteredMaximalSets.stream()
					.map(s -> s.stream()
							.map(i -> refsAndForms.containsKey(i) ? i + "-" + refsAndForms.get(i) : i)
							.collect(Collectors.joining(", ")))
					.collect(Collectors.toList());
			IntStream.range(0, filteredMaximalSets.size())
					.forEach(i -> {
						long support = itemSets.stream()
								.filter(s -> s.containsAll(filteredMaximalSets.get(i)))
								.count();
						log.info("Maximal itemset: [" + setStrings.get(i) + "] support=" + support);
					});
		}

		// Find subtrees for each shortest tree and maximal set
		return IntStream.range(0, filteredMaximalSets.size())
				.mapToObj(i -> getSubGraph(shortestTrees.get(i), filteredMaximalSets.get(i), avgPositions.get(i)))
				.collect(Collectors.toSet()); // this where we remove duplicates
	}

	/**
	 * Decides whether an annotation can be considered an item based on the data it annotates
	 *
	 * @param n annotation to be evaluated
	 * @return true if it annotates entity
	 */
	private static boolean isItem(AnnotatedWord n)
	{
		return !n.getLemma().startsWith("be_") &&
				(n.getPOS().startsWith("NN") || // nouns
						n.getPOS().startsWith("VB") || // verbs
						n.getPOS().startsWith("JJ") || // nominal modifiers
						n.getPOS().startsWith("CD") || // cardinal numbers
						n.getPOS().startsWith("FW")) && // and foreign words
				!n.getForm().equals("_"); // some punctuation marks such as quotes end up here as underscores
	}

	private static boolean hasVerb(Set<String> inSet)
	{
		return inSet.stream().anyMatch(ItemSetMining::isVerb);
	}

	private static boolean isVerb(String item)
	{
		if (item.startsWith("bn:") && item.endsWith("v"))
			return true;
		String[] verbs = {"VB", "VBD", "VBG", "VBN", "VBP", "VBZ"};
		return Arrays.stream(verbs).anyMatch(item::endsWith);
	}

	private static boolean hasNominalSense(Set<String> set)
	{
		return set.stream().anyMatch(ItemSetMining::isNominalSense);
	}

	private static boolean isNominalSense(String item)
	{
		return item.startsWith("bn:") && item.endsWith("n");
	}

	/**
	 * Given a tree and an itemset corresponding to a subset of its nodes, return a subtree spanning over all these
	 * nodes and satisfying certain linguistic constraints.
	 *
	 * @param g    the original tree
	 * @param inItemSet an itemset where each item corresponds to one or more nodes in the tree
	 * @return a subtree
	 */
	private ContentPattern getSubGraph(ContentGraph g, Set<String> inItemSet, double inPosition)
	{
		// Collect nodes which correspond to the items in the itemset
		Set<Entity> itemSetNodes = inItemSet.stream()
				.map(item -> g.vertexSet().stream()
						.filter(n -> n.getId().equals(item)))
				.flatMap(Function.identity())
				.collect(Collectors.toSet()); // removes duplicates

		// Find a common ancestor for them
		Optional<Entity> commonAncestor = getCommonAncestor(g, itemSetNodes);
		if (!commonAncestor.isPresent())
			throw new RuntimeException("No common ancestor found. Structure is not a tree?");

		// Expand set of nodes to include all nodes in their shortest paths to the root
		Set<Entity> nodes = itemSetNodes.stream()
				.map(n -> getPath(g, commonAncestor.get(), n))
				.flatMap(List::stream)
				.collect(Collectors.toSet());

		// Determine the subgraph that spans over all the nodes
		ContentPattern subGraph = new ContentPattern(g, commonAncestor.get());
		populateSubGraph(g, subGraph, nodes);

		return subGraph;
	}

	/**
	 * Find the closest common ancestor to a set of nodes in a rooted DAG.
	 * @param inNodes set of nodes
	 * @return common parent, if any
	 */
	private static Optional<Entity> getCommonAncestor(ContentGraph t, Set<Entity> inNodes)
	{
		Optional<Entity> commonAncestor = Optional.empty();
		Set<Entity> ancestors = new HashSet<>();
		ancestors.addAll(inNodes);
		while (!commonAncestor.isPresent())
		{
			commonAncestor = ancestors.stream()
					.filter(n -> t.inDegreeOf(n) == 0)
					.findFirst();

			if (!commonAncestor.isPresent())
			{
				ancestors = ancestors.stream()
						.filter(n -> t.inDegreeOf(n) > 0)
						.map(n -> t.incomingEdgesOf(n).iterator().next())
						.map(t::getEdgeSource)
						.collect(Collectors.toSet());
			}
		}

		return commonAncestor;
	}

	/**
	 * Given a node in a DAG and one of its descendants, return the shortest path from one to the other
	 */
	private static List<Entity> getPath(ContentGraph t, Entity a, Entity d)
	{
		GraphPath<Entity, Role> path = DijkstraShortestPath.findPathBetween(t, a, d);
		return path.getVertexList();
	}

	/**
	 * Creates a rooted subgraph of a DAG
	 *
	 * @param nodes set of nodes in the base graph which must be part of the subgraph
	 */
	private static void populateSubGraph(ContentGraph g, ContentPattern s, Set<Entity> nodes)
	{
		// todo fix this
//		s.outgoingEdgesOf(s.getRoot()).stream()
//				.map(s::getEdgeTarget)
//				.filter(n ->    (nodes.contains(n) ||
//								s.incomingEdgesOf(n).iterator().next().isCore() ||
//								isPlainModifier(s, n) ||
//								isNegation(n) ||
//								isNumber(n) ||
//								isPlainAdverb(s, n) ||
//								isName(n)) && !isVerbWithRelative(s, n))
//				.forEach(n -> {
//					s.addVertex(c);
//					Role e1 = s.incomingEdgesOf(n).iterator().next();
//					Role e2 = new Role(e1.getRole(), e1.isCore());
//					s.addEdge(inNewRoot, c, e2);
//					populateSubGraph(s, n, c, inCompulsoryNodes);
//				}); // recursive call
	}

	private static boolean isPlainModifier(ContentGraph g, Entity e)
	{
		if (g.inDegreeOf(e) == 0)
			return false;

		// Sample an anchor and an incoming relation
		AnnotatedWord a = g.getAnchors(e).get(0).getHead();
		Role edge = g.incomingEdgesOf(e).iterator().next();
		String role = edge.getRole();
		boolean isLeaf = g.outDegreeOf(e) == 0;
		String[] pos = {"DT", "NN", "RB", "JJ"};
		return role.equals("ATTR") && isLeaf && Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS()));
	}

	private static boolean isNegation(ContentGraph g, Entity e)
	{
		AnnotatedWord a = g.getAnchors(e).get(0).getHead();
		String[] forms = {"no", "not"};
		String[] pos = {"RB", "JJ"};
		return Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS())) &&
				Arrays.stream(forms).anyMatch(f -> f.equals(a.getForm()));
	}

	private static boolean isNumber(ContentGraph g, Entity e)
	{
		AnnotatedWord a = g.getAnchors(e).get(0).getHead();
		String[] pos = {"CD"};
		return Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS()));
	}

	private static boolean isPlainAdverb(ContentGraph g, Entity e)
	{
		AnnotatedWord a = g.getAnchors(e).get(0).getHead();
		boolean isLeaf = g.outDegreeOf(e) == 0;
		String[] pos = {"WRB"};
		return isLeaf && Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS()));
	}

//	private static boolean isInflectedVerb(WordAnnotation inAnn)
//	{
//		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
//		return Arrays.stream(inflectedVerbs).anyMatch(pos -> pos.equals(inAnn.getPOS()));
//	}

	private static boolean isVerbWithRelative(ContentGraph g, Entity e)
	{
		AnnotatedWord a = g.getAnchors(e).get(0).getHead();
		if (g.outDegreeOf(e) != 1)
			return false;
		Role dependent = g.outgoingEdgesOf(e).iterator().next();
		Entity d = g.getEdgeTarget(dependent);
		AnnotatedWord dAnn = g.getAnchors(d).get(0).getHead();
		return a.getPOS().startsWith("VB") &&
				g.outDegreeOf(e) == 1 &&
				dAnn.getPOS().equalsIgnoreCase("WDT");
	}

	private static boolean isName(ContentGraph g, Entity e)
	{
		if (g.inDegreeOf(e) == 0)
			return false;

		// Sample an anchor and an incoming relation
		AnnotatedWord a = g.getAnchors(e).get(0).getHead();
		Role edge = g.incomingEdgesOf(e).iterator().next();

		return edge.getRole().equals("NAME");
	}

}
