package edu.upf.taln.textplanning.core.io;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;

public interface SemanticGraphFactory<T>
{
	SemanticGraph create(T contents);
}
