package edu.upf.taln.textplanning.amr.io;

import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.io.GraphSemantics;

@SuppressWarnings("unused")
public class AMRSemantics implements GraphSemantics
{
	public static final String inverse_suffix = "-of";
	public static final String instance = ":instance";
	public static final String ARG0 = ":ARG0";
	public static final String ARG1 = ":ARG1";
	public static final String ARG2 = ":ARG2";
	public static final String ARG3 = ":ARG3";
	public static final String ARG4 = ":ARG4";
	public static final String ARG5 = ":ARG5";
	public static final String accompanier = ":accompanier";
	public static final String age = ":age";
	public static final String beneficiary = ":beneficiary";
	public static final String concession = ":concession";
	public static final String condition = ":condition";
	public static final String consist = ":consist-of";
	public static final String degree = ":degree";
	public static final String destination = ":destination";
	public static final String direction = ":direction";
	public static final String domain = ":domain";
	public static final String duration = ":duration";
	public static final String example = ":example";
	public static final String extent = ":extent";
	public static final String frequency = ":frequency";
	public static final String instrument = ":instrument";
	public static final String li = ":li";
	public static final String location = ":location";
	public static final String manner = ":manner";
	public static final String medium = ":medium";
	public static final String mod = ":mod";
	public static final String mode = ":mode";
	public static final String name = ":name";
	public static final String ord = ":ord";
	public static final String part = ":part";
	public static final String path = ":path";
	public static final String polarity = ":polarity";
	public static final String polite = ":polite";
	public static final String poss = ":poss";
	public static final String purpose = ":purpose";
	public static final String quant = ":quant";
	public static final String range = ":range";
	public static final String scale = ":scale";
	public static final String source = ":source";
	public static final String subevent = ":subevent";
	public static final String time = ":time";
	public static final String topic = ":topic";
	public static final String unit = ":unit";
	public static final String value = ":value";
	public static final String wiki = ":wiki";
	public static final String calendar = ":calendar";
	public static final String century = ":century";
	public static final String day = ":day";
	public static final String dayperiod = ":dayperiod";
	public static final String decade = ":decade";
	public static final String era = ":era";
	public static final String month = ":month";
	public static final String quarter = ":quarter";
	public static final String season = ":season";
	public static final String timezone = ":timezone";
	public static final String weekday = ":weekday";
	public static final String year = ":year";
	public static final String year2 = ":year2";
	public static final String op = ":op";
	public static final String op1 = ":op1";
	public static final String op2 = ":op2";
	public static final String op3 = ":op3";
	public static final String op4 = ":op4";
	public static final String op5 = ":op5";
	public static final String op6 = ":op6";
	public static final String op7 = ":op7";
	public static final String op8 = ":op8";
	public static final String op9 = ":op9";
	public static final String op10 = ":op10";
	public static final String prep = ":prep-";
	public static final String conj = ":conj-";
	public static final String name_concept = "name";
	public static final String unknown = "io-unknown";
	public static final String choice = "io-choice";
	public static final String product = "product-of";
	public static final String sum = "sum-of";
	public static final String truth = "truth-value";
	public static final String and = "and";
	public static final String or = "or";
	public static final String contrast = "contrast-01";
	public static final String either = "either";
	public static final String neither = "neither";

	public static boolean isName(String label)
	{
		return label.startsWith("\"") && label.endsWith("\""); // names in AMR start and end with quotes
	}

	public boolean isCore(String role)
	{
		switch (role)
		{
			case AMRSemantics.ARG0:
			case AMRSemantics.ARG1:
			case AMRSemantics.ARG2:
			case AMRSemantics.ARG3:
			case AMRSemantics.ARG4:
			case AMRSemantics.ARG5:
			case AMRSemantics.ARG0 + inverse_suffix:
			case AMRSemantics.ARG1 + inverse_suffix:
			case AMRSemantics.ARG2 + inverse_suffix:
			case AMRSemantics.ARG3 + inverse_suffix:
			case AMRSemantics.ARG4 + inverse_suffix:
			case AMRSemantics.ARG5 + inverse_suffix:
			case AMRSemantics.op1:
			case AMRSemantics.op2:
			case AMRSemantics.op3:
			case AMRSemantics.op4:
			case AMRSemantics.op5:
			case AMRSemantics.op6:
			case AMRSemantics.op7:
			case AMRSemantics.op8:
			case AMRSemantics.op9:
			case AMRSemantics.op10:
			case AMRSemantics.op1 + inverse_suffix:
			case AMRSemantics.op2 + inverse_suffix:
			case AMRSemantics.op3 + inverse_suffix:
			case AMRSemantics.op4 + inverse_suffix:
			case AMRSemantics.op5 + inverse_suffix:
			case AMRSemantics.op6 + inverse_suffix:
			case AMRSemantics.op7 + inverse_suffix:
			case AMRSemantics.op8 + inverse_suffix:
			case AMRSemantics.op9 + inverse_suffix:
			case AMRSemantics.op10 + inverse_suffix:
				return true;
			default:
				return false;
		}
	}

	public boolean isRequired(String label, String source, String target, String role, SemanticGraph g)
	{
		boolean source_selected = source.equals(label);
		boolean target_selected = target.equals(label);
		assert (source_selected || target_selected);

		switch (role)
		{
			case AMRSemantics.instance:
				return source_selected; // should not happen if concepts have been removed
			case AMRSemantics.ARG0:
			case AMRSemantics.ARG1:
			case AMRSemantics.ARG2:
			case AMRSemantics.ARG3:
			case AMRSemantics.ARG4:
			case AMRSemantics.ARG5:
				return source_selected; // r1
			case AMRSemantics.ARG0 + inverse_suffix:
			case AMRSemantics.ARG1 + inverse_suffix:
			case AMRSemantics.ARG2 + inverse_suffix:
			case AMRSemantics.ARG3 + inverse_suffix:
			case AMRSemantics.ARG4 + inverse_suffix:
			case AMRSemantics.ARG5 + inverse_suffix:
				return target_selected; // r1-r2
			case AMRSemantics.domain:
				return source_selected; // r3
			case AMRSemantics.mod:
				return target_selected; // r4
			case AMRSemantics.op1:
			case AMRSemantics.op2:
			case AMRSemantics.op3:
			case AMRSemantics.op4:
			case AMRSemantics.op5:
			case AMRSemantics.op6:
			case AMRSemantics.op7:
			case AMRSemantics.op8:
			case AMRSemantics.op9:
			case AMRSemantics.op10:
				return source_selected; // r5
			case AMRSemantics.op1 + inverse_suffix:
			case AMRSemantics.op2 + inverse_suffix:
			case AMRSemantics.op3 + inverse_suffix:
			case AMRSemantics.op4 + inverse_suffix:
			case AMRSemantics.op5 + inverse_suffix:
			case AMRSemantics.op6 + inverse_suffix:
			case AMRSemantics.op7 + inverse_suffix:
			case AMRSemantics.op8 + inverse_suffix:
			case AMRSemantics.op9 + inverse_suffix:
			case AMRSemantics.op10 + inverse_suffix:
				return target_selected; // r6
			case AMRSemantics.polarity:
				return source_selected; // r7
			case AMRSemantics.polarity + inverse_suffix:
				return target_selected; // r8
			case AMRSemantics.mode:
				return source_selected; // r11
			case AMRSemantics.quant:
				return source_selected; // r12
			case AMRSemantics.quant + inverse_suffix:
				return target_selected; // r13
			case AMRSemantics.unit:
			case AMRSemantics.value:
				return source_selected; // r14,r15
			case AMRSemantics.ord:
				return source_selected; // r16
			case AMRSemantics.ord + inverse_suffix:
				return target_selected; // r17
			case AMRSemantics.poss:
				return source_selected; // r18
			case AMRSemantics.poss + inverse_suffix:
				return target_selected; // r19
			case AMRSemantics.calendar:
			case AMRSemantics.century:
			case AMRSemantics.day:
			case AMRSemantics.dayperiod:
			case AMRSemantics.decade:
			case AMRSemantics.era:
			case AMRSemantics.month:
			case AMRSemantics.quarter:
			case AMRSemantics.season:
			case AMRSemantics.timezone:
			case AMRSemantics.weekday:
			case AMRSemantics.year:
			case AMRSemantics.year2:
				return source_selected; // Experimental
			default: // r9-r10
			{
				return source_selected &&
						g.outgoingEdgesOf(target).stream()
								.filter(e2 -> e2.toString().equals(AMRSemantics.instance))
								.map(g::getEdgeTarget)
								.anyMatch(v3 -> v3.equals(AMRSemantics.unknown) || v3.equals(AMRSemantics.choice));
			}
		}
	}

	public static boolean isOpRole(String role)
	{
		return role.equals(op1) || role.equals(op2) || role.equals(op3) || role.equals(op4) || role.equals(op5) ||
				role.equals(op6) || role.equals(op7) || role.equals(op8) || role.equals(op9) || role.equals(op10);
	}
}
