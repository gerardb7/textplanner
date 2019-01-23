package edu.upf.taln.textplanning.uima;

import edu.upf.taln.textplanning.uima.EntityExtractor.Entity;
import edu.upf.taln.textplanning.uima.EntityExtractor.Participant;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class RelationExtractor
{

	private static class Tree
	{
		private final Node root;

		public Tree(Entity root)
		{
			this.root = new Node(root);
		}

		public static class Node
		{
			private final Entity entity;
			private final String role;
			private final Node parent;
			private final List<Node> children = new ArrayList<>();
			private final int depth;
			private static final String root_role = "root";

			public Node(Entity entity)
			{
				this.entity = entity;
				role = root_role;
				parent = null;
				depth = 0;
			}

			public Node(Entity entity, String role, Node parent)
			{
				this.entity = entity;
				this.role = role;
				this.parent = parent;
				depth = parent.depth + 1;
				parent.children.add(this);
			}

			public Set<Node> getDescendants()
			{
				return children.stream()
						.map(Node::getDescendants)
						.flatMap(Set::stream)
						.collect(Collectors.toSet());
			}

		}
	}


	public static Map<String, Entity> extract(Map<String, Entity> entities) throws Exception
	{
		if (entities.size() < 2)
			return entities;

		Map<String, Entity> simple_trees = new HashMap<>();

		// Determine root entity
		final Set<Entity> dependents = entities.values().stream()
				.map(e -> e.participants)
				.flatMap(List::stream)
				.map(p -> p.participant)
				.collect(Collectors.toSet());
		final List<Entity> roots = entities.values().stream()
				.filter(e -> !dependents.contains(e))
				.collect(toList()); //.orElseThrow(() -> new Exception("Malformed list of entities"));

		for (Entity root : roots)
		{
			// Create a tree structure
			Tree tree = new Tree(root);
			populateTree(tree.root, root.participants);

			// Find the entity of type incident closest to the root
			final Tree.Node main_incident = tree.root.getDescendants().stream()
					.sorted(Comparator.comparingInt(n -> n.depth))
					.filter(n -> n.entity.type.stream()
							.anyMatch(TypeMapper::isIncident))
					.findFirst().orElse(tree.root); // use root if no incidents were found

			// Find entities of type vulnerable object in the descendant set of the main incident
			final List<Tree.Node> objects = main_incident.getDescendants().stream()
					.filter(n -> n.entity.type.stream()
							.anyMatch(TypeMapper::isVulnerableObject))
					.collect(toList());

			// Find all locations, regardless of where in the tree they are found
			final List<Tree.Node> locations = tree.root.getDescendants().stream()
					.filter(n -> n.entity.location != null)
					.filter(n -> !objects.contains(n)) // exclude locations that are also objects
					.collect(toList());

			// Build simplified representation
			final Entity main = new Entity(main_incident.entity); // copy without children
			simple_trees.put(main.id, main);

			// add objects as direct participants of the main event
			objects.stream()
					.map(o -> new Participant(o.role, new Entity(o.entity))) // copies without children
					.forEach(main.participants::add);

			// add locations as direct participants too
			locations.stream()
					.map(l -> new Participant("location", new Entity(l.entity)))
					.forEach(main.participants::add);
		}

		return simple_trees;
	}

	private static void populateTree(Tree.Node parent, List<Participant> participants)
	{
		participants.stream()
				.map(p -> new Tree.Node(p.participant, p.role, parent))
				.forEach(n -> populateTree(n, n.entity.participants));
	}
}
