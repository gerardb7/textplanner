package edu.upf.taln.textplanning.core.structures;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public interface MeaningDictionary
{
	Iterator<String> iterator();
	// List of meanings sorted according to dictionary criteria, e.g. frequency, best sense first, etc.
	List<String> getMeanings(String form, ULocale language);
	List<String> getMeanings(String form, POS.Tag pos, ULocale language);
	boolean contains(String id);
	Optional<String> getLabel(String id, ULocale language);
	Optional<Boolean> isNE(String id);
	List<String> getGlosses(String id, ULocale language);
	List<String> getLemmas(String id, ULocale language);
}

