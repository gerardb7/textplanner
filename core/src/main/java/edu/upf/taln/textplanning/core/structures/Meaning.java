package edu.upf.taln.textplanning.core.structures;

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
	private String type = ""; // specific NE type
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
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }


	@Override
	public String toString() { return reference + "-" + this.label; }

	@Override
	public int hashCode() { return Objects.hash(reference);	}
}
