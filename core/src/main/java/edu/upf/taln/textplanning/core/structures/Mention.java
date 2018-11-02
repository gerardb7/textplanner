package edu.upf.taln.textplanning.core.structures;

import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.Objects;

/**
 * A sequence of one or more consecutive tokens
 */
public final class Mention implements Serializable
{
	private final String sentence_id; // Identifies sentence and, if necessary, document
	private final Pair<Integer, Integer> span; // Token-based offsets
	private final String surface_form;
	private final String lemma;
	private final String pos; // POS tag
	private final boolean isNE; // is NE
	private final String type; // e.g. AMR concept label
	private final static long serialVersionUID = 1L;

	public Mention(String sentence_id, Pair<Integer, Integer> tokens_span, String surface_form, String lemma,
	               String POS, boolean isNE, String type)
	{
		this.sentence_id = sentence_id;
		this.span = tokens_span;
		this.surface_form = surface_form;
		this.lemma = lemma;
		this.pos = POS;
		this.isNE = isNE;
		this.type = type;
	}

	public String getSentenceId() { return sentence_id; }
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
		return getSurface_form();
	}

	public String toLongString()
	{
		return "Mention{" +
				"sentence_id='" + sentence_id + '\'' +
				", span=" + span +
				", surface_form='" + surface_form + '\'' +
				", lemma='" + lemma + '\'' +
				", pos='" + pos + '\'' +
				", type=" + type +
				'}';
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		Mention mention = (Mention) o;
		return sentence_id.equals(mention.sentence_id) && span.equals(mention.span);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(sentence_id, span);
	}
}
