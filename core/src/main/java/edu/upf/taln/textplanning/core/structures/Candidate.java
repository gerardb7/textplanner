package edu.upf.taln.textplanning.core.structures;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Wrapper for a pair of a mention and a candidate meaning
 */
public class Candidate implements Serializable
{
	public enum Type {Location, Organization, Person, Other}

	private final Mention mention;
	private final Meaning meaning;
	private Double weight = null; // weight can be set only once!
	private final static long serialVersionUID = 1L;

	public Candidate(Mention m, Meaning e)
	{
		this.mention = m;
		this.meaning = e;
	}

	public Mention getMention() { return mention; }
	public Meaning getMeaning() { return meaning; }

	public Optional<Double> getWeight()
	{
		return weight == null ? Optional.empty() : Optional.of(weight);
	}

	public void setWeight(double weight)
	{
		if (this.weight != null)
			throw new RuntimeException("Cannot set weight to candidate more than once");

		this.weight = weight;
	}


	@Override
	public String toString()
	{
		return mention.getSurfaceForm() + "-" + meaning;
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
