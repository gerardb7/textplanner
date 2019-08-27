package edu.upf.taln.textplanning.core.corpus;

import edu.upf.taln.textplanning.core.structures.Mention;

public class TextualOrderAdjacencyFunction extends AdjacencyFunction
{
	private final int context_size;

	public TextualOrderAdjacencyFunction(Corpora.Text text, int context_size)
	{
		super(text);
		this.context_size = context_size;
	}

	@Override
	public boolean test(Mention m1, Mention m2)
	{
		assert m1 != null && m2 != null && !m1.isMultiWord() && !m2.isMultiWord() : m1 + " " + m2;
		if (m1.equals(m2))
			return false;

		// Adjacent words? <- rewards words adjacent to highly ranked words. Side effect ->  penalizes words at the start and end of a sentence!
		return m1.getContextId().equals(m2.getContextId()) &&
				(Math.abs(word_mentions.indexOf(m1) - word_mentions.indexOf(m2)) <= context_size);

	}

	@Override
	public String getLabel(Mention m1, Mention m2)
	{
		return "text";
	}
}
