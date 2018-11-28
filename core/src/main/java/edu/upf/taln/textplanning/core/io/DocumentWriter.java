package edu.upf.taln.textplanning.core.io;

import edu.upf.taln.textplanning.core.structures.SemanticSubgraph;

import java.util.List;

public interface DocumentWriter
{
	String write(List<SemanticSubgraph> subgraphs);
}
