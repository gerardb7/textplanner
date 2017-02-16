package edu.upf.taln.textplanning.input;

import java.util.Set;

/**
 * Interface for classes providing the text planner with documents to summarize
 */
public interface DocumentProvider
{
	/**
	 * Given a set of references, return a set of documents relevant to the references
	 *
	 * @param inReferences references, e.g. BabelNet synsets
	 * @return documents relevant to the references
	 */
	Set<String> getDocuments(Set<String> inReferences);

	/**
	 * Returns all documents available to the provider
	 *
	 * @return
	 */
	Set<String> getAllDocuments();
}
