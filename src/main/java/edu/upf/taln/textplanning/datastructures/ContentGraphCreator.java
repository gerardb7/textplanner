package edu.upf.taln.textplanning.datastructures;

import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import org.jgrapht.alg.CycleDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Produces content graphs from sets of semantic trees
 */
public class ContentGraphCreator
{
	private final static Logger log = LoggerFactory.getLogger(ContentGraphCreator.class);

	/**
	 * Creates a pattern extraction graph
	 * @param structures list of structures, e.g. relations in a KB, parses extracted from text
	 * @return a semantic graph
	 */
	public static SemanticGraph createContentGraph(Set<SemanticGraph> structures)
	{
		// Create empty graph
		SemanticGraph graph = new SemanticGraph(SemanticGraph.Edge.class);

		// Iterate triples in each input structure and populate graph
		structures.forEach(s -> {
			for (SemanticGraph.Edge e : s.edgeSet())
			{
				Node governor = s.getEdgeSource(e);
				Node dependent = s.getEdgeTarget(e);

				// Add governing tree node to graph
				Node govNode = createGraphNode(s, governor);
				graph.addVertex(govNode); // does nothing if a node with same id was already added

				// Add dependent tree node to graph
				Node depNode = createGraphNode(s, dependent);
				graph.addVertex(depNode); // does nothing if a node with same id was already added

				// Ignore loops.
				// To see how loops may occur in semantic trees, consider "West Nickel Mines Amish School
				// shooting", where "West Nickel Mines Amish School" and "Amish School shooting" are assigned
				// the same synset (same id and therefore same node) and are linked through a NAME relation.
				if (!govNode.getId().equals(depNode.getId()))
				{
					try
					{
						// Add edge
						SemanticGraph.Edge e2 = new SemanticGraph.Edge(e.getRole(), e.isArg());
						graph.addEdge(govNode, depNode, e2);
					}
					catch (Exception ex)
					{
						throw new RuntimeException("Failed to add edge between " + govNode.getId() + " and " + depNode.getId() + ": " + ex);
					}
				}
			}
		});

		CycleDetector<Node, SemanticGraph.Edge> detector = new CycleDetector<>(graph);
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
	 * Creates a content graph node from a node in an input structure.
	 * A single graph node is created for all structure nodes corresponding to the same entity.
	 * A single graph node is created for all relation-nodes in the structures that share exactly the same arguments.
	 * Each graph node is assigned a weight.
	 * @param g a structure
	 * @param n a node in g
	 *
	 * @return a graph node
	 */
	private static Node createGraphNode(SemanticGraph g, Node n)
	{
		String id = getId(n);

		// todo check if underscores have already been filtered
		if (g.isPredicate(n) || id.equals("_")) // if a predicate use predicate and argument labels as id
			id = predicateToString(g, n);

		return new Node(id, n.getEntity(), n.getAnnotation());
	}

	private static String getId(Node n)
	{
		return n.getEntity().getLabel();
	}

	private static String predicateToString(SemanticGraph g, Node p)
	{
		List<String> dependents = new ArrayList<>();
		for (SemanticGraph.Edge e : g.outgoingEdgesOf(p))
		{
			dependents.add(e.getRole() + "-" + getId(g.getEdgeTarget(e)));
		}

		StringBuilder str = new StringBuilder(getId(p));
		dependents.stream()
				.sorted(Comparator.naturalOrder())
				.forEach(s -> str.append("_").append(s));

		return str.toString();
	}
}
