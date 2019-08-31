package edu.upf.taln.textplanning.core.dictionaries;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

/**
 * Uses space-efficient cache. Calls not supported by the cache are (optionally) diverted to a base dictionary
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

	public CachedDictionary(CompactDictionary cache)
	{
		this.base = null;
		this.cache = cache;
	}

	@Override
	public Iterator<String> meaning_iterator()
	{
		if (base == null)
			return Collections.emptyIterator();
		return base.meaning_iterator();
	}

	@Override
	public Set<String> getMeanings(ULocale language)
	{
		if (base == null)
			return Collections.emptySet();
		return base.getMeanings(language);
	}

	@Override
	public List<String> getMeanings(String form, ULocale language)
	{
		if (base == null)
			return Collections.emptyList();
		return base.getMeanings(form, language);
	}

	@Override
	public List<String> getMeanings(String word, POS.Tag pos, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
		{
			if (base != null)
				return base.getMeanings(word, pos, language);
			else
				return Collections.emptyList();
		}

		final Character tag = POS.toTag.get(pos);
		if (cache.contains(word, tag))
			return cache.getMeanings(word, tag);
		else if (base != null)
			return base.getMeanings(word, pos, language);
		else
			return Collections.emptyList();
	}

	@Override
	public boolean contains(String id)
	{
		return cache.contains(id) || (base != null && base.contains(id));
	}

	@Override
	public Optional<String> getLabel(String id, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
		{
			if (base != null)
				return base.getLabel(id, language);
			else
				return Optional.empty();
		}

		if (cache.contains(id))
			return cache.getLabel(id);
		else if (base != null)
			return base.getLabel(id, language);
		else
			return Optional.empty();
	}

	@Override
	public Optional<Boolean> isNE(String id)
	{
		if (base == null)
			return Optional.empty();
		return base.isNE(id);
	}

	@Override
	public List<String> getGlosses(String id, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
		{
			if (base != null)
				return base.getGlosses(id, language);
			else
				return Collections.emptyList();
		}

		if (cache.contains(id))
			return cache.getGlosses(id);
		else if (base != null)
			return base.getGlosses(id, language);
		else
			return Collections.emptyList();
	}

	@Override
	public Iterator<Triple<String, POS.Tag, ULocale>> lexicon_iterator()
	{
		if (base == null)
			return Collections.emptyIterator();
		else
			return base.lexicon_iterator();
	}

	@Override
	public Set<Pair<String, POS.Tag>> getLexicalizations(ULocale language)
	{
		if (base == null)
			return Collections.emptySet();
		return base.getLexicalizations(language);
	}

	@Override
	public List<Pair<String, POS.Tag>> getLexicalizations(String id)
	{
		if (base == null)
			return Collections.emptyList();
		return base.getLexicalizations(id);
	}

	@Override
	public List<Pair<String, POS.Tag>> getLexicalizations(String id, ULocale language)
	{
		if (base == null)
			return Collections.emptyList();
		return base.getLexicalizations(id, language);
	}


}
