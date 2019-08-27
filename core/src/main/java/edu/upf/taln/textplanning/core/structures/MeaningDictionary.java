package edu.upf.taln.textplanning.core.structures;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public interface MeaningDictionary
{
	Iterator<String> meaning_iterator();
	Set<String> getMeanings(ULocale language);
	// List of meanings sorted according to dictionary criteria, e.g. frequency, best sense first, etc.
	List<String> getMeanings(String form, ULocale language);
	List<String> getMeanings(String form, POS.Tag pos, ULocale language);

	boolean contains(String id);
	Optional<String> getLabel(String id, ULocale language);
	Optional<Boolean> isNE(String id);
	List<String> getGlosses(String id, ULocale language);

	Iterator<Pair<String, POS.Tag>> lexicon_iterator();
	Set<Pair<String, POS.Tag>> getLexicalizations(ULocale language);
	// List of lexicalizations sorted according to dictionary criteria
	List<Pair<String, POS.Tag>> getLexicalizations(String id);
	List<Pair<String, POS.Tag>> getLexicalizations(String id, ULocale language);
}

