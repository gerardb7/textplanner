package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.DocumentAccess;
import edu.upf.taln.textplanning.input.DocumentProvider;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pattern determination strategy for semantic trees
 */
public class PatternExtractorTrees implements PatternExtractor
{
	private final PatternSimilarity similarity;
	private final static Logger log = LoggerFactory.getLogger(PatternExtractorTrees.class);

	/**
	 * @param inSimilarity similarity metric used to find additional entities
	 */
	public PatternExtractorTrees(PatternSimilarity inSimilarity)
	{
		this.similarity = inSimilarity;
	}


	/**
	 * Returns all patterns from one or multiple documents.
	 *
	 * @param inDocs   provides set of documents
	 * @param inReader a reader capable of reading trees from each document
	 * @return a set of patterns constructed from the documents provided by @inDocs
	 */
	@Override
	public Set<SemanticTree> getPatterns(DocumentProvider inDocs, DocumentAccess inReader)
	{
		// Read semantic structures (DAGs) from documents
		Set<SemanticTree> trees = getTrees(inDocs.getAllDocuments(), inReader);

		// Determine patterns from ALL graphs
		log.info("Read " + trees.size() + " trees in " + inDocs.getAllDocuments().size() + " documents");
		return trees;
	}

	/**
	 * Returns a set of patterns from one or multiple documents related to one or multiple entities.
	 * First a set of patterns is returned that make direct reference to any of the entities.
	 * This initial set is extended recursively with additional patterns that entities in common with the
	 * patterns in the initial set.
	 *
	 * @param inReferences    references to entities for which a summary must be generated
	 * @param inDocs          provides documents for summarization
	 * @param inNumIterations number of iterations
	 * @return a set of patterns constructed from the documents provided by @inDocs and relevant to @inReferences
	 */
	public Set<SemanticTree> exploreContents(Set<String> inReferences, DocumentProvider inDocs, DocumentAccess inReader,
	                                         int inNumIterations)
	{
		Set<String> newReferences = new HashSet<>();
		newReferences.addAll(inReferences);
		Set<String> allReferences = new HashSet<>();
		Set<String> allDocuments = new HashSet<>();
		Map<SemanticTree, Optional<SemanticTree>> trees = new HashMap<>();

		for (int i = 0; i < inNumIterations; ++i)
		{
			log.info("\titeration " + i + ", new references " + newReferences);
			boolean continueSearching = true; // Keep searching until new patterns are produced
			do
			{
				// Get documents relevant to open references
				Set<String> newDocuments = inDocs.getDocuments(newReferences).stream()
						.filter(d -> !allDocuments.contains(d))
						.collect(Collectors.toSet());
				allDocuments.addAll(newDocuments);
				allReferences.addAll(newReferences);

				// Read new trees from documents
				getTrees(newDocuments, inReader)
						.forEach(t -> trees.put(t, Optional.empty()));

				// Update subtrees using the set of all open references
				updateSubtreesWithReferences(trees, allReferences);

				// Get new references from new patterns
				newReferences.clear();
				newReferences.addAll(trees.values().stream()
						.filter(Optional::isPresent)
						.map(Optional::get)
						.map(SemanticTree::getEntities)
						.flatMap(Set::stream)
						.filter(r -> !allReferences.contains(r)) // Filter out refs already seen
						.collect(Collectors.toSet()));

				// If no patterns found or ne references found, look for other references which are semantically similar to the input refs
				if (trees.values().stream().noneMatch(Optional::isPresent) || newReferences.isEmpty())
				{
					Optional<String> mostSimilarEntity = getMostSimilarReference(trees.keySet(), allReferences);
					if (mostSimilarEntity.isPresent())
					{
						newReferences.add(mostSimilarEntity.get()); // Keep reference
						log.info("\tadded similar reference " + mostSimilarEntity.get());
					}
					else
					{
						continueSearching = false;
					}
				}
				else
				{
					continueSearching = false;
				}
			}
			while (continueSearching);
		}

		log.info("Read " + trees.values().stream().filter(Optional::isPresent).count() + " patterns from " +
				trees.keySet().size() + " trees in " + allDocuments.size() + " documents");
		log.info("References used to explore contents: " + allReferences);

		return trees.values().stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
	}

	/**
	 * Reads linearly ordered n-ary trees from documents
	 *
	 * @param inDocuments documents to read trees from
	 * @param inReader    reader capable of reading trees from a doc
	 * @return set of trees
	 */
	private Set<SemanticTree> getTrees(Set<String> inDocuments, DocumentAccess inReader)
	{
		return inDocuments.stream()
				.map(inReader::readSemanticTrees) // get tree structures from doc
				.flatMap(List::stream)
				.collect(Collectors.toSet()); // Notice how trees are stored in a single set, no distinction by doc
	}

	private Optional<String> getMostSimilarReference(Set<SemanticTree> inTrees, Set<String> inOpenReferences)

	{
		return inTrees.stream()
				.map(SemanticTree::getEntities)
				.flatMap(Set::stream)
				.filter(r -> !inOpenReferences.contains(r))
				.map(e1 -> Pair.of(e1, inOpenReferences.stream()
						.mapToDouble(e2 -> similarity.getSimilarity(e1, e2))
						.average().orElse(0.0)))
				.max((a, b) -> a.getRight().compareTo(b.getRight()))
				.map(Pair::getLeft);
	}

	/**
	 * Determines patterns as subtrees of input trees containing specific entities. If a subtree already exists for
	 * a given tree, it is updated with nodes from the original tree relative to open references.
	 *
	 * @param inTrees map of input trees and their subtrees
	 * @param inOpenReferences set of references to entities
	 */
	private void updateSubtreesWithReferences(Map<SemanticTree, Optional<SemanticTree>> inTrees,
	                                          Set<String> inOpenReferences)
	{
		// Create subtrees for those input trees that don't have on yet
		inTrees.entrySet().stream()
				.forEach(p ->
				{
					// Collect nodes in original tree found in paths from the reference nodes to the root of the tree
					List<OrderedTree.Node<Pair<AnnotationInfo, String>>> nodesForNewTree = p.getKey().getPreOrder().stream()
							// Get nodes of tree matching refs in inOpenReferences
							.filter(n -> n.getData().getLeft().getReference() != null &&
									inOpenReferences.contains(n.getData().getLeft().getReference()))
//							.filter(n -> !p.getValue().isPresent()) || !p.getValue().get().getPreOrder().contains(n))
							// Get the path from each node to the root of the original tree
							.map(this::getPathToRoot)
							.flatMap(List::stream)
							.collect(Collectors.toList());

					// If nodes were found, create or update subtree
					if (!nodesForNewTree.isEmpty())
					{
						boolean subtreeExists = p.getValue().isPresent();
						SemanticTree newTree = subtreeExists ?
								p.getValue().get() :
								new SemanticTree(p.getKey().getRoot().getData().getLeft(), p.getKey().getPosition());

						// Keep only nodes which aren't already part of the subtree
						List<OrderedTree.Node<Pair<AnnotationInfo, String>>> preOrder = newTree.getPreOrder();
						List<OrderedTree.Node<Pair<AnnotationInfo, String>>> nodes = nodesForNewTree.stream()
								.filter(n1 -> preOrder.stream()
										.noneMatch(n2 -> n1.getData().equals(n2.getData())))
								.collect(Collectors.toList());

						if (!nodes.isEmpty())
						{
							populateTree(p.getKey().getRoot(), newTree.getRoot(), nodes, inOpenReferences);
							if (!subtreeExists)
							{
								p.setValue(Optional.of(newTree));
								log.info("\tcreated tree " + newTree);
							}
							else
							{
								log.info("\tupdated tree " + newTree);
							}
						}

					}
				});
	}

	private void populateTree(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode,
	                          OrderedTree.Node<Pair<AnnotationInfo, String>> outNode,
	                          List<OrderedTree.Node<Pair<AnnotationInfo, String>>> inCompulsoryNodes,
	                          Set<String> inOpenReferences)
	{
		// Check if node makes direct reference to one of the enitites in inOpenReferences
		String ref = inNode.getData().getLeft().getReference();
		boolean isReferenceNode = (ref != null && inOpenReferences.contains(ref));

		inNode.getChildren().forEach(n -> {
			if ((isReferenceNode || inCompulsoryNodes.contains(n) || isArgument(n) ||
					isNegation(n) || isNumber(n) || isPlainAdverb(n) || !isVerbWithRelative(n) || isName(n)) &&
					!outNode.getChildrenData().contains(n.getData()))
			{
				populateTree(n, outNode.addChild(n.getData()), inCompulsoryNodes, inOpenReferences); // recursive call
			}
		});
	}

	private List<OrderedTree.Node<Pair<AnnotationInfo, String>>> getPathToRoot(
			OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		OrderedTree.Node<Pair<AnnotationInfo, String>> currentNode = inNode;
		List<OrderedTree.Node<Pair<AnnotationInfo, String>>> path = new ArrayList<>();
		path.add(currentNode);

		// Find the (unique) path to the root
		while (!currentNode.isRoot())
		{
			currentNode = currentNode.getParent().get();
			path.add(currentNode);
		}

		return path;
	}

	private boolean isArgument(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		// Predicate to test if a node corresponds to a modifier
		String role = inNode.getData().getRight();
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}

	private boolean isNegation(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String[] forms = {"no", "not"};
		String[] pos = {"RB", "JJ"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS())) &&
				Arrays.stream(forms).anyMatch(f -> f.equals(inNode.getData().getLeft().getForm()));
	}

	private boolean isNumber(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String[] pos = {"CD"};
		return Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS()));
	}

	private boolean isPlainAdverb(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		String[] pos = {"WRB"};
		return inNode.isLeaf() && Arrays.stream(pos).anyMatch(n -> n.equals(inNode.getData().getLeft().getPOS()));
	}

	private boolean isVerbWithRelative(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		return inNode.getData().getLeft().getPOS().startsWith("VB") &&
				inNode.getChildrenData().size() == 1 &&
				inNode.getChildrenData().get(0).getLeft().getPOS().equalsIgnoreCase("WDT");
	}

	private boolean isName(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		return inNode.getData().getRight().equals("NAME");
	}
}
