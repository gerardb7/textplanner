package edu.upf.taln.textplanning.input;

import java.util.Collections;
import java.util.Set;

/**
 * Provider for single-doc summarization.
 * Immutable class.
 */
public class SingleDocProvider implements DocumentProvider
{
	private final String document;

	public SingleDocProvider(String inDocument)
	{
		this.document = inDocument;
	}

	@Override
	public Set<String> getDocuments(Set<String> inReferences)
	{
		return Collections.singleton(document);
	}

	@Override
	public Set<String> getAllDocuments()
	{
		return Collections.singleton(document);
	}
}
