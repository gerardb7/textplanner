package edu.upf.taln.textplanning.core.dictionaries;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.stream.Stream;

public interface MeaningDictionary
{
	Stream<String> getMeaningsStream();
	Stream<String> getMeaningsStream(ULocale language);
	Set<String> getMeanings(ULocale language);
	// List of meanings sorted according to dictionary criteria, e.g. frequency, best sense first, etc.
	List<String> getMeanings(String form, ULocale language);
	List<String> getMeanings(String form, POS.Tag pos, ULocale language);

	boolean contains(String id);
	Optional<String> getLabel(String id, ULocale language);
	Optional<Boolean> isNE(String id);
	List<String> getGlosses(String id, ULocale language);

	Stream<Triple<String, POS.Tag, ULocale>> getLexicalizationsStream();
	Stream<Pair<String, POS.Tag>> getLexicalizationsStream(ULocale language);
	Set<Pair<String, POS.Tag>> getLexicalizations(ULocale language);
	// List of lexicalizations sorted according to dictionary criteria
	List<Pair<String, POS.Tag>> getLexicalizations(String id);
	List<Pair<String, POS.Tag>> getLexicalizations(String id, ULocale language);
}

