package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.input.amr.Candidate.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.Objects;

/**
 * A sequence of consecutive tokens spanned over by an AMR node.
 */
public final class Mention implements Serializable
{
	private final String sentence_id; // Identifies sentence and, if necessary, document
	private final Pair<Integer, Integer> span; // Token-based offsets
	private final String surface_form;
	private final String lemma;
	private final String pos; // POS tag
	private final Type type; // NE type
	private final static long serialVersionUID = 1L;

	public Mention(String sentence_id, Pair<Integer, Integer> tokens_span, String surface_form, String lemma,
	               String POS, Type type)
	{
		this.sentence_id = sentence_id;
		this.span = tokens_span;
		this.surface_form = surface_form;
		this.lemma = lemma;
		this.pos = POS;
		this.type = type;
	}

	public String getSentenceId() { return sentence_id; }
	public Pair<Integer, Integer> getSpan() { return span; }
	public String getSurface_form() { return surface_form; }
	public String getLemma() { return lemma; }
	public String getPOS() { return pos;}
	public Type getType() { return type; }
	public boolean isNominal() { return pos.startsWith("N"); }
	public boolean isMultiWord() { return span.getRight() - span.getLeft() > 1; }

	@Override
	public String toString()
	{
		return getSurface_form();
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
