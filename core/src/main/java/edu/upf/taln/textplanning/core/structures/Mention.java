package edu.upf.taln.textplanning.core.structures;

import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

/**
 * A sequence of one or more consecutive tokens
 */
public final class Mention implements Comparable<Mention>, Serializable
{
	private final int id; // unique identifier
	private final String source_id; // Identifies context in which mention occurs, e.g. sentence, document, etc.
	private final Pair<Integer, Integer> span; // Token-based offsets
	private final String surface_form;
	private final String lemma;
	private final String pos; // POS tag
	private final boolean isNE; // is NE
	private final String type; // e.g. AMR concept label
	private final static long serialVersionUID = 1L;
	private static int id_counter = 0;

	public Mention(String context_id, Pair<Integer, Integer> tokens_span, String surface_form, String lemma,
	                String POS, boolean isNE, String type)
	{
		this.id = id_counter++;
		this.source_id = context_id;
		this.span = tokens_span;
		this.surface_form = surface_form;
		this.lemma = lemma;
		this.pos = POS;
		this.isNE = isNE;
		this.type = type;
	}

	public int getId() { return id;}
	public String getContextId() { return source_id; }
	public Pair<Integer, Integer> getSpan() { return span; }
	public String getSurface_form() { return surface_form; }
	public String getLemma() { return lemma; }
	public String getPOS() { return pos;}
	public boolean isNE() { return isNE; }
	public String getType() { return type; }
	public boolean isNominal() { return pos.startsWith("N"); }
	public boolean isFiniteVerb() {	return pos.startsWith("VB") && !pos.equals("VB"); } // very crude
	public boolean isMultiWord() { return span.getRight() - span.getLeft() > 1; }

	@Override
	public String toString()
	{
		return getContextId() + "_" + getSpan() + "_" + getSurface_form();
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(source_id, span);
	}

	@Override
	public int compareTo( Mention o)
	{
		return Comparator.comparing(Mention::getId).compare(this, o);
	}
}
