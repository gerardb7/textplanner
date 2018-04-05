package edu.upf.taln.textplanning.utils;

import edu.upf.taln.textplanning.input.GraphAlignments;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.structures.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class StatsReporter
{
	private final static Logger log = LoggerFactory.getLogger(GraphListFactory.class);

	public static void reportStats(GraphList graphs, Map<String, List<Candidate>> candidates)
	{
		int numVertices = graphs.stream()
				.map(SemanticGraph::vertexSet)
				.mapToInt(Set::size)
				.sum();
		Set<String> alignedVertices = graphs.stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> g.getAlignments().getAlignment(v) != GraphAlignments.unaligned))
				.collect(toSet());
		Set<String> nominalVertices = graphs.stream()
				.flatMap(g -> g.vertexSet().stream()
						.filter(v -> g.getAlignments().getAlignment(v) != GraphAlignments.unaligned)
						.filter(v -> g.getAlignments().getPOS(g.getAlignments().getAlignment(v)).startsWith("N")))
				.collect(toSet());

		long numForms = alignedVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.map(Mention::getSurfaceForm)
				.distinct()
				.count();
		long numNominalForms = nominalVertices.stream()
				.map(candidates::get)
				.flatMap(List::stream)
				.map(Candidate::getMention)
				.map(Mention::getSurfaceForm)
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
