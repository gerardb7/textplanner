package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.SubGraph;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import org.apache.commons.lang3.tuple.Pair;

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
	 * Extracts patterns from a content graph
	 * @param g a content graph
	 * @param rankedEntities entities in trees and their scores
	 * @param numPatterns number of patterns to extract
	 * @return the set of extracted patterns
	 */
	public static Set<SemanticTree> extract(SemanticGraph g, Map<Entity, Double> rankedEntities, int numPatterns)
	{
		// Assign weights to nodes in the graph
		g.vertexSet().forEach(v -> v.setWeight(rankedEntities.get(v.getEntity())));

		// Work out average node weight, which will be used as weight for edges
		double avgWeight = g.vertexSet().stream().mapToDouble(Node::getWeight).average().orElse(1.0);

		// Extract patterns
		Set<SemanticTree> patterns = new HashSet<>();
		boolean stop = patterns.size() == numPatterns;
		while(!stop)
		{
			SubGraph heavySubgraph = extractHeavySubgraph(numPatterns, g, avgWeight);
			if (heavySubgraph != null)
			{
				// split graph into trees
				patterns.add(SemanticTree.createTree(heavySubgraph));

				// Remove edges in subgraph from base graph
				heavySubgraph.edgeSet()
						.forEach(g::removeEdge);

				stop = patterns.size() == numPatterns;
			}
			else
				stop = true;
		}

		return patterns;
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

		// Start off from k top ranked verbal predicates
		List<Node> topNodes = g.vertexSet().stream()
				.filter(n -> isInflectedVerb(g, n)) // is a predicate
				.sorted((v1, v2) -> Double.compare(v2.getWeight(), v1.getWeight()))// swapped v1 and v2 to obtain descending order
				.limit(k)
				.collect(Collectors.toList());

		if (topNodes.isEmpty())
			return null;

		// Create beam of open states: patterns corresponding to top predicates with their (recursive) arguments)
		PriorityQueue<Pair<SubGraph, Double>> beam =
			new PriorityQueue<>((s1, s2) -> Double.compare(s2.getRight(), s1.getRight())); // swapped s1 and s2 to obtain descending order
		topNodes.stream()
				.map(n -> new SubGraph(g, n))
				.map(s -> { PatternExtraction.addArguments(s); return s; }) // get expanded subgraphs
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
				.mapToDouble(Node::getWeight)
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
		Set<SubGraph> expansions = getLeafExpansions(s);
		expansions.forEach(PatternExtraction::addArguments);

		return expansions;
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
						.filter(e -> !e.isArg))
				.filter(s::isValidExpansion) // prevents getting caught in cycles
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
							.filter(Edge::isArg))
					.filter(s::isValidExpansion) // prevents getting caught in cycles
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
