package edu.upf.taln.textplanning.core.structures;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public interface MeaningDictionary
{
	class Info implements Serializable
	{
		public final String id;
		public final String label;
		public final POS.Tag POS;
		public final List<String> glosses;
		public final List<String> forms;
		private static final long serialVersionUID = 1L;

		public Info(String id, String label, POS.Tag POS, List<String> glosses, List<String> forms)
		{
			this.id = id;
			this.label = label;
			this.POS = POS;
			this.glosses = glosses;
			this.forms = forms;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	Iterator<String> iterator();
	Iterator<Info> infoIterator(ULocale language);

	// List of meanings sorted according to dictionary criteria, e.g. frequency, best sense first, etc.
	List<String> getMeanings(String form, ULocale language);
	List<String> getMeanings(String form, POS.Tag pos, ULocale language);
	boolean contains(String id);
	Optional<String> getLabel(String id, ULocale language);
	Optional<Boolean> isNE(String id);
	List<String> getGlosses(String id, ULocale language);
	List<String> getLemmas(String id, ULocale language);
	long getNumQueries();
}
