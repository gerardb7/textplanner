package edu.upf.taln.textplanning.datastructures;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic ordered tree with labelled edges.
 * Immutable class.
 */
public class OrderedTree<T>
{
	private final Node<T> root;

	public static class Node<T>
	{
		private final long id; // Not used to compare nodes
		private static long idCounter = 0;
		private final T data;
		private final Optional<Node<T>> parent;
		private final TreeSet<Node<T>> children; // children are kept ordered

		public Node(T inData, Optional<Node<T>> inParent, Comparator<? super Node<T>> inComparator)
		{
			id = ++idCounter;
			data = inData;
			parent = inParent;
			children = new TreeSet<>(inComparator);
		}

		public long getId()
		{
			return id;
		}

		public boolean isLeaf()
		{
			return this.children.isEmpty();
		}

		public boolean isRoot()
		{
			return !this.parent.isPresent();
		}

		// Getters
		public T getData()
		{
			return data;
		}

		public Optional<Node<T>> getParent()
		{
			return parent;
		}

		public int getDegree()
		{
			return children.size();
		}

		public Optional<Node<T>> getRightMostChild()
		{
			return children.isEmpty() ? Optional.empty() : Optional.of(children.last());
		}

		public List<Node<T>> getChildren()
		{
			return new ArrayList<>(children);
		}

		public List<T> getChildrenData()
		{
			return children.stream()
					.map(Node::getData)
					.collect(Collectors.toList());
		}

		public Optional<Node<T>> getNextSibling()
		{
			if (!this.parent.isPresent())
			{
				return Optional.empty();
			}

			return Optional.ofNullable(this.parent.get().children.higher(this));
		}

		public Node<T> addChild(T inChild)
		{
			Node<T> child = new Node<>(inChild, Optional.of(this), this.children.comparator());
			children.add(child);

			return child;
		}

		public List<Node<T>> getPreOrder()
		{
			List<Node<T>> preOrder = new ArrayList<>();
			preOrder.add(this);

			this.children.forEach(c -> preOrder.addAll(c.getPreOrder()));
			return preOrder;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (o == null || getClass() != o.getClass())
			{
				return false;
			}

			if (!(o instanceof OrderedTree.Node))
			{
				return false;
			}

			OrderedTree.Node other = (OrderedTree.Node) o;
			return this.id == other.id;
		}


		@Override
		public int hashCode()
		{
			return (int) (id ^ (id >>> 32));
		}
	}

	public class PreOrderIterator implements Iterator
	{
		private final Deque<Iterator<Node<T>>> stack = new ArrayDeque<>();

		public PreOrderIterator(OrderedTree<T> inTree)
		{
			Node<T> root = inTree.root;
			stack.push(Collections.singleton(root).iterator());
		}

		public boolean hasNext()
		{
			return !stack.isEmpty();
		}

		public T next()
		{
			if (!hasNext())
			{
				throw new NoSuchElementException();
			}

			Iterator<Node<T>> it = stack.peek();
			Node<T> node = it.next();
			if (!it.hasNext())
			{
				stack.pop();
			}

			Iterator<Node<T>> childrenIt = node.children.iterator();
			if (childrenIt.hasNext())
			{
				stack.push(childrenIt);
			}

			return node.getData();
		}
	}


	public OrderedTree(T inData, Comparator<Node<T>> inComparator)
	{
		root = new Node<>(inData, Optional.empty(), inComparator);
	}

	public OrderedTree(OrderedTree.Node<T> inNode)
	{
		root = new Node<>(inNode.getData(), Optional.empty(), inNode.children.comparator());
	}

	public Node<T> getRoot()
	{
		return this.root;
	}

	public List<Node<T>> getLeaves()
	{
		return root.getPreOrder().stream().
				filter(Node::isLeaf).
				collect(Collectors.toList());
	}

	public List<Node<T>> getPreOrder()
	{
		final List<Node<T>> preorder = new ArrayList<>();
		final Deque<Iterator<Node<T>>> stack = new ArrayDeque<>();

		stack.push(Collections.singleton(root).iterator());
		while (!stack.isEmpty())
		{
			Iterator<Node<T>> it = stack.peek();
			Node<T> node = it.next();
			preorder.add(node);
			if (!it.hasNext())
			{
				stack.pop();
			}

			Iterator<Node<T>> childrenIt = node.children.iterator();
			if (childrenIt.hasNext())
			{
				stack.push(childrenIt);
			}
		}

		return preorder;
	}

	public PreOrderIterator getPreOrderIterator()
	{
		return new PreOrderIterator(this);
	}

	public String toString()
	{
		return getPreOrder().stream()
				.map(n -> n.getData().toString() + (n.isLeaf() ? " // " : " -> "))
				.reduce(String::concat).orElse("");
	}

	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		// Two trees are equal if they hold the same data in the same (preorder) order
		List<T> thisData = getPreOrder().stream()
				.map(Node::getData)
				.collect(Collectors.toList());
		@SuppressWarnings("unchecked") OrderedTree<T> other = (OrderedTree<T>) o;
		List<T> otherData = other.getPreOrder().stream()
				.map(Node::getData)
				.collect(Collectors.toList());

		return thisData.equals(otherData);
	}

	public int hashCode()
	{
		return getPreOrder().stream()
				.map(Node::getData)
				.mapToInt(T::hashCode)
				.reduce((x, y) -> 31 * x + y)
				.getAsInt();
	}
}
