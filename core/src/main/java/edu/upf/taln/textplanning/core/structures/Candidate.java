package edu.upf.taln.textplanning.core.structures;

import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper for a pair of a mention and a candidate meaning
 */
public class Candidate implements Serializable
{
	public enum Type {Location, Organization, Person, Other}

	private final Meaning meaning;
	private final Mention mention;
	private final static long serialVersionUID = 1L;

	public Candidate(Meaning e, Mention m)
	{
		this.meaning = e;
		this.mention = m;
	}

	public Meaning getMeaning() { return meaning; }
	public Mention getMention() { return mention; }

	@Override
	public String toString()
	{
		return "s" + mention.getSentenceId() + " " + mention.getSpan() + " " + meaning;
	}

	// Two candidates are the same if they have same vertex and meaning. Mentions are ignored!
	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Candidate candidate = (Candidate) o;
		return Objects.equals(meaning, candidate.meaning) && Objects.equals(mention, candidate.mention);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(meaning, mention);
	}
}
