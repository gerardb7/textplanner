package edu.upf.taln.textplanning.corpora;

import java.util.OptionalInt;

/**
 * Interface for classes implementing access to corpora semantically annotated with meanings.
 */
public interface Corpus
{
	OptionalInt getMeaningCount(String meaning);
	OptionalInt getMeaningDocumentCount(String meaning);
	OptionalInt getFormMeaningCount(String form, String meaning);
	OptionalInt getFormCount(String form);
	int getNumDocs();
}