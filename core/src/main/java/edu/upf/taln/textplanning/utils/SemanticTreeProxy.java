package edu.upf.taln.textplanning.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.OrderedTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * A proxy class for semantic tree objects that provides a view of them as the OrderedTree objects manipulated by the
 * tree edit distance library.
 * Immutable class.
 */
public final class SemanticTreeProxy implements unnonouno.treedist.Tree
{
	private final ImmutableList<OrderedTree.Node<AnnotatedEntity>> nodes; // list of nodes in preorder
	private final ImmutableMap<OrderedTree.Node<AnnotatedEntity>, Integer> index; // indexes for each node

	public SemanticTreeProxy(AnnotatedTree inTree)
	{
		List<OrderedTree.Node<AnnotatedEntity>> preOrder = inTree.getPreOrder();
		ImmutableList.Builder<OrderedTree.Node<AnnotatedEntity>> builder = ImmutableList.builder();
		nodes = builder.addAll(preOrder).build();

		Map<OrderedTree.Node<AnnotatedEntity>, Integer> mutableIndex = new HashMap<>();
		nodes.stream().forEach(e -> mutableIndex.put(e, nodes.indexOf(e)));
		ImmutableMap.Builder<OrderedTree.Node<AnnotatedEntity>, Integer> mapBuilder = ImmutableMap.builder();
		index = mapBuilder.putAll(mutableIndex).build();
	}

	public OrderedTree.Node<AnnotatedEntity> getEntity(int i)
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

		OrderedTree.Node<AnnotatedEntity> e = nodes.get(i);

		if (e.isLeaf())
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

		OrderedTree.Node<AnnotatedEntity> node = nodes.get(i);
		Optional<OrderedTree.Node<AnnotatedEntity>> nextSibling = node.getNextSibling();
		if (!nextSibling.isPresent())
			return NOT_FOUND;
		else
		{
			return index.get(nextSibling.get());
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

		OrderedTree.Node<AnnotatedEntity> node = nodes.get(i);
		Optional<OrderedTree.Node<AnnotatedEntity>> parent = node.getParent();
		if (!parent.isPresent())
		{
			return NOT_FOUND; // no parent ?
		}
		return index.get(parent.get());
	}

	@Override
	public int size()
	{
		return nodes.size();
	}
}
