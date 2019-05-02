package edu.upf.taln.textplanning.core.bias;

import java.util.function.Function;

// A BiasFunction is a function that maps string-based ids to normalized real values
public interface BiasFunction extends Function<String, Double>
{
	boolean isDefined(String item);

	enum Type { Context, Domain }
}
