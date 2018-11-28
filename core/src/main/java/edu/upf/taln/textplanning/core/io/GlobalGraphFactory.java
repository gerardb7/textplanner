package edu.upf.taln.textplanning.core.io;

import edu.upf.taln.textplanning.core.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.core.structures.GraphList;

public interface GlobalGraphFactory
{
	GlobalSemanticGraph create(GraphList graphs);
}
