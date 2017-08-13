package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.structures.Candidate.Type;
import org.apache.commons.lang3.tuple.Pair;
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
	 * @param structures list of structures, e.g. relations in a KB, parses extracted from text
	 * @return a semantic graph
	 */
	public static ContentGraph createContentGraph(Set<LinguisticStructure> structures)
	{
		// Create empty graph
		ContentGraph graph = new ContentGraph(Role.class);

		// Iterate triples in each input structure and populate graph
		structures.forEach(s -> {
			for (Role e : s.edgeSet())
			{
				AnnotatedWord governor = s.getEdgeSource(e);
				AnnotatedWord dependent = s.getEdgeTarget(e);

				// Add governing node to graph
				Pair<Entity, Mention> govNode = createGraphNode(s, governor);
				graph.addVertex(govNode.getLeft()); // does nothing if a node with same id was already added
				graph.addAnchor(govNode.getLeft(), govNode.getRight());

				// Add dependent tree node to graph
				Pair<Entity, Mention> depNode = createGraphNode(s, dependent);
				graph.addVertex(depNode.getLeft()); // does nothing if a node with same id was already added
				graph.addAnchor(depNode.getLeft(), depNode.getRight());

				// Ignore loops.
				// To see how loops may occur in semantic trees, consider "West Nickel Mines Amish School
				// shooting", where "West Nickel Mines Amish School" and "Amish School shooting" are assigned
				// the same nominal synset (therefore same node) and are linked through a NAME relation.
				if (!govNode.getLeft().getId().equals(depNode.getLeft().getId()))
				{
					try
					{
						// Add edge
						Role e2 = new Role(e.getRole(), e.isCore());
						graph.addEdge(govNode.getLeft(), depNode.getLeft(), e2);
					}
					catch (Exception ex)
					{
						throw new RuntimeException("Failed to add edge between " + govNode.getLeft().getId() + " and " + depNode.getLeft().getId() + ": " + ex);
					}
				}
			}
		});

		CycleDetector<Entity, Role> detector = new CycleDetector<>(graph);
		if (detector.detectCycles())
		{
			log.warn("Content graph has cycles: " + detector.findCycles());
		}
//
//		try
//		{
//			DOTExporter<LinguisticStructure.AnnotatedWord, LinguisticStructure.Edge> exporter = new DOTExporter<>(
//					new IntegerComponentNameProvider<>(),
//					new StringComponentNameProvider<>(),
//					new StringComponentNameProvider<>());
//			File temp = File.createTempFile("semgraph", ".dot");
//			exporter.exportGraph(graph, new FileWriter(temp));
//
//			File conllTemp = File.createTempFile("semgraph", ".conll");
//			CoNLLFormat conll = new CoNLLFormat();
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
	 * Creates an Entity object which will act as a node in the content graph
	 * A single node is created for all mentions to non-predicative entities.
	 * A node is created for each predicative word, even if they share the same sense.
	 * @param g a structure
	 * @param n a node in g
	 *
	 * @return a graph node
	 */
	private static Pair<Entity, Mention> createGraphNode(LinguisticStructure g, AnnotatedWord n)
	{
		Optional<Candidate> candidate = n.getBestCandidate();
		Optional<Entity> entity = candidate.map(Candidate::getEntity);
		Mention mention = candidate.map(Candidate::getMention).orElse(n.addMention(Collections.singletonList(n)));
		String ref = entity.map(Entity::getReference).orElse(n.getForm());
		String id = g.isPredicate(n) ? getIdForPredicate(g,n) : ref;
		Type type = entity.map(Entity::getType).orElse(Type.Other);
		double value = entity.map(Entity::getWeight).orElse(0.0);

		return Pair.of(new Entity(id, ref, type, value), mention);
	}

	// use a counter to guarantee that ids for predicates are unique
	private static long predicateIdCounter = 0;

	private static String getIdForPredicate(LinguisticStructure g, AnnotatedWord p)
	{
		List<String> dependents = new ArrayList<>();
		for (Role e : g.outgoingEdgesOf(p))
		{
			dependents.add(e.getRole() + "-" + g.getEdgeTarget(e).getForm());
		}

		StringBuilder str = new StringBuilder(++predicateIdCounter + "_" + p.getForm());
		dependents.stream()
				.sorted(Comparator.naturalOrder())
				.forEach(s -> str.append("_").append(s));

		return str.toString();
	}
}
