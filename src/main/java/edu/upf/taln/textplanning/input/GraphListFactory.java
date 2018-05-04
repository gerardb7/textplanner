package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.CoreferenceChain;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.structures.SemanticGraph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class GraphListFactory
{
	private final DocumentReader reader;
	private final StanfordWrapper stanford = new StanfordWrapper();
	private final CandidatesCollector candidate_collector;
	private final TypesCollector types_collector;

	public GraphListFactory(DocumentReader reader, Path types_file) throws IOException
	{
		this.reader = reader;
		BabelNetWrapper babelnet = new BabelNetWrapper();
		this.candidate_collector = new CandidatesCollector(babelnet);
		this.types_collector = (types_file != null) ? new TypesCollector(types_file, babelnet) : null;
	}

	public GraphList getGraphs(String graph_bank)
	{
		// Read graphs from file
		List<SemanticGraph> graphs = reader.read(graph_bank);

		// Make variable ids unique across graphs
		for (int i=0; i<graphs.size(); ++i)
		{
			SemanticGraph g = graphs.get(i);
				String suffix = "_" + i;
				List<String> nodes_to_rename = g.vertexSet().stream()
						.filter(v -> g.outgoingEdgesOf(v).stream().anyMatch(e -> e.toString().equals(AMRConstants.instance)))
						.collect(toList());
				nodes_to_rename.forEach(v -> g.renameVertex(v, v + suffix));
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
