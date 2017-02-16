package edu.upf.taln.textplanning.corpora;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Combines multiple corpora semantically annotated with references to entities.
 * Immutable class.
 */
public class CorporaCounts implements CorpusCounts
{
	private final List<CorpusCounts> corpora = new ArrayList<>();

	public CorporaCounts(Collection<CorpusCounts> inCorpora)
	{
		corpora.addAll(inCorpora);
	}

	@Override
	public Counts getCounts(String inEntity1, String inEntity2, String inDomain)
	{
		Counts counts = null;
		for (CorpusCounts c : corpora)
		{
			Counts corpusCounts = c.getCounts(inEntity1, inEntity2, inDomain);
			if (counts == null)
			{
				counts = new Counts(corpusCounts);
			}
			else
			{
				counts.freq += corpusCounts.freq;
				counts.cooccur += corpusCounts.cooccur;
			}
		}

		return counts;
	}

	@Override
	public Counts getCounts(String inEntity1, String inEntity2, String inDomain, int inDistance)
	{
		Counts counts = null;
		for (CorpusCounts c : corpora)
		{
			Counts corpusCounts = c.getCounts(inEntity1, inEntity2, inDomain, inDistance);
			if (counts == null)
			{
				counts = new Counts(corpusCounts);
			}
			counts.freq += corpusCounts.freq;
			counts.cooccur += corpusCounts.cooccur;
		}

		return counts;
	}

	@Override
	public Counts getOrderedCounts(String inEntity1, String inEntity2, String inDomain, int inDistance)
	{
		Counts counts = null;
		for (CorpusCounts c : corpora)
		{
			Counts corpusCounts = c.getOrderedCounts(inEntity1, inEntity2, inDomain, inDistance);
			if (counts == null)
			{
				counts = new Counts(corpusCounts);
			}
			counts.freq += corpusCounts.freq;
			counts.cooccur += corpusCounts.cooccur;
		}

		return counts;
	}
}
