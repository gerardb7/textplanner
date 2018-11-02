package edu.upf.taln.textplanning.core.structures;

public interface GraphSemantics
{
	boolean isCore(String role);
	boolean isRequired(String v, String source, String target, String role, GlobalSemanticGraph g);
}
