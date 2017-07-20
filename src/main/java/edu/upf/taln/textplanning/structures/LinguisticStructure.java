package edu.upf.taln.textplanning.structures;

import org.jgrapht.experimental.dag.DirectedAcyclicGraph;

/**
 * A linguistic structure is a directed acyclic graph with annotated words as nodes. Edges indicate relations between
 * words and the roles adopted by them.
 * This class can be used to represent dependency-based linguistic structures where relations are word-to-word.
 * Relations can be syntactic, semantic or discursive.
 */
public final class LinguisticStructure extends DirectedAcyclicGraph<AnnotatedWord, Role>
{

	private final Document d;

	public LinguisticStructure(Document d, Class<? extends Role> edgeClass)
	{
		super(edgeClass);
		this.d = d;
	}

	public Document getDocument() { return d; }

	/**
	 * @return true if node has one or more arguments
	 */
	public boolean isPredicate(AnnotatedWord n)
	{
		return outgoingEdgesOf(n).stream().anyMatch(Role::isCore);
	}
}
