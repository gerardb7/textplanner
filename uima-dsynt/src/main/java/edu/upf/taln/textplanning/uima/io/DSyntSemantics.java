package edu.upf.taln.textplanning.uima.io;

import edu.upf.taln.textplanning.core.io.GraphSemantics;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;

public class DSyntSemantics implements GraphSemantics
{
	public static final String inverse_suffix = "INV";
	public static final String ARG0 = "A0";
	public static final String ARG1 = "A1";
	public static final String ARG2 = "A2";
	public static final String ARG3 = "A3";
	public static final String ARG4 = "A4";
	public static final String ARG5 = "A5";
	public static final String ARG6 = "A6";
	public static final String ARG7 = "A7";
	public static final String ARG8 = "A8";
	public static final String ARG9 = "A9";
	public static final String ARG10 = "A10";

	@Override
	public boolean isCore(String role)
	{
		switch (role)
		{
			case ARG0:
			case ARG1:
			case ARG2:
			case ARG3:
			case ARG4:
			case ARG5:
			case ARG6:
			case ARG7:
			case ARG8:
			case ARG9:
			case ARG10:
			case ARG0 + inverse_suffix:
			case ARG1 + inverse_suffix:
			case ARG2 + inverse_suffix:
			case ARG3 + inverse_suffix:
			case ARG4 + inverse_suffix:
			case ARG5 + inverse_suffix:
			case ARG6 + inverse_suffix:
			case ARG7 + inverse_suffix:
			case ARG8 + inverse_suffix:
			case ARG9 + inverse_suffix:
			case ARG10 + inverse_suffix:
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean isRequired(String v, String source, String target, String role, SemanticGraph g)
	{
		boolean source_selected = source.equals(v);
		boolean target_selected = target.equals(v);
		assert (source_selected || target_selected);

		switch (role)
		{
			case ARG0:
			case ARG1:
			case ARG2:
			case ARG3:
			case ARG4:
			case ARG5:
				return source_selected; // r1
			case ARG0 + inverse_suffix:
			case ARG1 + inverse_suffix:
			case ARG2 + inverse_suffix:
			case ARG3 + inverse_suffix:
			case ARG4 + inverse_suffix:
			case ARG5 + inverse_suffix:
				return target_selected; // r1-r2
			default:
				return false;
		}
	}
}
