package edu.upf.taln.textplanning.datastructures;

import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class encapsulates mentions to some entity, possibly encompassing multiple tokens.
 */
public final class Mention
{
	private final List<Node> tokens = new ArrayList<>();
	private final int head;
	private final String surfaceForm;

	public Mention(List<Node> tokens, int head)
	{
		this.tokens.addAll(tokens);
		this.head = head;
		surfaceForm = tokens.stream()
				.map(Node::getAnnotation)
				.map(Annotation::getForm)
				.collect(Collectors.joining(" "));
	}

	public List<Node> getTokens() { return new ArrayList<>(tokens);}

	public Node getHead() { return this.tokens.get(head); }

	public String getSurfaceForm() { return surfaceForm; }

	public boolean contains(Mention o) { return this.surfaceForm.contains(o.surfaceForm); }
}
