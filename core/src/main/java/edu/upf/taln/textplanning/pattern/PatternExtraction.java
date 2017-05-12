package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.SubGraph;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.IntegerComponentNameProvider;
import org.jgrapht.ext.StringComponentNameProvider;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.min;

/**
 * Pattern extraction strategy based on finding dense subgraphs (multitrees) in a semantic graph.
 * Determination of heaviest subgraphs approximated using greedy algorithm as suggested in:
 * "Event detection in activity networks" Rozenshtein, P., Anagnostopoulos, A., Gionis, A., Tatti, N. (2014)
  */
public class PatternExtraction
{
	private final static double lambda = 1.0
			;

	/**
	 * Extracts patterns from a semantic graph
	 * @return the set of extracted patterns
	 */
	public static Set<SemanticTree> extract(List<SemanticTree> inContents, Map<Entity, Double> rankedEntities,
	                                        int inNumPatterns)
	{
		// Create semantic graph
		SemanticGraph g = createPatternExtractionGraph(inContents, rankedEntities);

		// Work out average node weight, which will be used as weight for edges
		double avgWeight = g.vertexSet().stream().mapToDouble(v -> v.weight).average().orElse(1.0);

		// Extract patterns
		Set<SemanticTree> patterns = new HashSet<>();
		boolean stop = patterns.size() == inNumPatterns;
		while(!stop)
		{
			SubGraph heavySubgraph = extractHeavySubgraph(inNumPatterns, g, avgWeight);
			if (heavySubgraph != null)
			{
				// split graph into trees
				patterns.add(SemanticTree.createTree(heavySubgraph));

				// Remove edges in subgraph from base graph
				heavySubgraph.edgeSet()
						.forEach(g::removeEdge);

				stop = patterns.size() == inNumPatterns;
			}
			else
				stop = true;
		}

		return patterns;
	}

	/**
	 * Creates a pattern extraction graph
	 * @param trees list of annotated trees
	 * @param rankedEntities entities in trees and their scores
	 * @return a semantic graph
	 */
	private static SemanticGraph createPatternExtractionGraph(List<SemanticTree> trees,
	                                                          Map<Entity, Double> rankedEntities)
	{
		// Create empty graph
		SemanticGraph graph = new SemanticGraph(Edge.class);
		Map<String, Set<Node>> ids = new HashMap<>();

		// Iterate triples in each tree and populate graph from them
		trees.forEach(t -> {
					for (Edge e : t.edgeSet())
					{
						Node governor = t.getEdgeSource(e);
						Node dependent = t.getEdgeTarget(e);

						// Add governing tree node to graph
						Node govNode = createGraphNode(t, governor, rankedEntities);
						graph.addVertex(govNode); // does nothing if node existed
						ids.computeIfAbsent(govNode.id, v -> new HashSet<>()).add(governor);

						// Add dependent tree node to graph
						Node depNode = createGraphNode(t, dependent, rankedEntities);
						graph.addVertex(depNode); // does nothing if node existed
						ids.computeIfAbsent(depNode.id, v -> new HashSet<>()).add(dependent);

						// Add edge
						if (!govNode.id.equals(depNode.id))
						{
							try
							{
								Edge e2 = new Edge(e.role, e.isArg);
								graph.addEdge(govNode, depNode, e2);
							}
							catch (Exception ex)
							{
								throw new RuntimeException("Failed to add edge between " + govNode.id + " and " + depNode.id + ": " + ex);
							}
						}
						// Ignore loops.
						// To see how loops may occur in semantic trees, consider "West Nickel Mines Amish School
						// shooting", where "West Nickel Mines Amish School" and "Amish School shooting" are assigned
						// the same synset (same id and therefore same node) and are linked through a NAME relation.
					}
				});

		try
		{
			DOTExporter<Node, Edge> exporter = new DOTExporter<>(
					new IntegerComponentNameProvider<>(),
					new StringComponentNameProvider<>(),
					new StringComponentNameProvider<>());
			File temp = File.createTempFile("semgraph", ".dot");
			exporter.exportGraph(graph, new FileWriter(temp));

			File conllTemp = File.createTempFile("semgraph", ".conll");
			ConLLAcces conll = new ConLLAcces();
			String graphConll = conll.writeGraphs(Collections.singleton(graph));
			FileUtils.writeStringToFile(conllTemp, graphConll, Charset.forName("UTF-16"));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}

		return graph;
	}

	/**
	 * Creates a graph node from a tree node.
	 * A single graph node is created for all non-predicative tree nodes with the same label.
	 * A single graph node is created for all predicates with same label and same dependent labels.
	 * The graph node is assigned a weight.
	 * @param t a tree
	 * @param n a node in the tree
	 * @param rankedEntities weights for nodess
	 *
	 * @return a graph node
	 */
	private static Node createGraphNode(SemanticTree t, Node n, Map<Entity, Double> rankedEntities)
	{
		String id = n.getEntity().getEntityLabel();
		if (t.isPredicate(n) || id.equals("_")) // if a predicate use predicate and argument labels as id
			id = predicateToString(t, n);
		double govWeight = rankedEntities.get(n.getEntity());

		return new Node(id, n.getEntity(), govWeight);
	}

	private static String predicateToString(SemanticTree t, Node p)
	{
		StringBuilder str = new StringBuilder(p.getEntity().getEntityLabel());
		for (Edge e : t.outgoingEdgesOf(p))
		{
			str.append("_").append(e.getRole()).append("-").append(t.getEdgeTarget(e).getEntity().getEntityLabel());
		}

		return str.toString();
	}

	/**
	 * Local beam search for a heavy subgraph.
	 * @param k size of the beam
	 * @param g the graph
	 * @param edgeWeight cost of weights in subgraph, is substracted from node weights
	 * @return a heavy subgraph or null if no graph was found
	 */
	private static SubGraph extractHeavySubgraph(int k, SemanticGraph g, double edgeWeight)
	{
		// All jgrapht graphs are tested for equality by comparing their vertex and edge sets.
		// Nodes are equal if they share the same id.
		// Edges are equal if they're the same instance, which is fine as all expansions are beam with a subset of
		// the edges of the base graph, so instances will match.

		// Get k top ranked non-predicative nodes
		List<Node> topNodes = g.vertexSet().stream()
				.filter(n -> !g.isPredicate(n)) // is not a predicate
				.filter(n -> g.inDegreeOf(n) > 0) // is argument of some predicate
				.sorted((v1, v2) -> Double.compare(v2.weight, v1.weight))// swapped v1 and v2 to obtain descending order
				.limit(k)
				.collect(Collectors.toList());

		if (topNodes.isEmpty())
			return null;

		// Create beam of open states: start subgraphs resulting from applying first expansion to top nodes
		PriorityQueue<Pair<SubGraph, Double>> beam =
			new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight())); // swapped s1 and s2 to obtain descending order
		topNodes.stream()
				.map(n -> new SubGraph(g, n))
				.map(PatternExtraction::getExpansions) // get expanded subgraphs
				.flatMap(Set::stream)
				.map(s -> Pair.of(s, calculateWeight(s, edgeWeight)))
				.forEach(beam::add);

		// Create set of visited subgraphs that have already been expanded (visited states)
		Set<SubGraph> visited = new HashSet<>();
		beam.forEach(p -> visited.add(p.getLeft()));

		Set<Pair<SubGraph, Double>> next;
		do
		{
			// Expand subgraphs in beam to find potential next states to visit
			next = beam.stream()
					.map(Pair::getLeft)
					.map(PatternExtraction::getExpansions) // get expanded beam
					.flatMap(Set::stream)
					.filter(s -> !visited.contains(s)) // discard visited states
					.map(s -> Pair.of(s, calculateWeight(s, edgeWeight))) // weight subgraph
					.collect(Collectors.toSet());

			// Update list of visited states
			next.forEach(p -> visited.add(p.getLeft()));

			// Update beam: 1- add new states to it
			beam.addAll(next);
			// Update beam: 2- poll top k states
			PriorityQueue<Pair<SubGraph, Double>> bestSubgraphs = new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight()));
			IntStream.range(0, min(k, beam.size()))
					.mapToObj(i -> beam.poll())
					.forEach(bestSubgraphs::add);

			// Update beam: 3- replace old beam with top k states
			beam.clear();
			beam.addAll(bestSubgraphs);

		}
		while (!next.isEmpty());

		if (beam.isEmpty())
			return null;

		return beam.poll().getLeft();
	}

	/**
	 * Calculates the cost of a subgraph as a combination of node weights and edge distances
	 * @param s subgraph to weight
	 * @param edgeWeight fixed weight assigned to each edge
	 * @return cost of the subgraph
	 */
	private static double calculateWeight(SubGraph s, double edgeWeight)
	{
		double ws = s.vertexSet().stream()
				.mapToDouble(n -> n.weight)
				.sum();
		double ds = s.edgeSet().size() * edgeWeight; // assign edges the average node weight
		double dv = s.getBase().edgeSet().size() * edgeWeight;
		return lambda*ws - ds + dv;
	}

	/**
	 * Returns the set of expansions of a subgraph relative to the graph it belongs to.
	 * @return a set of expanded subgraphs
	 */
	private static Set<SubGraph> getExpansions(SubGraph s)
	{
		Set<SubGraph> expansions = getRootExpansions(s);
		expansions.addAll(getLeafExpansions(s));
		expansions.forEach(PatternExtraction::addArguments);

		return expansions.stream()
				.filter(e -> isInflectedVerb((SemanticGraph)e.getBase(), e.getRoot())) // Discard non-verbal roots
				.collect(Collectors.toSet());
	}

	/**
	 * Given a graph g and a rooted subgraph s, return a new subgraph for each verbal node in g that is an ancestor
	 * of the root of s. Each returned subgraph contains s plus additional nodes and edges in g.
	 * @return expanded set of subgraphs
	 */
	private static Set<SubGraph> getRootExpansions(SubGraph s)
	{
		Set<SubGraph> trees = new HashSet<>();
		trees.add(s);

		Node root = s.getRoot();
		if (s.getBase().inDegreeOf(root) == 0 || isInflectedVerb((SemanticGraph)s.getBase(), root))
		{
			return trees;
		}
		else
		{
			return s.getBase().incomingEdgesOf(root).stream()
					.map(e -> new SubGraph(s, e))
					.map(PatternExtraction::getRootExpansions)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());
		}
	}

	/**
	 * Given a semantic graph g and a rooted subgraph s, return all subgraphs resulting from adding to s an edge in g
	 * indicating a non-arg relation where the governor is a node in s.
	 * @return expanded set of subgraphs
	 */
	private static Set<SubGraph> getLeafExpansions(SubGraph s)
	{
		return s.vertexSet().stream()
				.flatMap(v -> s.getBase().outgoingEdgesOf(v).stream()
						.filter(e -> !e.isArg)
						.filter(e -> !s.containsEdge(e)))
				.map(e -> new SubGraph(s, e))
				.collect(Collectors.toSet());
	}

	/**
	 * Given a semantic graph g and a rooted subgraph s, recursively adds all arguments of any node of s in g.
	 */
	private static void addArguments(SubGraph s)
	{
		List<Edge> edgesToArgs;
		do
		{
			edgesToArgs = s.vertexSet().stream()
					.flatMap(v -> s.getBase().outgoingEdgesOf(v).stream()
							.filter(Edge::isArg)
							.filter(e -> !s.containsEdge(e)))
					.collect(Collectors.toList());
			edgesToArgs.forEach(s::expand);
		}
		while (!edgesToArgs.isEmpty());
	}

	private static boolean isInflectedVerb(SemanticGraph g, Node n)
	{
		String[] inflectedVerbs = {"VBD", "VBP", "VBZ"};
		String pos = ((AnnotatedEntity) n.getEntity()).getAnnotation().getPOS();
		return g.isPredicate(n) && Arrays.stream(inflectedVerbs).anyMatch(pos::equals);
	}
}
