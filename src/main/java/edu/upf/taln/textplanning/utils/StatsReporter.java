package edu.upf.taln.textplanning.utils;

import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.structures.Meaning;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.input.amr.Candidate;
import edu.upf.taln.textplanning.input.amr.GraphList;
import edu.upf.taln.textplanning.input.amr.SemanticGraph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class StatsReporter
{
	private final static Logger log = LogManager.getLogger(GraphListFactory.class);

	public static void reportStats(GraphList graphs, Map<String, List<Candidate>> candidates)
	{
		int numVertices = graphs.getGraphs().stream()
				.map(SemanticGraph::vertexSet)
				.mapToInt(Set::size)
				.sum();
		Set<String> alignedVertices = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> g.getAlignments().getAlignment(v).isPresent()))
				.collect(toSet());
		Set<String> nominalVertices = graphs.getGraphs().stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> g.getAlignments().getAlignment(v).isPresent())
						.filter(v -> g.getAlignments().getPOS(g.getAlignments().getAlignment(v).get()).startsWith("N")))
				.collect(toSet());

		long numForms = alignedVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.map(Mention::getSurface_form)
				.distinct()
				.count();
		long numNominalForms = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.map(Mention::getSurface_form)
				.distinct()
				.count();
		long numMentions = candidates.values().stream()
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.distinct()
				.count();
		long numNominalMentions = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.distinct()
				.count();
		long numCandidates = candidates.values().stream()
				.mapToLong(List::size)
				.sum();
		long numNominalCandidates = nominalVertices.stream()
				.map(candidates::get)
				.mapToLong(List::size)
				.sum();
		long numMeanings = candidates.values().stream()
				.flatMap(List::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.count();
		long numNominalMeanings = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.count();
		double candidatesPerNode = candidates.values().stream()
				.mapToLong(List::size)
				.average()
				.orElse(0.0);
		double candidatesPerNominalNode = nominalVertices.stream()
				.map(candidates::get)
				.mapToLong(List::size)
				.average()
				.orElse(0.0);

//		double avgMentionsPerOtherLabel = avgMentionsPerLabel - avgMentionsPerNominalLabel;

		// Set up formatting
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(2);
		f.setMinimumFractionDigits(2);

		log.info(numVertices + " vertices, " + alignedVertices.size() + " aligned (" + nominalVertices.size() + " nominal)" );
		log.info(numForms + " forms (" + numNominalForms + " nominal) ");
		log.info(numMentions + " mentions (" + numNominalMentions + " nominal) ");
		log.info(numCandidates + " candidates (" + numNominalCandidates + " nominal) ");
		log.info(numMeanings + " meanings (" + numNominalMeanings + " nominal) ");
		log.info(f.format(candidatesPerNode) + " candidates/node (" + f.format(candidatesPerNominalNode) + " nominal) ");
	}
}
