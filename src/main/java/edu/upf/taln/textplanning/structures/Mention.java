package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.structures.Candidate.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class encapsulates single or multiword mentions to some entity, and its entity type.
 */
public final class Mention
{
	private final LinguisticStructure s;
	private final List<AnnotatedWord> tokens = new ArrayList<>();
	private final int head;
	private final Type type; // NE type
	private final List<String> surfaceForm;

	public Mention(LinguisticStructure s, List<AnnotatedWord> tokens, int head, Type type)
	{
		this.s = s;
		this.tokens.addAll(tokens);
		this.head = head;
		this.type = type;
		surfaceForm = tokens.stream()
				.map(AnnotatedWord::getForm)
				.collect(Collectors.toList());
	}

	public LinguisticStructure getStructure() { return s; }
	public List<AnnotatedWord> getTokens() { return new ArrayList<>(tokens);}
	public int getNumTokens() { return tokens.size();}

	public AnnotatedWord getHead() { return this.tokens.get(head); }

	public String getSurfaceForm() { return surfaceForm.stream().collect(Collectors.joining(" ")); }

	public Type getType()
	{
		return type;
	}

	// this mention contains another mention o its surface form contains the other mention surface form (and is larger)
	public boolean contains(Mention o)
	{
		return this.surfaceForm.size() > o.surfaceForm.size() && Collections.indexOfSubList(this.surfaceForm, o.surfaceForm) != -1;
	}

	@Override
	public String toString()
	{
		return getSurfaceForm();
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
		return head == mention.head && s.equals(mention.s) && tokens.equals(mention.tokens);
	}

	@Override
	public int hashCode()
	{
		int result = s.hashCode();
		result = 31 * result + tokens.hashCode();
		result = 31 * result + head;
		return result;
	}
}
