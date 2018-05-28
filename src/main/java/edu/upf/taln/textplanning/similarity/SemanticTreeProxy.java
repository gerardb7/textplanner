package edu.upf.taln.textplanning.similarity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.SemanticTree;

import java.util.*;
import java.util.stream.Collectors;


/**
 * A proxy class for semantic trees that provides a view of them as the OrderedTree objects manipulated by the
 * tree edit distance library.
 * Immutable class.
 */
public final class SemanticTreeProxy implements unnonouno.treedist.Tree
{
	private final SemanticTree tree;
	private final ImmutableList<String> nodes; // list of nodes in preorder
	private final ImmutableMap<String, Integer> index; // indexes for each node

	SemanticTreeProxy(SemanticTree t)
	{
		tree = t;

		// Get list of nodes in preorder
		List<String> preorder_nodes = tree.getPreOrderLabels();

		ImmutableList.Builder<String> builder = ImmutableList.builder();
		nodes = builder.addAll(preorder_nodes).build();

		Map<String, Integer> mutableIndex = new HashMap<>();
		nodes.forEach(n -> mutableIndex.put(n, nodes.indexOf(n)));
		ImmutableMap.Builder<String, Integer> mapBuilder = ImmutableMap.builder();
		index = mapBuilder.putAll(mutableIndex).build();
	}

	public Optional<String> getMeaning(int i)
	{
		return tree.getMeaning(nodes.get(i)).map(Meaning::getReference);
	}

	String getParentRole(int i)
	{
		return tree.getParentRole(nodes.get(i));
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

		String m = nodes.get(i);

		if (tree.outDegreeOf(m) == 0)
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

		String v = nodes.get(i);
		String p = tree.getEdgeSource(tree.incomingEdgesOf(v).iterator().next());
		List<String> siblings = tree.outgoingEdgesOf(p).stream()
				.map(tree::getEdgeTarget)
				.sorted(Comparator.naturalOrder())
				.collect(Collectors.toList());

		int next_sibling = siblings.indexOf(v) + 1;
		if (next_sibling >= siblings.size())
			return NOT_FOUND;
		else
		{
			return index.get(siblings.get(next_sibling));
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

		String v = nodes.get(i);
		if (tree.inDegreeOf(v) == 0)
			return NOT_FOUND;

		String p = tree.getEdgeSource(tree.incomingEdgesOf(v).iterator().next());
		return index.get(p);
	}

	@Override
	public int size()
	{
		return nodes.size();
	}
}
