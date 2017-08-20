package edu.upf.taln.textplanning.structures;

import java.util.ArrayList;
import java.util.List;

/**
 * A document containing analyzed structures
 */
public class Document
{
	private final List<LinguisticStructure> structures = new ArrayList<>(); // sequence of structures, e.g. parses of sentences, in textual order

	public Document() {}
	public Document(List<LinguisticStructure> structures) { this.structures.addAll(structures); }

	public int getNumStructures()
	{
		return structures.size();
	}
	public List<LinguisticStructure> getStructures() { return new ArrayList<>(structures); }
	public void addStructure(LinguisticStructure s) { structures.add(s); }
}
