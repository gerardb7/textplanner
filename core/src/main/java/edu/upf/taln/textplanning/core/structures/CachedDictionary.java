package edu.upf.taln.textplanning.core.structures;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper around a base dictionary, stores results of calls to base methods into a space-efficient cache
 */
public class CachedDictionary implements MeaningDictionary
{
	private final MeaningDictionary base;
	private final CompactDictionary cache;

	public CachedDictionary(MeaningDictionary base, CompactDictionary cache)
	{
		this.base = base;
		this.cache = cache;
	}

	@Override
	public Iterator<String> iterator()
	{
		return base.iterator();
	}

	@Override
	public Iterator<Info> infoIterator(ULocale language)
	{
		return base.infoIterator(language);
	}

	@Override
	public List<String> getMeanings(String form, ULocale language)
	{
		// cache is only available if pos is specified
		return base.getMeanings(form, language);
	}

	@Override
	public List<String> getMeanings(String word, POS.Tag pos, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
			return base.getMeanings(word, pos, language);

		final Character tag = POS.toTag.get(pos);
		if (cache.contains(word, tag))
			return cache.getMeanings(word, tag);
		else
		{
			final List<String> meanings = base.getMeanings(word, pos, language);
			cache.addForm(word, tag, meanings);
			return meanings;
		}
	}

	@Override
	public boolean contains(String id)
	{
		return cache.contains(id) || base.contains(id);
	}

	@Override
	public Optional<String> getLabel(String id, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
			return base.getLabel(id, language);

		if (cache.contains(id))
			return cache.getLabel(id);
		else
		{
			final Optional<String> label = base.getLabel(id, language);
			final List<String> glosses = base.getGlosses(id, language);
			cache.addMeaning(id, label.orElse(""), glosses);
			return label;
		}
	}

	@Override
	public Optional<Boolean> isNE(String id)
	{
		return base.isNE(id);
	}

	@Override
	public List<String> getGlosses(String id, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
			return base.getGlosses(id, language);

		if (cache.contains(id))
			return cache.getGlosses(id);
		else
		{
			final Optional<String> label = base.getLabel(id, language);
			final List<String> glosses = base.getGlosses(id, language);
			cache.addMeaning(id, label.orElse(""), glosses);
			return glosses;
		}
	}

	@Override
	public List<String> getLemmas(String id, ULocale language)
	{
		return base.getLemmas(id, language);
	}

	@Override
	public long getNumQueries()
	{
		return 0;
	}
}
