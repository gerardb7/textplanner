package edu.upf.taln.textplanning.core.weighting;

import java.util.function.Function;

// A WeightFunction is a function that returns normalized doubel values for items identified with a String
public interface WeightFunction extends Function<String, Double>
{
	boolean isDefined(String item);
}
