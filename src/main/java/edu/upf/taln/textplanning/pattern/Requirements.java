package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.input.AMRConstants;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;

import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class Requirements
{
	public static Set<String> determine(GlobalSemanticGraph g, String v)
	{
		Set<String> S = new HashSet<>(); // set of semantically required nodes
		Set<String> nodes = new HashSet<>();
		nodes.add(v);
		do
		{
			nodes = nodes.stream()
					.map(n -> g.outgoingEdgesOf(n).stream()
							.filter(e -> isRequired(g, v, g.getEdgeTarget(e), e))
							.map(g::getEdgeTarget)
							.collect(toSet()))
					.flatMap(Set::stream)
					.peek(S::add)
					.collect(toSet());
		}
		while (!nodes.isEmpty());

		return S;
	}

	private static boolean isRequired(GlobalSemanticGraph g, String v1, String v2, String e)
	{
		switch(e)
		{
			case AMRConstants.instance:
				return true; // Experimental
			case AMRConstants.ARG0:
			case AMRConstants.ARG1:
			case AMRConstants.ARG2:
			case AMRConstants.ARG3:
			case AMRConstants.ARG4:
			case AMRConstants.ARG5:
				return true; // r1-r2
			case AMRConstants.domain:
				return true; // r3-r4
			case AMRConstants.op1:
			case AMRConstants.op2:
			case AMRConstants.op3:
			case AMRConstants.op4:
			case AMRConstants.op5:
			case AMRConstants.op6:
			case AMRConstants.op7:
			case AMRConstants.op8:
			case AMRConstants.op9:
			case AMRConstants.op10:
				return true; // // r5-r6
			case AMRConstants.polarity:
				return true; // r7-r8
			case AMRConstants.mode:
				return true; // r11
			case AMRConstants.quant:
			case AMRConstants.unit:
			case AMRConstants.value:
				return true; // r12-r15
			case AMRConstants.ord:
				return true; // r16-r17
			case AMRConstants.poss:
				return true; // r18-r19
			case AMRConstants.calendar:
			case AMRConstants.century:
			case AMRConstants.day:
			case AMRConstants.dayperiod:
			case AMRConstants.decade:
			case AMRConstants.era:
			case AMRConstants.month:
			case AMRConstants.quarter:
			case AMRConstants.season:
			case AMRConstants.timezone:
			case AMRConstants.weekday:
			case AMRConstants.year:
			case AMRConstants.year2:
				return true; // Experimental
			default:
			{
				return (g.outgoingEdgesOf(v2).stream()
						.filter(e2 -> e2.equals(AMRConstants.instance))
						.map(g::getEdgeTarget)
						.anyMatch(v3 -> v3.equals(AMRConstants.unknown) || v3.equals(AMRConstants.choice))); // r9-r10
			}
		}
	}
}
