package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.DocumentAccess;
import edu.upf.taln.textplanning.input.DocumentProvider;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.AbstractBaseGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements a pattern determination strategy from directed acyclic graphs (e.g. semantic structures)found in a set
 * of documents.
 * Immutable class.
 */
public final class PatternExtractorGraphs implements PatternExtractor
{
	private final PatternSimilarity similarity;
	private final static Logger log = LoggerFactory.getLogger(PatternExtractorGraphs.class);

	/**
	 * @param inSimilarity similarity metric used to find additional entities
	 */
	public PatternExtractorGraphs(PatternSimilarity inSimilarity)
	{
		this.similarity = inSimilarity;
	}


	/**
	 * Returns all patterns from one or multiple documents.
	 *
	 * @param inDocs   provides set of documents
	 * @param inReader a reader capable of returning a set of semantic structures from each document
	 * @return a set of patterns constructed from the documents provided by @inDocs
	 */
	@Override
	public Set<SemanticTree> getPatterns(DocumentProvider inDocs,
	                                     DocumentAccess inReader)
	{
		// Read semantic structures (DAGs) from documents
		Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> graphs =
				getGraphs(inDocs.getAllDocuments(), inReader);

		// Determine patterns from ALL graphs
		Set<SemanticTree> patterns = getBasePatterns(graphs);
		log.info("Read " + patterns.size() + " patterns from " + graphs.size() + " structures in " + inDocs.getAllDocuments().size() + " documents");
		return patterns;
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
	public Set<OrderedTree<Pair<AnnotationInfo, String>>> exploreContents(
			Set<String> inReferences, DocumentProvider inDocs, DocumentAccess inReader, int inNumIterations)
	{
		Set<String> newReferences = new HashSet<>();
		newReferences.addAll(inReferences);
		Set<String> allReferences = new HashSet<>();
		Set<String> allDocuments = new HashSet<>();
		Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> allGraphs = new HashSet<>();
		Set<OrderedTree<Pair<AnnotationInfo, String>>> allPatterns = new HashSet<>();

		for (int i = 0; i < inNumIterations; ++i)
		{
			boolean continueSearching = true; // Keep searching until new patterns are produced
			do
			{
				// Get documents relevant to open references
				Set<String> newDocuments = inDocs.getDocuments(newReferences).stream()
						.filter(d -> !allDocuments.contains(d))
						.collect(Collectors.toSet());
				allDocuments.addAll(newDocuments);
				allReferences.addAll(newReferences);

				// Read new semantic structures (DAGs) from documents
				allGraphs.addAll(getGraphs(newDocuments, inReader));

				// Determine new patterns for ALL references in ALL graphs
				Set<SemanticTree> newPatterns = getSubPatternsForReferences(allGraphs, allReferences);

				// Keep patterns found in this iteration
				allPatterns.addAll(newPatterns);

				// Get new references from new patterns
				newReferences.clear();
				newReferences.addAll(newPatterns.stream()
						.map(SemanticTree::getEntities)
						.flatMap(Set::stream)
						.filter(r -> !allReferences.contains(r)) // Filter out refs already seen
						.collect(Collectors.toSet()));

				// If no patterns found, look for other references in graphs which are semantically similar to the input references
				if (newPatterns.isEmpty())
				{
					Optional<String> mostSimilarEntity = getMostSimilarReference(allGraphs, allReferences);
					if (mostSimilarEntity.isPresent())
					{
						newReferences.add(mostSimilarEntity.get()); // Keep reference
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

		log.info("Read " + allPatterns.size() + " patterns from " + allGraphs.size() + " structures in " + allDocuments.size() + " documents");
		log.info("References used to explore contents: " + allReferences);

		return allPatterns;
	}


	/**
	 * Reads semantic structures (DAGs) from documents
	 *
	 * @param inDocuments documents to read from
	 * @param inReader    reader capable of reading semantic structures from a doc
	 * @return set of semantic structures
	 */
	private Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> getGraphs(
			Set<String> inDocuments, DocumentAccess inReader)
	{

		return inDocuments.stream()
				.map(inReader::readSemanticDAGs) // get semantic structures from doc
				.flatMap(List::stream)
				.collect(Collectors.toSet()); // Notice how graphs are stored in a single set, no distinction by doc
	}

	private Optional<String> getMostSimilarReference(
			Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> inGraphs, Set<String> inOpenReferences)
	{
		return inGraphs.stream()
				.map(AbstractBaseGraph::vertexSet)
				.flatMap(Set::stream)
				.map(AnnotationInfo::getReference)
				.filter(Objects::nonNull)
				.filter(r -> !inOpenReferences.contains(r))
				.map(e1 -> Pair.of(e1, inOpenReferences.stream()
						.mapToDouble(e2 -> similarity.getSimilarity(e1, e2))
						.average().orElse(0.0)))
				.max((a, b) -> a.getRight().compareTo(b.getRight()))
				.map(Pair::getLeft);
	}

	/**
	 * Returns patterns from semantic structures. A pattern is created for each 'base' root of the
	 * semantic structures (nodes marked as root excluding modifiers).
	 *
	 * @param inGraphs set of semantic structures (DAGs)
	 * @return set of patterns
	 */
	private Set<SemanticTree> getBasePatterns(Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> inGraphs)
	{
		return inGraphs.stream()
				.map(g -> g.vertexSet().stream()
						.filter(v -> g.inDegreeOf(v) == 0 && !isModifier(v, g))
						.map(r -> new SemanticTree(r, 0.0)) // @Todo implement position for graphs
						.map(t -> {
							populateTree(t.getRoot(), g);
							return t;
						})
						.collect(Collectors.toSet()))
				.flatMap(Set::stream)
				.collect(Collectors.toSet()); // All patterns go to same set, no distinction by graph nor doc
	}

	/**
	 * Returns patterns containing references to entities. A patterns is created for each 'semantic root' of a node
	 * referring to one of the entities.
	 *
	 * @param inGraphs         set of semantic structures (DAGs)
	 * @param inOpenReferences set of references to entities
	 * @return set of patterns (trees)
	 */
	private Set<SemanticTree> getSubPatternsForReferences(
			Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> inGraphs,
			Set<String> inOpenReferences)
	{
		return inGraphs.stream()
				.map(g -> {
					Set<AnnotationInfo> nodes = g.vertexSet().stream()
							.filter(v -> v.getReference() != null && inOpenReferences.contains(v.getReference()))
							.collect(Collectors.toSet());
					return determineSubPatterns(nodes, g);
				})
				.flatMap(Set::stream)
				.collect(Collectors.toSet()); // All patterns go to same set, no distinction by graph nor doc
	}

//	/**
//	 * Returns patterns containing references to entities. A pattern is created for each 'base' root of the
//	 * semantic structures (nodes marked as root excluding modifiers). The resulting set of patterns is reduced to
//	 * those containing a node matching any of the references.
//	 *
//	 * @param inGraphs         set of semantic structures (DAGs)
//	 * @param inOpenReferences set of references to entities
//	 * @return set of patterns (trees)
//	 */
//	private Set<OrderedTree<Pair<AnnotationInfo, String>>> getBasePatternsForReferences(
//			Set<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> inGraphs, Set<String> inOpenReferences)
//	{
//		return getBasePatterns(inGraphs).stream()
//				.filter(m -> !Collections.disjoint(SemanticTreeUtils.getEntities(m), inOpenReferences))
//				.collect(Collectors.toSet());
//	}

	/**
	 * Given a set of nodes in a semantic structure, find their 'semantic roots', and for each root, create a patterns.
	 * Patterns are linearly ordered trees resulting from selecting the subgraph containing all nodes reachable
	 * from a given root, and transforming the resulting subDAG into a tree by placing modifiers as children of
	 * their arguments.
	 *
	 * @param inNodes a set of nodes in a semantic structure
	 * @param inGraph semantic structure (DAG)
	 * @return set of patterns (trees)
	 */
	private Set<SemanticTree> determineSubPatterns(Set<AnnotationInfo> inNodes,
	                                          DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge> inGraph)
	{
		Set<AnnotationInfo> semanticRoots = inNodes.stream()
				.map(n -> findSemanticRoots(n, inGraph))
				.flatMap(Set::stream)
				.distinct()
				.collect(Collectors.toSet());

		return semanticRoots.stream()
				.map(r -> {
					SemanticTree tree = new SemanticTree(r, 0.0); // @Todo sentence position!
					populateTree(tree.getRoot(), inGraph);
					return tree;
				})
				.collect(Collectors.toSet());
	}

	/**
	 * Given a node in a semantic structure, find the closest governing nodes which correspond to an inflected
	 * verb. If the node has multiple governors, then multiple semantic roots may be returned. If no inflected verbs
	 * are found in the path from the node to its roots in the graph, then no semantic roots are returned.
	 *
	 * @param inAnn   node for which semantic roots are searched
	 * @param inGraph semantic structure containing the node (DAG)
	 * @return the semantic roots
	 */
	private Set<AnnotationInfo> findSemanticRoots(AnnotationInfo inAnn,
	                                              DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge> inGraph)
	{
		if (isInflectedVerb(inAnn))
		{
			return Collections.singleton(inAnn); // base case: inflected verb
		}
		else
		{
			return inGraph.incomingEdgesOf(inAnn).stream()
					.map(inGraph::getEdgeSource)
					.map(v -> findSemanticRoots(v, inGraph)) // recursive call
					.flatMap(Set::stream)
					.collect(Collectors.toSet());
		}
	}

	private void populateTree(OrderedTree.Node<Pair<AnnotationInfo, String>> inRoot,
	                          DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge> inGraph)
	{
		AnnotationInfo node = inRoot.getData().getLeft();

		// Now add dependents in the graph as children
		if (inGraph.outDegreeOf(node) > 0)
		{
			inGraph.outgoingEdgesOf(node).forEach(d ->
					inRoot.addChild((Pair.of(inGraph.getEdgeTarget(d), d.getLabel()))));
			inRoot.getChildren().forEach(c -> populateTree(c, inGraph)); // recursive call
		}

		// Add governors in the graph corresponding to modifiers as children of the current node in the tree
		addModifiersAsChildren(inRoot, inGraph);
	}

	private void addModifiersAsChildren(OrderedTree.Node<Pair<AnnotationInfo, String>> inRoot,
	                                    DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge> inGraph)
	{


		// Add governors in the graph corresponding to modifiers as children of the current node in the tree
		inGraph.incomingEdgesOf(inRoot.getData().getLeft()).stream()
				.map(inGraph::getEdgeSource)
				.filter(v -> isModifier(v, inGraph))
				.map(v -> inRoot.addChild(Pair.of(v, "Attribute")))
				.forEach(n -> addModifiersAsChildren(n, inGraph)); // Add modifiers of modifiers recursively
	}

	private boolean isInflectedVerb(AnnotationInfo inNode)
	{
//		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
//		//String[] relatives = {"WDT", "WP", "PRP"};
//		return Arrays.stream(inflectedVerbs).anyMatch(pos -> pos.equals(inNode.getPOS()));
		return inNode.getPOS().startsWith("VB");
	}

	private boolean isModifier(AnnotationInfo inNode, DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge> inGraph)
	{
		// Predicate to test if a node corresponds to a modifier
		String[] modifiers = {"RB", "RBR", "RBS", "JJ", "JJR", "JJS", "DT", "PDT", "WDT", "WRB"};
		return inGraph.outDegreeOf(inNode) == 1 &&
				inGraph.outgoingEdgesOf(inNode).iterator().next().getLabel().startsWith("Argument") &&
				Arrays.stream(modifiers).anyMatch(pos -> pos.equals(inNode.getPOS()));
	}
}
