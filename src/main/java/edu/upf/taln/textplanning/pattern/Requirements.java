package edu.upf.taln.textplanning.pattern;

import edu.upf.taln.textplanning.input.AMRConstants;
import edu.upf.taln.textplanning.structures.GlobalSemanticGraph;
import edu.upf.taln.textplanning.structures.Role;

import java.util.HashSet;
import java.util.Set;

import static edu.upf.taln.textplanning.input.AMRConstants.inverse_suffix;
import static java.util.stream.Collectors.toSet;

class Requirements
{
	static Set<String> determine(GlobalSemanticGraph g, String v)
	{
		Set<String> S = new HashSet<>(); // set of semantically required nodes
		Set<String> nodes = new HashSet<>();
		nodes.add(v);
		do
		{
			nodes = nodes.stream()
					.map(n -> g.edgesOf(n).stream()
							.filter(e -> isRequired(g, n, e))
							.map(g::getEdgeTarget)
							.collect(toSet()))
					.flatMap(Set::stream)
					.peek(S::add)
					.collect(toSet());
		}
		while (!nodes.isEmpty());

		return S;
	}

	private static boolean isRequired(GlobalSemanticGraph g, String selected_node, Role e)
	{
		String source = g.getEdgeSource(e);
		String target = g.getEdgeTarget(e);
		boolean source_selected = source.equals(selected_node);
		boolean target_selected = target.equals(selected_node);

		switch(e.toString())
		{
			case AMRConstants.instance:
				return source_selected; // Experimental
			case AMRConstants.ARG0:
			case AMRConstants.ARG1:
			case AMRConstants.ARG2:
			case AMRConstants.ARG3:
			case AMRConstants.ARG4:
			case AMRConstants.ARG5:
				return source_selected; // r1
			case AMRConstants.ARG0 + inverse_suffix:
			case AMRConstants.ARG1 + inverse_suffix:
			case AMRConstants.ARG2 + inverse_suffix:
			case AMRConstants.ARG3 + inverse_suffix:
			case AMRConstants.ARG4 + inverse_suffix:
			case AMRConstants.ARG5 + inverse_suffix:
				return target_selected; // r1-r2
			case AMRConstants.domain:
				return source_selected; // r3
			case AMRConstants.mod:
				return target_selected; // r4
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
				return source_selected; // r5
			case AMRConstants.op1 + inverse_suffix:
			case AMRConstants.op2 + inverse_suffix:
			case AMRConstants.op3 + inverse_suffix:
			case AMRConstants.op4 + inverse_suffix:
			case AMRConstants.op5 + inverse_suffix:
			case AMRConstants.op6 + inverse_suffix:
			case AMRConstants.op7 + inverse_suffix:
			case AMRConstants.op8 + inverse_suffix:
			case AMRConstants.op9 + inverse_suffix:
			case AMRConstants.op10 + inverse_suffix:
				return target_selected; // r6
			case AMRConstants.polarity:
				return source_selected; // r7
			case AMRConstants.polarity + inverse_suffix:
				return target_selected; // r8
			case AMRConstants.mode:
				return source_selected; // r11
			case AMRConstants.quant:
				return source_selected; // r12
			case AMRConstants.quant + inverse_suffix:
				return target_selected; // r13
			case AMRConstants.unit:
			case AMRConstants.value:
				return source_selected; // r14,r15
			case AMRConstants.ord:
				return source_selected; // r16
			case AMRConstants.ord + inverse_suffix:
				return target_selected; // r17
			case AMRConstants.poss:
				return source_selected; // r18
			case AMRConstants.poss + inverse_suffix:
				return target_selected; // r19
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
				return source_selected; // Experimental
			default: // r9-r10
			{
				return  source_selected &&
						g.outgoingEdgesOf(target).stream()
								.filter(e2 -> e2.toString().equals(AMRConstants.instance))
								.map(g::getEdgeTarget)
								.anyMatch(v3 -> v3.equals(AMRConstants.unknown) || v3.equals(AMRConstants.choice));
			}
		}
	}
}
