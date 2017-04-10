package edu.upf.taln.textplanning.similarity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Edge;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;

import java.util.*;
import java.util.stream.Collectors;


/**
 * A proxy class for annotated trees that provides a view of them as the OrderedTree objects manipulated by the
 * tree edit distance library.
 * Immutable class.
 */
public final class SemanticTreeProxy implements unnonouno.treedist.Tree
{
	private final SemanticTree tree;
	private final ImmutableList<Node> nodes; // list of nodes in preorder
	private final ImmutableMap<Node, Integer> index; // indexes for each node

	public SemanticTreeProxy(SemanticTree inTree)
	{
		tree = inTree;

		// Get list of nodes in preorder
		List<Edge> preOrder = tree.getPreOrder();
		List<Node> preOrderNodes = new ArrayList<>();
		preOrderNodes.add(tree.getEdgeSource(preOrder.get(0))); // add root to list
		preOrder.stream()
				.map(tree::getEdgeTarget)
				.forEach(preOrderNodes::add);

		ImmutableList.Builder<Node> builder = ImmutableList.builder();
		nodes = builder.addAll(preOrderNodes).build();

		Map<Node, Integer> mutableIndex = new HashMap<>();
		nodes.stream().forEach(n -> mutableIndex.put(n, nodes.indexOf(n)));
		ImmutableMap.Builder<Node, Integer> mapBuilder = ImmutableMap.builder();
		index = mapBuilder.putAll(mutableIndex).build();
	}

	public Node getEntity(int i)
	{
		if (nodes.isEmpty())
		{
			return null;
		}
		if (i < 0 || i >= nodes.size())
		{
			return null;
		}

		return nodes.get(i);
	}

	@Override
	public int getRoot()
	{
		if (nodes.isEmpty())
		{
			return NOT_FOUND;
		}

		return 0;
	}

	@Override
	public int getFirstChild(int i)
	{
		if (nodes.isEmpty())
		{
			return NOT_FOUND;
		}
		if (i < 0 || i >= nodes.size())
		{
			return NOT_FOUND;
		}

		Node e = nodes.get(i);

		if (tree.outDegreeOf(e) == 0)
		{
			return NOT_FOUND;
		}

		return i + 1; // pre-order
	}

	@Override
	public int getNextSibling(int i)
	{
		if (nodes.isEmpty())
			return NOT_FOUND;
		if (i < 0 || i >= nodes.size())
			return NOT_FOUND;
		if (i == 0)
			return NOT_FOUND; // root has no siblings

		Node node = nodes.get(i);
		Node parent = tree.getEdgeSource(tree.incomingEdgesOf(node).iterator().next());
		List<Node> siblings = tree.outgoingEdgesOf(parent).stream()
				.map(tree::getEdgeTarget)
				.collect(Collectors.toList());

		// @todo lexicographical sort very inefficient
		siblings.sort(Comparator.comparing(n -> n.id)); // lexicographic compare
		int nextSibling = siblings.indexOf(node) + 1;

		if (nextSibling >= siblings.size())
			return NOT_FOUND;
		else
		{
			return index.get(siblings.get(nextSibling));
		}
	}

	@Override
	public int getParent(int i)
	{
		if (nodes.isEmpty())
		{
			return NOT_FOUND;
		}
		if (i < 0 || i >= nodes.size())
		{
			return NOT_FOUND;
		}
		if (i == 0)
		{
			return NOT_FOUND; // root has no parent
		}

		Node node = nodes.get(i);
		if (tree.inDegreeOf(node) == 0)
			return NOT_FOUND;

		Node parent = tree.getEdgeSource(tree.incomingEdgesOf(node).iterator().next());
		return index.get(parent);
	}

	@Override
	public int size()
	{
		return nodes.size();
	}
}
