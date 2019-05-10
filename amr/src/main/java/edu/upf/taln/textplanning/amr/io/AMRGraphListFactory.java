package edu.upf.taln.textplanning.amr.io;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.amr.structures.AMRGraph;
import edu.upf.taln.textplanning.amr.structures.AMRGraphList;
import edu.upf.taln.textplanning.amr.structures.CoreferenceChain;
import edu.upf.taln.textplanning.core.io.CandidatesCollector;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.MeaningDictionary;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

// Reads an AMR Bank into a set of graph objects, which are then decorated with NER, Coreference and BabelNet annotations.
public class AMRGraphListFactory
{
	private final AMRReader reader;
	private final ULocale language;
	private final StanfordWrapper stanford;
	private final MeaningDictionary dictionary;
	private final TypesCollector types_collector;
	private final static Logger log = LogManager.getLogger();

	public AMRGraphListFactory(AMRReader reader, ULocale language, Path types_file, MeaningDictionary dictionary,
	                           boolean no_stanford) throws IOException
	{
		this.reader = reader;
		this.language = language;
		this.dictionary = dictionary;
		stanford = new StanfordWrapper(no_stanford);
		this.types_collector = (types_file != null) ? new TypesCollector(types_file, dictionary) : null;
	}

	public AMRGraphList create(String graph_bank)
	{
		Stopwatch timer = Stopwatch.createStarted();
		log.info("*Creating semantic graphs*");

		// Read graphs from file
		List<AMRGraph> graphs = reader.read(graph_bank);

		// Make variable ids unique across graphs
		for (AMRGraph g : graphs)
		{
			String prefix = g.getContextId() + "_";
			List<String> nodes_to_rename = g.vertexSet().stream()
					.filter(v -> g.outgoingEdgesOf(v).stream().anyMatch(e -> e.toString().equals(AMRSemantics.instance)))
					.collect(toList());
			nodes_to_rename.forEach(v -> g.renameVertex(v, prefix + v));
		}

		// Process with Stanford
		List<CoreferenceChain> chains = stanford.process(graphs);

		// Collect and classify mentions
		final Multimap<String, Mention> mentions = AMRMentionsCollector.collectMentions(graphs, language);
		final Multimap<String, Mention> singlewords = HashMultimap.create();
		mentions.entries().stream().filter(e -> !e.getValue().isMultiWord()).forEach(e -> singlewords.put(e.getKey(), e.getValue()));
		final Multimap<String, Mention> multiwords = HashMultimap.create();
		mentions.entries().stream().filter(e -> e.getValue().isMultiWord()).forEach(e -> multiwords.put(e.getKey(), e.getValue()));
		final Multimap<String, Mention> nominal_words = HashMultimap.create();
		singlewords.entries().stream().filter(e -> e.getValue().isNominal()).forEach(e -> nominal_words.put(e.getKey(), e.getValue()));

		// Collect candidates for mentions. Current behaviour is to lookup nouns and multiwords only
		final List<Mention> mentions_to_lookup = new ArrayList<>(multiwords.values());
		mentions_to_lookup.addAll(nominal_words.values());
		Map<Mention, List<Candidate>> mentions2candidates = CandidatesCollector.collect(dictionary, language, mentions_to_lookup);
		final List<Candidate> candidates = mentions2candidates.values().stream()
				.flatMap(List::stream)
				.collect(toList());

		// Assign types to candidates
		if (types_collector != null)
			types_collector.getMeaningTypes(candidates);

		final AMRGraphList graph_list = new AMRGraphList(graphs, mentions, candidates, chains);
		log.info("Semantic graphs created in " + timer.stop());
		return graph_list;
	}
}
