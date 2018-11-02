package edu.upf.taln.amrplanning.input;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.amrplanning.structures.AMRSemantics;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.amrplanning.structures.CoreferenceChain;
import edu.upf.taln.amrplanning.structures.GraphList;
import edu.upf.taln.amrplanning.structures.SemanticGraph;
import edu.upf.taln.textplanning.structures.Mention;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;

// Reads an AMR Bank into a set of graph objects, which are then decorated with NER, Coreference and BabelNet annotations.
public class GraphListFactory
{
	private final DocumentReader reader;
	private final StanfordWrapper stanford;
	private final CandidatesCollector candidate_collector;
	private final TypesCollector types_collector;
	private final static Logger log = LogManager.getLogger();

	public GraphListFactory(DocumentReader reader, Path types_file, Path bn_config_folder, boolean no_stanford,
	                        boolean no_babelnet) throws IOException
	{
		this.reader = reader;
		stanford = new StanfordWrapper(no_stanford);
		BabelNetWrapper babelnet = new BabelNetWrapper(bn_config_folder, no_babelnet);
		this.candidate_collector = new CandidatesCollector(babelnet);
		this.types_collector = (types_file != null) ? new TypesCollector(types_file, babelnet) : null;
	}

	public GraphList getGraphs(String graph_bank)
	{
		log.info("***Creating semantic graphs***");
		Stopwatch timer = Stopwatch.createStarted();

		// Read graphs from file
		List<SemanticGraph> graphs = reader.read(graph_bank);

		// Make variable ids unique across graphs
		for (SemanticGraph g : graphs)
		{
			String prefix = g.getSource() + "_";
			List<String> nodes_to_rename = g.vertexSet().stream()
					.filter(v -> g.outgoingEdgesOf(v).stream().anyMatch(e -> e.toString().equals(AMRSemantics.instance)))
					.collect(toList());
			nodes_to_rename.forEach(v -> g.renameVertex(v, prefix + v));
		}

		// Process with Stanford
		List<CoreferenceChain> chains = stanford.process(graphs);

		// Collect and classify mentions
		final Multimap<String, Mention> mentions = MentionsCollector.collectMentions(graphs);
		final Multimap<String, Mention> singlewords = HashMultimap.create();
		mentions.entries().stream().filter(e -> !e.getValue().isMultiWord()).forEach(e -> singlewords.put(e.getKey(), e.getValue()));
		final Multimap<String, Mention> multiwords = HashMultimap.create();
		mentions.entries().stream().filter(e -> e.getValue().isMultiWord()).forEach(e -> multiwords.put(e.getKey(), e.getValue()));
		final Multimap<String, Mention> nominal_words = HashMultimap.create();
		singlewords.entries().stream().filter(e -> e.getValue().isNominal()).forEach(e -> nominal_words.put(e.getKey(), e.getValue()));

		// Collect candidates for mentions. Current behaviour is to lookup nouns and multiwords only
		final Set<Mention> mentions_to_lookup = new HashSet<>(multiwords.values());
		mentions_to_lookup.addAll(nominal_words.values());
		List<Candidate> candidate_meanings = candidate_collector.getCandidateMeanings(mentions_to_lookup);

		// Assign types to candidates
		if (types_collector != null)
			types_collector.getMeaningTypes(candidate_meanings);

		final GraphList graph_list = new GraphList(graphs, mentions, candidate_meanings, chains);
		log.info("Semantic graphs created in " + timer.stop());
		return graph_list;
	}
}
