package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.LinguisticStructure;

import java.util.Collection;

public interface DocumentWriter
{
	String writeStructures(Collection<LinguisticStructure> structures);
}
