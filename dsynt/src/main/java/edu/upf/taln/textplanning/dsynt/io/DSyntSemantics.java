package edu.upf.taln.textplanning.dsynt.io;

import edu.upf.taln.textplanning.core.io.GraphSemantics;
import edu.upf.taln.textplanning.core.structures.GlobalSemanticGraph;

public class DSyntSemantics implements GraphSemantics
{
	@Override
	public boolean isCore(String role)
	{
		return false;
	}

	@Override
	public boolean isRequired(String v, String source, String target, String role, GlobalSemanticGraph g)
	{
		return false;
	}
}
