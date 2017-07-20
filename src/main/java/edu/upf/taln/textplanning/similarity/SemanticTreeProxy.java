package edu.upf.taln.textplanning.similarity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.upf.taln.textplanning.structures.ContentPattern;
import edu.upf.taln.textplanning.structures.Entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * A proxy class for annotated trees that provides a view of them as the OrderedTree objects manipulated by the
 * tree edit distance library.
 * Immutable class.
 */
public final class SemanticTreeProxy implements unnonouno.treedist.Tree
{
	private final ContentPattern tree;
	private final ImmutableList<Entity> nodes; // list of nodes in preorder
	private final ImmutableMap<Entity, Integer> index; // indexes for each node

	public SemanticTreeProxy(ContentPattern inTree)
	{
		tree = inTree;

		// Get list of nodes in preorder
		List<Entity> preOrderNodes = tree.getTopologicalOrder();

		ImmutableList.Builder<Entity> builder = ImmutableList.builder();
		nodes = builder.addAll(preOrderNodes).build();

		Map<Entity, Integer> mutableIndex = new HashMap<>();
		nodes.forEach(n -> mutableIndex.put(n, nodes.indexOf(n)));
		ImmutableMap.Builder<Entity, Integer> mapBuilder = ImmutableMap.builder();
		index = mapBuilder.putAll(mutableIndex).build();
	}

	public Entity getEntity(int i)
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

		Entity e = nodes.get(i);

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

		Entity node = nodes.get(i);
		Entity parent = tree.getEdgeSource(tree.incomingEdgesOf(node).iterator().next());
		List<Entity> siblings = tree.outgoingEdgesOf(parent).stream()
				.map(tree::getEdgeTarget)
				.collect(Collectors.toList());

		siblings.sort(Comparator.comparing(Entity::toString)); // lexicographic compare
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

		Entity node = nodes.get(i);
		if (tree.inDegreeOf(node) == 0)
			return NOT_FOUND;

		Entity parent = tree.getEdgeSource(tree.incomingEdgesOf(node).iterator().next());
		return index.get(parent);
	}

	@Override
	public int size()
	{
		return nodes.size();
	}
}
