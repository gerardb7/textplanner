package edu.upf.taln.textplanning.core.structures;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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
	private final boolean update_cache;

	public CachedDictionary(MeaningDictionary base, CompactDictionary cache, boolean update_cache)
	{
		this.base = base;
		this.cache = cache;
		this.update_cache = update_cache;
	}

	public CachedDictionary(CompactDictionary cache)
	{
		this.base = null;
		this.cache = cache;
		this.update_cache = false; // can't update cache if no base dictionary
	}

	@Override
	public Iterator<String> iterator()
	{
		if (base == null)
			throw new RuntimeException("Unsupported operation");
		return base.iterator();
	}

	@Override
	public List<String> getMeanings(String form, ULocale language)
	{
		if (base == null)
			throw new RuntimeException("Unsupported operation");
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
				throw new RuntimeException("Unsupported language");
		}

		final Character tag = POS.toTag.get(pos);
		if (cache.contains(word, tag))
			return cache.getMeanings(word, tag);
		else if (base != null)
		{
			final List<String> meanings = base.getMeanings(word, pos, language);
			if (update_cache)
				cache.addForm(word, POS.toTag.get(pos), meanings);
			return meanings;
		}
		else
			return new ArrayList<>();
	}

	@Override
	public boolean contains(String id)
	{
		if (base == null)
			throw new RuntimeException("Unsupported operation");
		return cache.contains(id);
	}

	@Override
	public Optional<String> getLabel(String id, ULocale language)
	{
		if (!cache.getLanguage().equals(language))
		{
			if (base != null)
				return base.getLabel(id, language);
			else
				throw new RuntimeException("Unsupported language");
		}

		if (cache.contains(id))
			return cache.getLabel(id);
		else if (base != null)
		{
			final Optional<String> label = base.getLabel(id, language);
			final List<String> glosses = base.getGlosses(id, language);
			if (update_cache)
			{
				// determine label value
				final String label_str;
				if (label.isPresent() && StringUtils.isNotBlank(label.get()))
					label_str = label.get();
				else
				{
					final List<String> lemmas = base.getLemmas(id, language);
					// determine label value
					if (!lemmas.isEmpty())
						label_str = lemmas.get(0);
					else
						label_str = null;
				}

				cache.addMeaning(id, label_str, glosses);
			}
			return label;
		}
		else
			throw new RuntimeException("Cannot find " + id);
	}

	@Override
	public Optional<Boolean> isNE(String id)
	{
		if (base == null)
			throw new RuntimeException("Unsupported operation");
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
				throw new RuntimeException("Unsupported language");
		}

		if (cache.contains(id))
			return cache.getGlosses(id);
		else if (base != null)
		{
			final Optional<String> label = base.getLabel(id, language);
			final List<String> glosses = base.getGlosses(id, language);
			if (update_cache)
				cache.addMeaning(id, label.orElse(""), glosses);
			return glosses;
		}
		else
			throw new RuntimeException("Cannot find " + id);
	}

	@Override
	public List<String> getLemmas(String id, ULocale language)
	{
		if (base == null)
			throw new RuntimeException("Unsupported operation");
		return base.getLemmas(id, language);
	}
}
