package edu.upf.taln.textplanning.datastructures;

import org.apache.commons.lang3.tuple.Triple;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An annotated tree is an ordered n-ary tree where nodes correspond to annotations of senses in a text.
 */
public class AnnotatedTree extends OrderedTree<AnnotatedEntity>
{
	/**
	 * A comparator for sorting siblings in ordered trees.
	 * Nodes are sorted according to their roles first, then their references, lemmas and word forms.
	 */
	public static class SiblingComparator implements Comparator<OrderedTree.Node<AnnotatedEntity>>
	{
		@Override
		public int compare(OrderedTree.Node<AnnotatedEntity> o1,
		                   OrderedTree.Node<AnnotatedEntity> o2)
		{
			String role1 = o1.getData().getAnnotation().getRole();
			String role2 = o2.getData().getAnnotation().getRole();
			if (!role1.equals(role2))
			{
				return role1.compareTo(role2);
			}
			else
			{
				String ref1 = o1.getData().getEntityLabel();
				String ref2 = o2.getData().getEntityLabel();
				return ref1.compareTo(ref2);
			}
		}
	}

	private final double position;

	/**
	 * Constructor
	 * @param inRootAnn annotation to act as root of the tree
	 */
	public AnnotatedTree(AnnotatedEntity inRootAnn)
	{
		super(inRootAnn, new SiblingComparator());
		position = 0.0;
	}

	/**
	 * Constructor
	 * @param inRootAnn annotation to act as root of the tree
	 * @param inPosition position of the semantic tree in a document, dialogue, etc.
	 */
	public AnnotatedTree(AnnotatedEntity inRootAnn, double inPosition)
	{
		super(inRootAnn, new SiblingComparator());
		position = inPosition;
	}

	public double getPosition() { return position; }


	/**
	 * Return list of dependencies
	 * @return dependencies as triples <governing node, dependent node, role of dependent>
	 */
	public List<Triple<OrderedTree.Node<AnnotatedEntity>, OrderedTree.Node<AnnotatedEntity>, String>> getDependencies()
	{
		return getPreOrder().stream()
				.map(p -> p.getChildren().stream()
						.map(c -> Triple.of(p, c, c.getData().getAnnotation().getRole()))
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.toList());
	}

	/**
	 * @return true if node has one or more arguments (i.e. dependents with ARG roles)
	 */
	public static boolean isPredicate(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		return inNode.getChildren().stream().anyMatch(AnnotatedTree::isArgument);
	}

	/**
	 * @return true if node has an argument role assigned to it
	 */
	public static boolean isArgument(OrderedTree.Node<AnnotatedEntity> inNode)
	{
		String role = inNode.getData().getAnnotation().getRole();
		return role.equals("I") || role.equals("II") || role.equals("III") || role.equals("IV") || role.equals("V")
				|| role.equals("VI") || role.equals("VII") || role.equals("VIII") || role.equals("IX") || role.equals("X");
	}
}
