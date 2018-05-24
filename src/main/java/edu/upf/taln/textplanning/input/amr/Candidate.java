package edu.upf.taln.textplanning.input.amr;

import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;

import java.io.Serializable;
import java.util.Objects;

/**
 * Wrapper for a pair of a mention and a candidate meaning
 */
public class Candidate implements Serializable
{
	public enum Type {Location, Organization, Person, Other}

	private final String vertex;
	private final Meaning meaning;
	private final Mention mention;
	private final static long serialVersionUID = 1L;

	public Candidate(String v, Meaning e, Mention m)
	{
		this.vertex = v;
		this.meaning = e;
		this.mention = m;
	}

	public String getVertex() { return vertex; }
	public Meaning getMeaning() { return meaning; }
	public Mention getMention() { return mention; }

	@Override
	public String toString()
	{
		return vertex + "-" + meaning;
	}

	// Two candidates are the same if they have same vertex and meaning. Mentions are ignored!
	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Candidate candidate = (Candidate) o;
		return Objects.equals(vertex, candidate.vertex) &&
				Objects.equals(meaning, candidate.meaning);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(vertex, meaning);
	}
}
