package edu.upf.taln.textplanning.structures;

import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class SemanticTree extends SimpleDirectedGraph<String, Role>
{
	private static final String root_label = "root";
	private final String root;
	private final SemanticSubgraph subgraph;
	private final Map<String, String> correspondences = new HashMap<>();

	public SemanticTree(SemanticSubgraph subgraph)
	{
		super(Role.class);

		// Duplicate subgraph
		this.subgraph = subgraph;
		subgraph.vertexSet().forEach(this::addVertex);
		subgraph.edgeSet().forEach(e ->
		{
			Role new_e = Role.create(e.getLabel());
			this.addEdge(subgraph.getEdgeSource(e), subgraph.getEdgeTarget(e), new_e);
		});

		// Replicate vertices with multiple ancestors
		Optional<String> v_multiple = vertexSet().stream()
				.filter(v -> inDegreeOf(v) > 1)
				.findFirst();

		while (v_multiple.isPresent())
		{
			replicate(v_multiple.get());

			v_multiple = vertexSet().stream()
					.filter(v -> inDegreeOf(v) > 1)
					.findFirst();
		}

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

	private void replicate(String v)
	{
		final Iterator<Role> it = incomingEdgesOf(v).iterator();

		for (int i = 0; i < inDegreeOf(v); i++)
		{
			// add replica of v for current ancestor
			String v_replica = String.format("%s_%d", v, i);
			addVertex(v_replica);

			// add new edge from v's ancestor to the replica
			final Role e = it.next();
			String ancestor = getEdgeSource(e);
			addNewEdge(ancestor, v_replica, e.getLabel());

			// Link any outgoing edges of v to the replica
			outgoingEdgesOf(v).forEach(e2 -> addNewEdge(v_replica, getEdgeTarget(e2), e2.getLabel()));
			correspondences.put(v_replica, v);
		}

		removeVertex(v);
	}


	private String createLabel(String v)
	{
		if (this.root.equals(v))
			return v;
		else
			return String.format("%s_%s", getMeaning(v), incomingEdgesOf(v).iterator().next());
	}

	private void addNewEdge(String source, String target, String role)
	{
		Role new_edge = Role.create(role);
		addEdge(source, target, new_edge);
	}
}
