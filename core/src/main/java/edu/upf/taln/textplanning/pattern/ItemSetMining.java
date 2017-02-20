package edu.upf.taln.textplanning.pattern;

import ca.pfv.spmf.algorithms.frequentpatterns.fpgrowth.AlgoFPMax;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A pattern determination strategy based in mining maximal sets of annotations in the input documents.
 * A pattern is determined from each itemset consisting of a subtree of the analysis tree of the shortest sentence
 * supporting the itemset.
 */
public class ItemSetMining implements PatternExtractor
{
	private final boolean debug = true;
	private final static Logger log = LoggerFactory.getLogger(ItemSetMining.class);

	@Override
	public Set<SemanticTree> getPatterns(List<SemanticTree> inContents)
	{
		List<List<OrderedTree.Node<Pair<AnnotationInfo, String>>>> treeNodes = inContents.stream()
				.map(OrderedTree::getPreOrder)
				.collect(Collectors.toList());

		// Convert trees to itemsets
		List<Set<String>> itemSets = treeNodes.stream()
				.map(p -> p.stream()
						.map(OrderedTree.Node::getData) // map nodes to items
						.map(Pair::getLeft)
						.filter(ItemSetMining::isItem)  // filter annotations
						.collect(Collectors.toSet()))   // results in no duplicates
				.filter(ItemSetMining::hasVerb)         // sets must contain at least one verb
				.filter(ItemSetMining::hasNominalSense) // sets must contain at least a nominal sense
				.map(s -> s.stream()
							.map(ItemSetMining::getItem)
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

		// Filter maximal itemsets
		List<Itemset> filteredMaximalSets = maximalSets.getLevels().stream()
				.flatMap(List::stream) // treat all levels as the same
				.filter(s -> s.getItems().length > 1) // only interested in sets of size 2 or greater
				.collect(Collectors.toList());

		// Decode itemsets returned by algorithm by converting indexes to actual items
		List<Set<String>> decodedMaximalSets = filteredMaximalSets.stream()
				.map(Itemset::getItems)
				.map(s -> Arrays.stream(s)
						.mapToObj(alphabet::get)
						.collect(Collectors.toSet()))
				.collect(Collectors.toList());

		if (debug)
		{
			IntStream.range(0, decodedMaximalSets.size())
					.forEach(i ->
					{
						String s = decodedMaximalSets.get(i).stream()
								.map(item -> item + "-" + treeNodes.stream()
										.flatMap(List::stream)
										.map(OrderedTree.Node::getData)
										.map(Pair::getLeft)
										.filter(a -> ItemSetMining.getItem(a).equals(item))
										.map(AnnotationInfo::getForm)
										.findFirst().orElse(""))
								.collect(Collectors.joining(", "));
						log.info("itemset: [" + s + "] support=" + filteredMaximalSets.get(i).support);
					});
		}

		// Work out the average position of each maximal set from the position of each of its supporting patterns
		List<Double> avgPositions = decodedMaximalSets.stream()
				.map(m -> itemSets.stream()
						.filter(s -> s.containsAll(m))
						.map(s -> inContents.get(itemSets.indexOf(s)))
						.mapToDouble(SemanticTree::getPosition)
						.average().orElse(0.0))
				.collect(Collectors.toList());

		// For each maximal itemset, find the shortest tree that contains it
		List<Integer> shortestTrees = decodedMaximalSets.stream()
				.map(m -> itemSets.stream()
						.filter(s -> s.containsAll(m)) // find trees that contain this maximal set
						.map(s -> treeNodes.get(itemSets.indexOf(s))) // and get the shortest one
						.min((t1, t2) -> Integer.compare(t1.size(), t2.size())))
				.map(Optional::get)
				.map(treeNodes::indexOf)
				.collect(Collectors.toList()); // yes, we actually want to preserve duplicate trees

		// Find subtrees for each shortest tree and maximal set
		return IntStream.range(0, decodedMaximalSets.size())
				.mapToObj(i -> getSubTree(treeNodes.get(shortestTrees.get(i)), decodedMaximalSets.get(i), avgPositions.get(i)))
				.collect(Collectors.toSet()); // this where we remove duplicates
	}

	/**
	 * Decides whether an annotation can be considered an item based on its POS
	 *
	 * @param inAnn annotation to be evaluated
	 * @return true if it is an entity
	 */
	private static boolean isItem(AnnotationInfo inAnn)
	{
		return !inAnn.getLemma().startsWith("be_") &&
				(inAnn.getPOS().startsWith("NN") || // nouns
				inAnn.getPOS().startsWith("VB") || // verbs
				inAnn.getPOS().startsWith("JJ") || // nominal modifiers
				inAnn.getPOS().startsWith("CD") || // cardinal numbers
				inAnn.getPOS().startsWith("FW")) || // and foreign words
				inAnn.getForm().equals('_'); // some punctuation marks such as quotes end up here as underscores
	}

	/**
	 * Gets the representation of an annotation as an item: either its sense, or its lemma if no sense was
	 * associated.
	 *
	 * @param inAnn an annotation
	 * @return item
	 */
	private static String getItem(AnnotationInfo inAnn)
	{
		if (inAnn.getReference() != null)
			return inAnn.getReference();
		else
			return inAnn.getLemma();
	}

	private static boolean hasVerb(Set<AnnotationInfo> inSet)
	{
		return inSet.stream().filter(ItemSetMining::isInflectedVerb).findFirst().isPresent();
	}

	private static boolean hasNominalSense(Set<AnnotationInfo> inSet)
	{
		return inSet.stream().filter(ItemSetMining::isNominalSense).findFirst().isPresent();
	}

	/**
	 * Given a tree and an itemset corresponding to a subset of its nodes, return a subtree spanning over all these
	 * nodes and satisfying certain linguistic constraints.
	 *
	 * @param inTree    the original tree
	 * @param inItemSet an itemset where each item corresponds to one or more nodes in the tree
	 * @return a subtree
	 */
	private SemanticTree getSubTree(List<OrderedTree.Node<Pair<AnnotationInfo, String>>> inTree, Set<String> inItemSet,
	                                double inPosition)
	{
		// Collect nodes which correspond to the items in the itemset
		Set<OrderedTree.Node<Pair<AnnotationInfo, String>>> itemSetNodes = inItemSet.stream()
				.map(i -> inTree.stream()
						.filter(n -> ItemSetMining.getItem(n.getData().getLeft()).equals(i)))
				.flatMap(Function.identity())
				.collect(Collectors.toSet()); // removes duplicates

		// Find a common ancestor for them
		Optional<OrderedTree.Node<Pair<AnnotationInfo, String>>> commonAncestor = getCommonAncestor(itemSetNodes);
		if (!commonAncestor.isPresent())
			throw new RuntimeException("No common ancestor found. Structure is not a tree?");

		// Expand set of node to include all nodes in their paths to the root
		Set<OrderedTree.Node<Pair<AnnotationInfo, String>>> nodes = itemSetNodes.stream()
				.map(n -> getPath(commonAncestor.get(), n))
				.flatMap(List::stream)
				.collect(Collectors.toSet());

		// Determine the subtree that spans over all the nodes
		SemanticTree subTree =
				new SemanticTree(commonAncestor.get().getData().getLeft(), inPosition);
		populateSubTree(commonAncestor.get(), subTree.getRoot(), nodes);

		return subTree;
	}

	/**
	 * Find the closest common ancestor to a set of nodes in a tree.
	 * @param inNodes set of nodes
	 * @return common parent, if any
	 */
	private static Optional<OrderedTree.Node<Pair<AnnotationInfo, String>>> getCommonAncestor(Set<OrderedTree.Node<Pair<AnnotationInfo, String>>> inNodes)
	{
		Optional<OrderedTree.Node<Pair<AnnotationInfo, String>>> commonAncestor = Optional.empty();
		Set<OrderedTree.Node<Pair<AnnotationInfo, String>>> ancestors = new HashSet<>();
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
	private static List<OrderedTree.Node<Pair<AnnotationInfo, String>>> getPath(
			OrderedTree.Node<Pair<AnnotationInfo, String>> inAncestor,
			OrderedTree.Node<Pair<AnnotationInfo, String>> inDescendent)
	{
		OrderedTree.Node<Pair<AnnotationInfo, String>> currentNode = inDescendent;
		List<OrderedTree.Node<Pair<AnnotationInfo, String>>> path = new ArrayList<>();
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
	private static void populateSubTree(OrderedTree.Node<Pair<AnnotationInfo, String>> inOldRoot,
	                             OrderedTree.Node<Pair<AnnotationInfo, String>> inNewRoot,
	                             Set<OrderedTree.Node<Pair<AnnotationInfo, String>>> inCompulsoryNodes)
	{
		inOldRoot.getChildren().stream()
				.filter(n ->    (inCompulsoryNodes.contains(n) ||
								isArgument(n) ||
								isPlainModifier(n) ||
								isNegation(n) ||
								isNumber(n) ||
								isPlainAdverb(n) ||
								isName(n)) && !isVerbWithRelative(n))
				.forEach(n -> populateSubTree(n, inNewRoot.addChild(n.getData()), inCompulsoryNodes)); // recursive call
	}

	private static boolean isArgument(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String role = inNode.getData().getRight();
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}

	private static boolean isNominalSense(AnnotationInfo inAnn)
	{
		return inAnn.getPOS().startsWith("NN") && inAnn.getReference() != null && inAnn.getReference().endsWith("n");
	}

	private static boolean isPlainModifier(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String role = inNode.getData().getRight();
		String[] pos = {"DT", "NN", "RB", "JJ"};
		return role.equals("ATTR") && inNode.isLeaf() && Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS()));
	}

	private static boolean isNegation(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String[] forms = {"no", "not"};
		String[] pos = {"RB", "JJ"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS())) &&
				Arrays.stream(forms).anyMatch(f -> f.equals(inNode.getData().getLeft().getForm()));
	}

	private static boolean isNumber(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String[] pos = {"CD"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS()));
	}

	private static boolean isPlainAdverb(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String[] pos = {"WRB"};
		return inNode.isLeaf() && Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS()));
	}

	private static boolean isInflectedVerb(AnnotationInfo inAnn)
	{
		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
		return Arrays.stream(inflectedVerbs).anyMatch(pos -> pos.equals(inAnn.getPOS()));
	}

	private static boolean isVerbWithRelative(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		return inNode.getData().getLeft().getPOS().startsWith("VB") &&
				inNode.getChildrenData().size() == 1 &&
				inNode.getChildrenData().get(0).getLeft().getPOS().equalsIgnoreCase("WDT");
	}

	private static boolean isName(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		return inNode.getData().getRight().equals("NAME");
	}

}
