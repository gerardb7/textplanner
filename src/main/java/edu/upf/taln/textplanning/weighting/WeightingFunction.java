package edu.upf.taln.textplanning.weighting;


import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;

/**
 * Interface for weighting functions
 */
public interface WeightingFunction
{
	void setContents(Multimap<Meaning, Mention> contents);
	double weight(String item);
}
