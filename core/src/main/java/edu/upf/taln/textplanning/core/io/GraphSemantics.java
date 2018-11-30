package edu.upf.taln.textplanning.core.io;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;

public interface GraphSemantics
{
	boolean isCore(String role);
	boolean isRequired(String v, String source, String target, String role, SemanticGraph g);
}
