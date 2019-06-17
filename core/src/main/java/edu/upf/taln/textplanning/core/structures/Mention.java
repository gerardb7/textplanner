package edu.upf.taln.textplanning.core.structures;

import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * A sequence of one or more consecutive tokens
 */
public final class Mention implements Comparable<Mention>, Serializable
{
	private final int id; // automatically generated unique identifier
	private final String context_id; // identifier from context, e.g. annotation id
	private final String source_id; // Identifies context in which mention occurs, e.g. sentence, document, etc.
	private final Pair<Integer, Integer> span; // Token-based offsets
	private final String surface_form;
	private final String lemma;
	private final POS.Tag pos; // POS tag
	private final boolean isNE; // is NE
	private final String type; // e.g. AMR concept label
	private Double weight = null;
	private final static long serialVersionUID = 1L;
	private static int id_counter = 0;

	public Mention(String context_id, String source_id, Pair<Integer, Integer> tokens_span, String surface_form, String lemma,
	               POS.Tag POS, boolean isNE, String type)
	{
		this.id = id_counter++;
		this.context_id = context_id;
		this.source_id = source_id;
		this.span = tokens_span;
		this.surface_form = surface_form;
		this.lemma = lemma;
		this.pos = POS;
		this.isNE = isNE;
		this.type = type;
	}

	public int getId() { return id;}
	public String getContextId() { return context_id; }
	public String getSourceId() { return source_id; }
	public Pair<Integer, Integer> getSpan() { return span; }
	public String getSurface_form() { return surface_form; }
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

	@Override
	public String toString()
	{
		return getId() + "_" + getContextId() + "_" + getSpan() + "_" + getSurface_form();
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}

	@Override
	public int compareTo(Mention o)
	{
		return Comparator.comparing(Mention::getId).compare(this, o);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return this.id == ((Mention)o).id;
	}
}
