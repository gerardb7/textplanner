package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.pattern.ItemSetMining;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.Linear;
import edu.upf.taln.textplanning.weighting.Position;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates statistics about a document containing annotations of deep syntactic trees.
 */
public class StatsReporter
{
	public static String reportStats(List<SemanticTree> inTrees, WeightingFunction inWeighting,
	                                 EntitySimilarity inSimilarity)
	{
		// Set up formatting
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);

		StringWriter writer = new StringWriter();

		// Report trees
		writer.write("Annotated trees");
		IntStream.range(0, inTrees.size())
				.forEach(i -> writer.write("\tTree " + (i + 1) + ": " + inTrees.get(i)));
		writer.write("\n");

		// Collect entities
		List<Entity> entities = inTrees.stream().map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity))
				.flatMap(Function.identity())
				.distinct()
				.collect(Collectors.toList());

		// Set up metrics
		Map<WeightingFunction, Double> functions = ((Linear) inWeighting).getFunctions();
		Iterator<WeightingFunction> it = functions.keySet().iterator();
		TFIDF corpusMetric = (TFIDF)it.next();
		Position positionMetric = (Position)it.next();

		// Report metrics for each collection
		writer.write("Scoring of entities\n");
		writer.write("num\tlabel\ttfidf\tpos\tlinear\n");
		entities.forEach(e -> {
			double tfIdf = corpusMetric.weight(e);
			double pos = positionMetric.weight(e);
			double linear = inWeighting.weight(e);
			writer.write(entities.indexOf(e) +"\t" + e + "\t" + format.format(tfIdf) + "\t" + format.format(pos) +
							"\t" + format.format(linear) + "\n");
		});
		writer.write("\n");

		// Report similarity
		writer.write("Similarity table\n");
		IntStream.range(0, entities.size())
				.mapToObj(i -> i + "\t" + IntStream.range(0, entities.size())
						.mapToDouble(j -> inSimilarity.computeSimilarity(entities.get(i), entities.get(j)))
						.mapToObj(format::format)
						.collect(Collectors.joining("\t")))
				.forEach(row -> writer.write(row + "\n"));
		writer.write("\n");
		entities.forEach(e -> writer.write(entities.indexOf(e) +"\t" + e + "\t" + inSimilarity.isDefinedFor(e) + "\n"));
		writer.write("\n");

		// Report patterns
		writer.write("Patterns\n");
		ItemSetMining miner = new ItemSetMining();
		Set<SemanticTree> patterns = miner.getPatterns(inTrees);
		patterns.forEach(p -> writer.write(p.toString() + "\n"));
		writer.write("\n");

		return writer.toString();
	}

	public static String getMatrixStats(Matrix inMatrix)
	{
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);

		double max = Arrays.stream(inMatrix.getColumnPackedCopy())
				.filter(d -> d > 0.0 && d < 1.0)
				.max().orElse(0.0);
		double min = Arrays.stream(inMatrix.getColumnPackedCopy())
				.filter(d -> d > 0.0 && d < 1.0)
				.min().orElse(0.0);
		double avg = Arrays.stream(inMatrix.getColumnPackedCopy())
				.filter(d -> d > 0.0 && d < 1.0)
				.average().orElse(0.0);
		double var = Arrays.stream(inMatrix.getColumnPackedCopy())
				.filter(d -> d > 0.0 && d < 1.0)
				.map(d -> Math.pow(d - avg, 2))
				.sum();
		long zeroes = Arrays.stream(inMatrix.getColumnPackedCopy())
				.filter(d -> d == 0.0)
				.count();
		long ones = Arrays.stream(inMatrix.getColumnPackedCopy())
				.filter(d -> d == 1.0)
				.count();
		var /=  (double) inMatrix.getColumnDimension()*inMatrix.getRowDimension();
		double stdDev = Math.sqrt(var);

		return "items=" + inMatrix.getColumnDimension()*inMatrix.getRowDimension() + " zeroes=" + zeroes + " ones=" +
				ones + " max=" + format.format(max) +
				" min=" + format.format(min) + " avg=" + format.format(avg) + " stdev=" + format.format(stdDev);
	}
}
