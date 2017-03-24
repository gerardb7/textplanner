package edu.upf.taln.textplanning.pattern;

import ca.pfv.spmf.algorithms.frequentpatterns.fpgrowth.AlgoFPMax;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Select trees that cover frequent patterns of annotations in a collection of semantic trees
 * Patterns are found using an algorithm for mining maximal sets (of annotations).
 * For each pattern found, the smallest input tree where the pattern occurs is returned.
 */
public class ItemSetMining
{
	private final boolean debug = true;
	private final static Logger log = LoggerFactory.getLogger(ItemSetMining.class);

	public Set<AnnotatedTree> getPatterns(List<AnnotatedTree> inContents)
	{
		// Collect sets of relevant annotations from trees
		List<Set<AnnotatedEntity>> annSets = inContents.stream()
				.map(t -> t.getPreOrder().stream()
						.map(OrderedTree.Node::getData)
						.filter(ItemSetMining::isItem)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		// Now convert annotation sets to item sets by picking their labels
		List<Set<String>> itemSets = annSets.stream()
				.map(as -> as.stream()
						.map(AnnotatedEntity::getEntityLabel)
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
						.mapToDouble(AnnotatedTree::getPosition)
						.average().orElse(0.0))
				.collect(Collectors.toList());

		// For each maximal itemset, find the shortest tree that contains it
		List<AnnotatedTree> shortestTrees = filteredMaximalSets.stream()
				.map(m -> itemSets.stream()
						.filter(s -> s.containsAll(m)) // find sets that contain this maximal set
						.map(is -> inContents.get(itemSets.indexOf(is))) // find corresponding tree
						.min((t1, t2) -> Integer.compare(t1.getPreOrder().size(), t2.getPreOrder().size()))) // min length
				.map(Optional::get)
				.collect(Collectors.toList()); // yes, we do want to preserve duplicate trees

		if (debug)
		{
			Map<String, String> refsAndForms = inContents.stream()
					.map(AnnotatedTree::getPreOrder)
					.flatMap(List::stream)
					.map(OrderedTree.Node::getData)
					.map(AnnotatedEntity::getAnnotation)
					.collect(Collectors.toMap(a -> a.getSense() != null ? a.getSense() : "none", Annotation::getForm, (r1, r2) -> r1));

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
	 * @param inAnn annotation to be evaluated
	 * @return true if it annotates entity
	 */
	private static boolean isItem(AnnotatedEntity inAnn)
	{
		return !inAnn.getAnnotation().getLemma().startsWith("be_") &&
				(inAnn.getAnnotation().getPOS().startsWith("NN") || // nouns
						inAnn.getAnnotation().getPOS().startsWith("VB") || // verbs
						inAnn.getAnnotation().getPOS().startsWith("JJ") || // nominal modifiers
						inAnn.getAnnotation().getPOS().startsWith("CD") || // cardinal numbers
						inAnn.getAnnotation().getPOS().startsWith("FW")) && // and foreign words
				!inAnn.getAnnotation().getForm().equals("_"); // some punctuation marks such as quotes end up here as underscores
	}

	private static boolean hasVerb(Set<String> inSet)
	{
		return inSet.stream().filter(ItemSetMining::isVerb).findFirst().isPresent();
	}

	private static boolean isVerb(String inItem)
	{
		if (inItem.endsWith("v"))
			return true;
		String[] verbs = {"VB", "VBD", "VBG", "VBN", "VBP", "VBZ"};
		return Arrays.stream(verbs).anyMatch(inItem::endsWith);
	}

	private static boolean hasNominalSense(Set<String> inSet)
	{
		return inSet.stream().filter(ItemSetMining::isNominalSense).findFirst().isPresent();
	}

	private static boolean isNominalSense(String inItem)
	{
		return inItem.endsWith("n");
	}

	/**
	 * Given a tree and an itemset corresponding to a subset of its nodes, return a subtree spanning over all these
	 * nodes and satisfying certain linguistic constraints.
	 *
	 * @param inTree    the original tree
	 * @param inItemSet an itemset where each item corresponds to one or more nodes in the tree
	 * @return a subtree
	 */
	private AnnotatedTree getSubTree(AnnotatedTree inTree, Set<String> inItemSet, double inPosition)
	{
		// Collect nodes which correspond to the items in the itemset
		Set<OrderedTree.Node<AnnotatedEntity>> itemSetNodes = inItemSet.stream()
				.map(item -> inTree.getPreOrder().stream()
						.filter(n -> n.getData().getEntityLabel().equals(item)))
				.flatMap(Function.identity())
				.collect(Collectors.toSet()); // removes duplicates

		// Find a common ancestor for them
		Optional<OrderedTree.Node<AnnotatedEntity>> commonAncestor = getCommonAncestor(itemSetNodes);
		if (!commonAncestor.isPresent())
			throw new RuntimeException("No common ancestor found. Structure is not a tree?");

		// Expand set of node to include all nodes in their paths to the root
		Set<OrderedTree.Node<AnnotatedEntity>> nodes = itemSetNodes.stream()
				.map(n -> getPath(commonAncestor.get(), n))
				.flatMap(List::stream)
				.collect(Collectors.toSet());

		// Determine the subtree that spans over all the nodes
		AnnotatedTree subTree =
				new AnnotatedTree(commonAncestor.get().getData(), inPosition);
		populateSubTree(commonAncestor.get(), subTree.getRoot(), nodes);

		return subTree;
	}

	/**
	 * Find the closest common ancestor to a set of nodes in a tree.
	 * @param inNodes set of nodes
	 * @return common parent, if any
	 */
	private static Optional<OrderedTree.Node<AnnotatedEntity>> getCommonAncestor(Set<OrderedTree.Node<AnnotatedEntity>> inNodes)
	{
		Optional<OrderedTree.Node<AnnotatedEntity>> commonAncestor = Optional.empty();
		Set<OrderedTree.Node<AnnotatedEntity>> ancestors = new HashSet<>();
		ancestors.addAll(inNodes);
		while (!commonAncestor.isPresent())
		{
			commonAncestor = ancestors.stream()
					.filter(OrderedTree.Node::isRoot)
					.findFirst();

			if (!commonAncestor.isPresent())
			{
				ancestors = ancestors.stream()
						.map(OrderedTree.Node::getParent)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.collect(Collectors.toSet());
			}
		}

		return commonAncestor;
	}

	/**
	 * Given a node in a tree and one of its descendents, return the path from one to the other
	 *
	 * @param inAncestor       a node
	 * @param inDescendent a descendent of @inNode
	 * @return the sequence of nodes in the path
	 */
	private static List<OrderedTree.Node<AnnotatedEntity>> getPath(
			OrderedTree.Node<AnnotatedEntity> inAncestor,
			OrderedTree.Node<AnnotatedEntity> inDescendent)
	{
		OrderedTree.Node<AnnotatedEntity> currentNode = inDescendent;
		List<OrderedTree.Node<AnnotatedEntity>> path = new ArrayList<>();
		path.add(currentNode);

		// Find the (unique) path to the ancestor
		while (currentNode != inAncestor)
		{
			if (currentNode.isRoot())
			{
				throw new RuntimeException("Cannot find a path between nodes " + inAncestor.getData() + " and " + inDescendent.getData());
			}
			currentNode = currentNode.getParent().get();
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
	private static void populateSubTree(OrderedTree.Node<AnnotatedEntity> inOldRoot,
	                             OrderedTree.Node<AnnotatedEntity> inNewRoot,
	                             Set<OrderedTree.Node<AnnotatedEntity>> inCompulsoryNodes)
	{
		inOldRoot.getChildren().stream()
				.filter(n ->    (inCompulsoryNodes.contains(n) ||
								AnnotatedTree.isArgument(n) ||
								isPlainModifier(n) ||
								isNegation(n) ||
								isNumber(n) ||
								isPlainAdverb(n) ||
								isName(n)) && !isVerbWithRelative(n))
				.forEach(n -> populateSubTree(n, inNewRoot.addChild(n.getData()), inCompulsoryNodes)); // recursive call
	}

	private static boolean isPlainModifier(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		String role = inNode.getData().getAnnotation().getRole();
		String[] pos = {"DT", "NN", "RB", "JJ"};
		return role.equals("ATTR") && inNode.isLeaf() && Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getAnnotation().getPOS()));
	}

	private static boolean isNegation(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		String[] forms = {"no", "not"};
		String[] pos = {"RB", "JJ"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getAnnotation().getPOS())) &&
				Arrays.stream(forms).anyMatch(f -> f.equals(inNode.getData().getAnnotation().getForm()));
	}

	private static boolean isNumber(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		String[] pos = {"CD"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getAnnotation().getPOS()));
	}

	private static boolean isPlainAdverb(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		String[] pos = {"WRB"};
		return inNode.isLeaf() && Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getAnnotation().getPOS()));
	}

//	private static boolean isInflectedVerb(Annotation inAnn)
//	{
//		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
//		return Arrays.stream(inflectedVerbs).anyMatch(pos -> pos.equals(inAnn.getPOS()));
//	}

	private static boolean isVerbWithRelative(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		return inNode.getData().getAnnotation().getPOS().startsWith("VB") &&
				inNode.getChildrenData().size() == 1 &&
				inNode.getChildrenData().get(0).getAnnotation().getPOS().equalsIgnoreCase("WDT");
	}

	private static boolean isName(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		return inNode.getData().getAnnotation().getRole().equals("NAME");
	}

}
