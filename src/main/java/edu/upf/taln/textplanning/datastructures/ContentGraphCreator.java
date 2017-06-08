package edu.upf.taln.textplanning.datastructures;

import org.jgrapht.alg.CycleDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Produces content graphs from sets of semantic trees
 */
public class ContentGraphCreator
{
	private final static Logger log = LoggerFactory.getLogger(ContentGraphCreator.class);

	/**
	 * Creates a pattern extraction graph
	 * @param trees list of annotated trees
	 * @return a semantic graph
	 */
	public static SemanticGraph createContentGraph(List<SemanticTree> trees)
	{
		// Create empty graph
		SemanticGraph graph = new SemanticGraph(SemanticGraph.Edge.class);
		Map<String, Set<SemanticGraph.Node>> ids = new HashMap<>();

		// Iterate triples in each tree and populate graph from them
		trees.forEach(t -> {
			for (SemanticGraph.Edge e : t.edgeSet())
			{
				SemanticGraph.Node governor = t.getEdgeSource(e);
				SemanticGraph.Node dependent = t.getEdgeTarget(e);

				// Add governing tree node to graph
				SemanticGraph.Node govNode = createGraphNode(t, governor);
				graph.addVertex(govNode); // does nothing if node existed
				ids.computeIfAbsent(govNode.getId(), v -> new HashSet<>()).add(governor);

				// Add dependent tree node to graph
				SemanticGraph.Node depNode = createGraphNode(t, dependent);
				graph.addVertex(depNode); // does nothing if node existed
				ids.computeIfAbsent(depNode.getId(), v -> new HashSet<>()).add(dependent);

				// Add edge
				if (!govNode.getId().equals(depNode.getId()))
				{
					try
					{
						SemanticGraph.Edge e2 = new SemanticGraph.Edge(e.getRole(), e.isArg());
						graph.addEdge(govNode, depNode, e2);
					}
					catch (Exception ex)
					{
						throw new RuntimeException("Failed to add edge between " + govNode.getId() + " and " + depNode.getId() + ": " + ex);
					}
				}
				// Ignore loops.
				// To see how loops may occur in semantic trees, consider "West Nickel Mines Amish School
				// shooting", where "West Nickel Mines Amish School" and "Amish School shooting" are assigned
				// the same synset (same id and therefore same node) and are linked through a NAME relation.
			}
		});

		CycleDetector<SemanticGraph.Node, SemanticGraph.Edge> detector = new CycleDetector<>(graph);
		if (detector.detectCycles())
		{
			log.warn("Content graph has cycles: " + detector.findCycles());
		}
//
//		try
//		{
//			DOTExporter<SemanticGraph.Node, SemanticGraph.Edge> exporter = new DOTExporter<>(
//					new IntegerComponentNameProvider<>(),
//					new StringComponentNameProvider<>(),
//					new StringComponentNameProvider<>());
//			File temp = File.createTempFile("semgraph", ".dot");
//			exporter.exportGraph(graph, new FileWriter(temp));
//
//			File conllTemp = File.createTempFile("semgraph", ".conll");
//			ConLLAcces conll = new ConLLAcces();
//			String graphConll = conll.writeGraphs(Collections.singleton(graph));
//			FileUtils.writeStringToFile(conllTemp, graphConll, Charset.forName("UTF-16"));
//		}
//		catch (Exception e)
//		{
//			throw new RuntimeException(e);
//		}

		return graph;
	}

	/**
	 * Creates a graph node from a tree node.
	 * A single graph node is created for all non-predicative tree nodes with the same label.
	 * A single graph node is created for all predicates with same label and same dependent labels.
	 * The graph node is assigned a weight.
	 * @param t a tree
	 * @param n a node in the tree
	 *
	 * @return a graph node
	 */
	private static SemanticGraph.Node createGraphNode(SemanticTree t, SemanticGraph.Node n)
	{
		String id = n.getEntity().getEntityLabel();
		if (t.isPredicate(n) || id.equals("_")) // if a predicate use predicate and argument labels as id
			id = predicateToString(t, n);

		return new SemanticGraph.Node(id, n.getEntity());
	}

	private static String predicateToString(SemanticTree t, SemanticGraph.Node p)
	{

		List<String> dependents = new ArrayList<>();
		for (SemanticGraph.Edge e : t.outgoingEdgesOf(p))
		{
			dependents.add(e.getRole() + "-" + t.getEdgeTarget(e).getEntity().getEntityLabel());
		}

		StringBuilder str = new StringBuilder(p.getEntity().getEntityLabel());
		dependents.stream()
				.sorted(Comparator.naturalOrder())
				.forEach(s -> str.append("_").append(s));

		return str.toString();
	}
}
