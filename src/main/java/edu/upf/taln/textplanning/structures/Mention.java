package edu.upf.taln.textplanning.structures;

import edu.upf.taln.textplanning.structures.Candidate.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class encapsulates single or multiword mentions to some entity, and its entity type.
 */
public final class Mention
{
	private final LinguisticStructure s;
	private final List<AnnotatedWord> tokens = new ArrayList<>();
	private final int head;
	private final List<String> surfaceForm;
	private final Pair<Long, Long> offsets;
	private Mention coref = null; // Coreferring mention (representative mention in a coreference chain)

	public Mention(LinguisticStructure s, List<AnnotatedWord> tokens, int head)
	{
		this.s = s;
		this.tokens.addAll(tokens);
		this.head = head;
		surfaceForm = tokens.stream()
				.map(AnnotatedWord::getForm)
				.collect(Collectors.toList());
		offsets = Pair.of(tokens.get(0).getOffsetStart(), tokens.get(tokens.size()-1).getOffsetEnd());
	}

	public LinguisticStructure getStructure() { return s; }
	public List<AnnotatedWord> getTokens() { return new ArrayList<>(tokens);}
	public int getNumTokens() { return tokens.size();}
	public AnnotatedWord getHead() { return this.tokens.get(head); }
	public String getSurfaceForm() { return surfaceForm.stream().collect(Collectors.joining(" ")); }
	public Pair<Long, Long> getOffsets() { return offsets; }
	public Type getType() { return getHead().getType(); } // the NE type of a mention is that of its head
	public Optional<Mention> getCoref() { return Optional.ofNullable(this.coref); }
	public void setCoref(Mention m) { coref = m; }

	// this mention contains another mention o its surface form contains the other mention surface form (and is larger)
	public boolean contains(Mention o)
	{
		return  this.getNumTokens() > o.getNumTokens() &&
				Collections.indexOfSubList(this.surfaceForm, o.surfaceForm) != -1;
	}

	public boolean corefers(Mention m)
	{
		return  (coref != null && coref == m) || // m is the representative mention for this
				(m.coref != null && (m.coref == this || m.coref == this.coref)); // or they both have the same representative mention
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
