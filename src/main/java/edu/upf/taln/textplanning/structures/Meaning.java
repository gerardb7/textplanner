package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.input.amr.Candidate.Type;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A meaning can be a word sense, a reference to a real world entity, or some datum in a database
 */
public final class Meaning implements Serializable
{
	private final String reference; // should be unique
	private final String label; // human-readable label
	private final boolean is_NE; // refers to the meaning itself (e.g. synset for Chicago is a NE)
	private Type type = Type.Other; // specific NE type
	private double weight = 0.0; // used when ranking meaning for disambiguation
	private static final Map<String, Meaning> references = new HashMap<>(); // to ensure unique Meaning objects per id
	private final static long serialVersionUID = 1L;


	// Factory method
	public static Meaning get(String reference, String label, boolean is_NE)
	{
		if (references.containsKey(reference))
			return references.get(reference);

		Meaning e = new Meaning(reference, label, is_NE);
		references.put(reference, e);
		return e;
	}

	// Constructor, kept private
	private Meaning(String reference, String label, boolean is_NE)
	{
		this.reference = reference;
		this.label = label;
		this.is_NE = is_NE;
	}

	public String getReference() { return reference; }
	public boolean isNE() { return is_NE; }
	public Type getType() { return type; }
	public void setType(Type type) { this.type = type; }
	public double getWeight() { return weight; }
	public void setWeight(double weight) { this.weight = weight; }

	@Override
	public String toString() { return reference + "-" + this.label; }

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Meaning meaning = (Meaning) o;
		return Objects.equals(reference, meaning.reference);
	}

	@Override
	public int hashCode() { return Objects.hash(reference);	}
}
