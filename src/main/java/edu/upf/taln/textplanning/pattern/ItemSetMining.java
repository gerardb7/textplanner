package edu.upf.taln.textplanning.pattern;

import ca.pfv.spmf.algorithms.frequentpatterns.fpgrowth.AlgoFPMax;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
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

	public Set<SemanticTree> getPatterns(List<SemanticTree> inContents)
	{
		// Collect sets of relevant annotations from trees
		List<Set<Node>> annSets = inContents.stream()
				.map(t -> t.vertexSet().stream()
						.filter(ItemSetMining::isItem)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// Now convert annotation sets to item sets by picking their labels
		List<Set<String>> itemSets = annSets.stream()
				.map(as -> as.stream()
						.map(Node::getEntity)
						.map(Entity::getLabel)
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
						.mapToDouble(SemanticTree::getPosition)
						.average().orElse(0.0))
				.collect(Collectors.toList());

		// For each maximal itemset, find the shortest tree that contains it
		List<SemanticTree> shortestTrees = filteredMaximalSets.stream()
				.map(m -> itemSets.stream()
						.filter(s -> s.containsAll(m)) // find sets that contain this maximal set
						.map(is -> inContents.get(itemSets.indexOf(is))) // find corresponding tree
						.min(Comparator.comparingInt(t -> t.getPreOrder().size()))) // min length
				.map(Optional::get)
				.collect(Collectors.toList()); // yes, we do want to preserve duplicate trees

		//noinspection ConstantConditions
		if (debug)
		{
			Map<String, String> refsAndForms = inContents.stream()
					.map(SemanticTree::vertexSet)
					.flatMap(Set::stream)
					.collect(Collectors.toMap(n -> n.getEntity().getLabel(), n -> n.getAnnotation().getForm(), (n1, n2) -> n1));

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
				.mapToObj(i -> getSubTree(shortestTrees.get(i), filteredMaximalSets.get(i), avgPositions.get(i)))
				.collect(Collectors.toSet()); // this where we remove duplicates
	}

	/**
	 * Decides whether an annotation can be considered an item based on the data it annotates
	 *
	 * @param n annotation to be evaluated
	 * @return true if it annotates entity
	 */
	private static boolean isItem(Node n)
	{
		return !n.getAnnotation().getLemma().startsWith("be_") &&
				(n.getAnnotation().getPOS().startsWith("NN") || // nouns
						n.getAnnotation().getPOS().startsWith("VB") || // verbs
						n.getAnnotation().getPOS().startsWith("JJ") || // nominal modifiers
						n.getAnnotation().getPOS().startsWith("CD") || // cardinal numbers
						n.getAnnotation().getPOS().startsWith("FW")) && // and foreign words
				!n.getAnnotation().getForm().equals("_"); // some punctuation marks such as quotes end up here as underscores
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
	 * @param t    the original tree
	 * @param inItemSet an itemset where each item corresponds to one or more nodes in the tree
	 * @return a subtree
	 */
	private SemanticTree getSubTree(SemanticTree t, Set<String> inItemSet, double inPosition)
	{
		// Collect nodes which correspond to the items in the itemset
		Set<Node> itemSetNodes = inItemSet.stream()
				.map(item -> t.vertexSet().stream()
						.filter(n -> n.getEntity().getLabel().equals(item)))
				.flatMap(Function.identity())
				.collect(Collectors.toSet()); // removes duplicates

		// Find a common ancestor for them
		Optional<Node> commonAncestor = getCommonAncestor(t, itemSetNodes);
		if (!commonAncestor.isPresent())
			throw new RuntimeException("No common ancestor found. Structure is not a tree?");

		// Expand set of node to include all nodes in their paths to the root
		Set<Node> nodes = itemSetNodes.stream()
				.map(n -> getPath(t, commonAncestor.get(), n))
				.flatMap(List::stream)
				.collect(Collectors.toSet());

		// Determine the subtree that spans over all the nodes
		SemanticTree subTree =
				new SemanticTree(commonAncestor.get(), inPosition);
		populateSubTree(t, commonAncestor.get(), subTree.getRoot(), nodes);

		return subTree;
	}

	/**
	 * Find the closest common ancestor to a set of nodes in a tree.
	 * @param inNodes set of nodes
	 * @return common parent, if any
	 */
	private static Optional<Node> getCommonAncestor(SemanticTree t, Set<Node> inNodes)
	{
		Optional<Node> commonAncestor = Optional.empty();
		Set<Node> ancestors = new HashSet<>();
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
	 * Given a node in a tree and one of its descendants, return the path from one to the other
	 *
	 * @param inAncestor       a node
	 * @param inDescendent a descendent of @inNode
	 * @return the sequence of nodes in the path
	 */
	private static List<Node> getPath(SemanticTree t, Node inAncestor, Node inDescendent)
	{
		Node currentNode = inDescendent;
		List<Node> path = new ArrayList<>();
		path.add(currentNode);

		// Find the (unique) path to the ancestor
		while (currentNode != inAncestor)
		{
			if (t.getRoot() == currentNode)
			{
				throw new RuntimeException("Cannot find a path between nodes " + inAncestor.getEntity() + " and " + inDescendent.getEntity());
			}
			currentNode = t.getEdgeSource(t.incomingEdgesOf(currentNode).iterator().next());
			path.add(currentNode);
		}

		return path;
	}

	/**
	 * Creates a subtree with root @inNewRoot from another tree with root @inOldRoot
	 *
	 * @param inOldRoot         the root of the supertree
	 * @param inNewRoot         the root of the subtree
	 * @param inCompulsoryNodes set of nodes in the supertree which must be part of the subtree
	 */
	private static void populateSubTree(SemanticTree t, Node inOldRoot, Node inNewRoot, Set<Node> inCompulsoryNodes)
	{
		t.outgoingEdgesOf(inOldRoot).stream()
				.map(t::getEdgeTarget)
				.filter(n ->    (inCompulsoryNodes.contains(n) ||
								t.incomingEdgesOf(n).iterator().next().isArg() ||
								isPlainModifier(t, n) ||
								isNegation(n) ||
								isNumber(n) ||
								isPlainAdverb(t, n) ||
								isName(n)) && !isVerbWithRelative(t, n))
				.forEach(n -> {
					Annotation a = n.getAnnotation();
					Entity e = n.getEntity();
					Node c = new Node(n.getEntity().getLabel(), e, a);
					t.addVertex(c);
					Edge e1 = t.incomingEdgesOf(n).iterator().next();
					Edge e2 = new Edge(e1.getRole(), e1.isArg());
					t.addEdge(inNewRoot, c, e2);
					populateSubTree(t, n, c, inCompulsoryNodes);
				}); // recursive call
	}

	private static boolean isPlainModifier(SemanticTree t, Node n)
	{
		Annotation a = n.getAnnotation();
		String role = a.getRole();
		boolean isLeaf = t.outDegreeOf(n) == 0;
		String[] pos = {"DT", "NN", "RB", "JJ"};
		return role.equals("ATTR") && isLeaf && Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS()));
	}

	private static boolean isNegation(Node n)
	{
		Annotation a = n.getAnnotation();
		String[] forms = {"no", "not"};
		String[] pos = {"RB", "JJ"};
		return Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS())) &&
				Arrays.stream(forms).anyMatch(f -> f.equals(a.getForm()));
	}

	private static boolean isNumber(Node n)
	{
		Annotation a = n.getAnnotation();
		String[] pos = {"CD"};
		return Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS()));
	}

	private static boolean isPlainAdverb(SemanticTree t, Node n)
	{
		Annotation a = n.getAnnotation();
		boolean isLeaf = t.outDegreeOf(n) == 0;
		String[] pos = {"WRB"};
		return isLeaf && Arrays.stream(pos).anyMatch(p -> p.equals(a.getPOS()));
	}

//	private static boolean isInflectedVerb(Annotation inAnn)
//	{
//		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
//		return Arrays.stream(inflectedVerbs).anyMatch(pos -> pos.equals(inAnn.getPOS()));
//	}

	private static boolean isVerbWithRelative(SemanticTree t, Node n)
	{
		if (t.outDegreeOf(n) != 1)
			return false;
		Edge dependent = t.outgoingEdgesOf(n).iterator().next();
		Annotation a = n.getAnnotation();
		Annotation c = t.getEdgeTarget(dependent).getAnnotation();
		return a.getPOS().startsWith("VB") &&
				t.outDegreeOf(n) == 1 &&
				c.getPOS().equalsIgnoreCase("WDT");
	}

	private static boolean isName(Node n)
	{
		Annotation a = n.getAnnotation();
		return a.getRole().equals("NAME");
	}

}
