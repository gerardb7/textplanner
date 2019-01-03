package edu.upf.taln.textplanning.common;

import com.ibm.icu.util.ULocale;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface MeaningDictionary
{
	class Info
	{
		public final String id;
		public final List<String> glosses;
		public final List<String> lemmas;

		public Info(String id, List<String> glosses, List<String> lemmas)
		{
			this.id = id;
			this.glosses = glosses;
			this.lemmas = lemmas;
		}

		@Override
		public String toString()
		{
			return id + "-" + String.join("|", lemmas);
		}
	}

	Iterator<String> iterator();
	Iterator<Info> infoIterator(ULocale language);

	List<String> getMeanings(String form, ULocale language);
	List<String> getMeanings(String form, String pos, ULocale language);
	boolean contains(String id);
	Optional<String> getLabel(String id, ULocale language);
	Optional<Boolean> isNE(String id);
	List<String> getGlosses(String id, ULocale language);
	List<String> getLemmas(String id, ULocale language);
}
