package edu.upf.taln.textplanning.core.structures;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;

public interface SemanticGraphFactory<T>
{
	SemanticGraph create(T contents);
}
