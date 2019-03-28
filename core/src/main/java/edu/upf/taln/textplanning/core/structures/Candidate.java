package edu.upf.taln.textplanning.core.structures;

import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper for a pair of a mention and a candidate meaning
 */
public class Candidate implements Serializable
{
	public enum Type {Location, Organization, Person, Other}

	private final Mention mention;
	private final Meaning meaning;
	private double weight = 0.0;
	private final static long serialVersionUID = 1L;

	public Candidate(Mention m, Meaning e)
	{
		this.mention = m;
		this.meaning = e;
	}

	public Mention getMention() { return mention; }
	public Meaning getMeaning() { return meaning; }

	public double getWeight()
	{
		return weight;
	}

	public void setWeight(double weight)
	{
		this.weight = weight;
	}


	@Override
	public String toString()
	{
		return mention.getId() + "-" + meaning;
	}

	// Two candidates are the same if they have same vertex and meaning. Mentions are ignored!
	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Candidate candidate = (Candidate) o;
		return Objects.equals(mention, candidate.mention) && Objects.equals(meaning, candidate.meaning);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(mention, meaning);
	}
}
