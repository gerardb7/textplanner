package edu.upf.taln.textplanning.core.structures;

import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.*;

/**
 * A sequence of one or more consecutive tokens
 */
public final class Mention implements Comparable<Mention>, Serializable
{
	private final String id; // externally provided id
	private final String context_id; // Identifies context in which mention occurs, e.g. sentence, document, etc.
	private final Pair<Integer, Integer> span; // Token-based offsets at document-level
	private final String lemma;
	private final List<String> tokens;
	private final POS.Tag pos; // POS tag
	private final boolean isNE; // is NE
	private final String type; // e.g. AMR concept label
	private Double weight = null;
	private final static long serialVersionUID = 1L;

	public Mention(String id, String context_id, Pair<Integer, Integer> tokens_span, List<String> tokens, String lemma,
	               POS.Tag POS, boolean isNE, String type)
	{
		this.id = id;
		this.context_id = context_id;
		this.span = tokens_span;
		this.tokens = tokens;
		this.lemma = lemma;
		this.pos = POS;
		this.isNE = isNE;
		this.type = type;
	}

	public String getId() { return id; }
	public String getContextId() { return context_id; }
	public Pair<Integer, Integer> getSpan() { return span; }
	public List<String> getTokens() { return tokens; }
	public String getSurfaceForm() { return String.join(" ", tokens); }
	public String getLemma() { return lemma; }
	public POS.Tag getPOS() { return pos;}
	public boolean isNE() { return isNE; }
	public String getType() { return type; }

	public Optional<Double> getWeight()
	{
		return weight == null ? Optional.empty() : Optional.of(weight);
	}

	public void setWeight(double weight)
	{
		if (this.weight != null)
			throw new RuntimeException("Cannot set weight to mention more than once");

		this.weight = weight;
	}

	public boolean isNominal() { return pos == POS.Tag.NOUN; }
	public boolean isVerbal() {	return pos == POS.Tag.VERB; }
	public boolean isMultiWord() { return span.getRight() - span.getLeft() > 1; }
	public int numTokens() {  return span.getRight() - span.getLeft(); }

	@Override
	public String toString()
	{
		return getContextId() + "_" + getSpan() + "_" + getSurfaceForm();
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(context_id) + Objects.hash(getSpan());
	}

	@Override
	public int compareTo(@Nonnull Mention o)
	{
		return Comparator.comparing(Mention::getContextId)
				.thenComparingInt(m -> m.getSpan().getLeft())
				.thenComparingInt(m -> m.getSpan().getRight())
				.compare(this, o);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Mention other = ((Mention)o);
		return this.getContextId().equals(other.getContextId()) && this.getSpan().equals(other.getSpan());
	}
}
