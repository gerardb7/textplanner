package edu.upf.taln.textplanning.structures;

import org.jgrapht.experimental.dag.DirectedAcyclicGraph;

import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * A linguistic structure is a directed acyclic graph with annotated words as nodes. Edges indicate relations between
 * words and the roles adopted by them.
 * This class can be used to represent dependency-based linguistic structures where relations are word-to-word.
 * Relations can be syntactic, semantic or discursive.
 */
public final class LinguisticStructure extends DirectedAcyclicGraph<AnnotatedWord, Role>
{
	private final Document document;
	private final int position; // position in document document

	public LinguisticStructure(Document d, int pos, Class<? extends Role> edgeClass)
	{
		super(edgeClass);
		this.document = d;
		this.position = pos;
		d.addStructure(this);
	}

	public Document getDocument() { return document; }
	public int getPosition() { return position; }

	/**
	 * @return list of vertices in the structure following their order of appearance in the text fragment it annotates
	 */
	public List<AnnotatedWord> getTextualOrder()
	{
		return vertexSet().stream()
				.sorted(Comparator.comparing(AnnotatedWord::getOffsetStart))
				.collect(toList());
	}

	/**
	 * @return true if node has one or more arguments
	 */
	public boolean isPredicate(AnnotatedWord n)
	{
		return outgoingEdgesOf(n).stream().anyMatch(Role::isCore);
	}
}
