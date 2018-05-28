package edu.upf.taln.textplanning.structures;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class SemanticTree extends SimpleDirectedGraph<String, Role>
{
	private static final String root_label = "root";
	private final String root;
	private final SemanticSubgraph subgraph;
	private final Multiset<String> counters = HashMultiset.create();
	private final Map<String, String> correspondences = new HashMap<>();

	public SemanticTree(SemanticSubgraph subgraph)
	{
		super(Role.class);

		// Duplicate subgraph
		this.subgraph = subgraph;
		subgraph.vertexSet().forEach(this::addVertex);
		subgraph.edgeSet().forEach(e -> this.addEdge(subgraph.getEdgeSource(e), subgraph.getEdgeTarget(e), e));

		// Replicate vertices with multiple ancestors
		Set<String> M = vertexSet().stream()
				.filter(v -> inDegreeOf(v) > 1)
				.collect(toSet());

		Set<Role> E = M.stream()
				.map(this::incomingEdgesOf)
				.flatMap(Set::stream)
				.collect(toSet());

		E.forEach(e -> edgeShift(getEdgeSource(e), getEdgeTarget(e), e));

		// Root the graph
		Set<String> roots = vertexSet().stream()
				.filter(v -> inDegreeOf(v) == 0)
				.collect(toSet());

		if (roots.size() == 1)
			this.root = roots.iterator().next();
		else
		{
			root = root_label;
			addVertex(root_label);
			roots.forEach(r -> addEdge(root_label, r, Role.create(root_label)));
		}
	}

	public SemanticSubgraph getGraph()
	{
		return subgraph;
	}

	public List<String> getPreOrderLabels()
	{
		List<String> preorder = new ArrayList<>();
		preorder.add(root);
		List<String> frontier = new ArrayList<>();
		frontier.add(root);

		while(!frontier.isEmpty())
		{
			frontier = frontier.stream()
					.map(v -> outgoingEdgesOf(v).stream()
							.map(this::getEdgeTarget)
							.map(this::createLabel)
							.sorted()
							.collect(toList()))
					.flatMap(List::stream)
					.peek(preorder::add)
					.collect(toList());
		}

		return preorder;
	}

	public String getParentRole(String v)
	{
		if (!this.containsVertex(v))
			return null;
		else if (this.root.equals(v))
			return "";
		else
			return incomingEdgesOf(v).iterator().next().toString();
	}

	public Optional<Meaning> getMeaning(String v)
	{
		if (containsVertex(v))
			return subgraph.getBase().getMeaning(v);
		else if (correspondences.containsKey(v))
			return subgraph.getBase().getMeaning(correspondences.get(v));
		else
			return Optional.empty();
	}

	private void edgeShift(String x, String y, Role e)
	{
		counters.add(y);
		int count = counters.count(y);
		String y2 = String.format("%s_%d", y, count);
		addVertex(y2);
		addEdge(x, y2, e);
		outgoingEdgesOf(y).forEach(e2 -> addEdge(y2, getEdgeTarget(e2), e2));
		removeVertex(y);
		correspondences.put(y2, y);
	}


	private String createLabel(String v)
	{
		if (this.root.equals(v))
			return v;
		else
			return String.format("%s_%s", getMeaning(v), incomingEdgesOf(v).iterator().next());
	}

//	public String toString()
//	{
//		return root + (outDegreeOf(root) == 0 ? " // " : "") +
//				getTopologicalOrder().stream()
//				.map(v -> v + outgoingEdgesOf(v).stream()
//						.map(this::getEdgeTarget)
//						.collect(joining("(", ",", ")")))
//				.collect(joining(" "));
//	}
}
