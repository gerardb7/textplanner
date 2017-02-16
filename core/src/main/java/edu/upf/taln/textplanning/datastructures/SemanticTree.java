package edu.upf.taln.textplanning.datastructures;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A semantic tree is an ordered n-ary tree where nodes correspond to annotations and edges are labelled with roles.
 */
public class SemanticTree extends OrderedTree<Pair<AnnotationInfo, String>>
{
	/**
	 * A comparator for sorting siblings in ordered trees.
	 * Nodes are sorted according to their roles first, then their references, lemmas and word forms.
	 */
	public static class SiblingComparator implements Comparator<OrderedTree.Node<Pair<AnnotationInfo, String>>>
	{
		@Override
		public int compare(OrderedTree.Node<Pair<AnnotationInfo, String>> o1,
		                   OrderedTree.Node<Pair<AnnotationInfo, String>> o2)
		{
			String role1 = o1.getData().getRight();
			String role2 = o1.getData().getRight();
			if (!role1.equals(role2))
			{
				return role1.compareTo(role2);
			}
			else
			{
				String ref1 = o1.getData().getLeft().getReference();
				String ref2 = o1.getData().getLeft().getReference();
				if (ref1 != null && ref2 != null && !ref1.equals(ref2))
				{
					return ref1.compareTo(ref2);
				}
				else
				{
					return o1.getData().getLeft().compareTo(o2.getData().getLeft());
				}
			}
		}
	}


	private final double position;

	/**
	 * Constructor
	 * @param inRootAnnotation annotation to act as root of the tree
	 */
	public SemanticTree(AnnotationInfo inRootAnnotation, double inPosition)
	{
		super(Pair.of(inRootAnnotation, "root"), new SiblingComparator());

		position = inPosition;
	}

	public double getPosition() { return position; }

	/**
	 * Only nouns are considered entities!
	 * @return entities
	 */
	public Set<String> getEntities()
	{
		return this.getPreOrder().stream()
				.filter(SemanticTree::isEntity)
				.map(e -> e.getData().getLeft().getReference())
				.collect(Collectors.toSet());
	}

	/**
	 * Returns word forms of nodes which cannot be considered an entity
	 * @return word forms
	 */
	public Set<String> getWordForms()
	{
		return this.getPreOrder().stream()
				.filter(e -> !isEntity(e))
				.map(e -> e.getData().getLeft().getForm())
				.collect(Collectors.toSet());
	}

	/**
	 * Decides whether a node can be considered an entity:
	 * 1- Node has a reference
	 * 2- Node is a noun
	 *
	 * @param inNode node to be evaluated
	 * @return true if it is an entity
	 */
	public static boolean isEntity(OrderedTree.Node<Pair<AnnotationInfo, String>> inNode)
	{
		return inNode.getData().getLeft().getReference() != null && inNode.getData().getLeft().getPOS().startsWith("NN");
	}

	public boolean shareEntities(SemanticTree inTree)
	{
		Set<String> thisEntities = getEntities();
		Set<String> otherEntities = inTree.getEntities();
		return !Collections.disjoint(thisEntities, otherEntities);
	}

//	public boolean areEquivalent(SemanticTree inTree)
//	{
//		OrderedTree<Pair<AnnotationInfo, String>>.PreOrderIterator it1 = this.getPreOrderIterator();
//		OrderedTree<Pair<AnnotationInfo, String>>.PreOrderIterator it2 = inTree.getPreOrderIterator();
//
//		while (it1.hasNext() && it2.hasNext())
//		{
//			Pair<AnnotationInfo, String> node1 = it1.next();
//			Pair<AnnotationInfo, String> node2 = it2.next();
//			if (!node1.getRight().equals(node2.getRight()))
//			{
//				return false;
//			}
//
//			if (!node1.getLeft().equivalent(node2.getLeft()))
//			{
//				return false;
//			}
//		}
//
//		return !it1.hasNext() && !it2.hasNext(); // true if no items left in none of the trees
//	}
}
