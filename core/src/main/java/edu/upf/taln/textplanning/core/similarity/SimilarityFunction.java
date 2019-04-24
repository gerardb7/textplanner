package edu.upf.taln.textplanning.core.similarity;

import java.util.OptionalDouble;
import java.util.function.BiFunction;

public interface SimilarityFunction extends BiFunction<String, String, OptionalDouble>
{
	boolean isDefined(String item);
}
