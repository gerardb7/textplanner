package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.CoreferenceChain;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.util.stream.Collectors.toList;

// Reads an AMR Bank into a set of graph objects, which are then decorated with NER, Coreference and BabelNet annotations.
public class GraphListFactory
{
	private final DocumentReader reader;
	private final StanfordWrapper stanford = new StanfordWrapper();
	private final CandidatesCollector candidate_collector;
	private final TypesCollector types_collector;

	public GraphListFactory(DocumentReader reader, Path types_file, Path bn_config_folder) throws IOException
	{
		this.reader = reader;
		BabelNetWrapper babelnet = new BabelNetWrapper(bn_config_folder);
		this.candidate_collector = new CandidatesCollector(babelnet);
		this.types_collector = (types_file != null) ? new TypesCollector(types_file, babelnet) : null;
	}

	public GraphList getGraphs(String graph_bank)
	{
		// Read graphs from file
		List<SemanticGraph> graphs = reader.read(graph_bank);

		// Make variable ids unique across graphs
		for (SemanticGraph g : graphs)
		{
			String prefix = g.getId() + "_";
			List<String> nodes_to_rename = g.vertexSet().stream()
					.filter(v -> g.outgoingEdgesOf(v).stream().anyMatch(e -> e.toString().equals(AMRConstants.instance)))
					.collect(toList());
			nodes_to_rename.forEach(v -> g.renameVertex(v, prefix + v));
		}

		// Process with Stanford
		List<CoreferenceChain> chains = stanford.process(graphs);

		// Create dictionary of vertices to candidate meanings (assumes unique vertex labels)
		List<Candidate> candidate_meanings = candidate_collector.getCandidateMeanings(graphs);

		// Assign types to candidates
		if (types_collector != null)
			types_collector.getMeaningTypes(candidate_meanings);

		return new GraphList(graphs, candidate_meanings, chains);
	}
}
