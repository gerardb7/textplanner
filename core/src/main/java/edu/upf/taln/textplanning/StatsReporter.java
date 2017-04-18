package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.weighting.Position;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates statistics about a document containing annotations of deep syntactic trees.
 */
public class StatsReporter
{
	public static String reportStats(List<SemanticTree> inTrees, WeightingFunction inWeighting,
	                                 EntitySimilarity inSimilarity, Map<Entity, Double> rankedEntities)
	{
		// Set up formatting
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);

		StringWriter writer = new StringWriter();

		// Report trees
//		writer.write("Annotated trees");
//		IntStream.range(0, inTrees.size())
//				.forEach(i -> writer.write("\tTree " + (i + 1) + ": " + inTrees.get(i)));
//		writer.write("\n");

		// Collect entities
		List<Entity> entities = inTrees.stream().map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity))
				.flatMap(Function.identity())
				.distinct()
				.collect(Collectors.toList());

		List<Double> simValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> inSimilarity.computeSimilarity(e1, e2))
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());

		List<Double> simRelValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> inSimilarity.computeSimilarity(e1, e2) * inWeighting.weight(e2))
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());

		// Set up metrics
//		Map<WeightingFunction, Double> functions = ((Linear) inWeighting).getFunctions();
//		Iterator<WeightingFunction> it = functions.keySet().iterator();
		TFIDF corpusMetric = (TFIDF)inWeighting;
		Position positionMetric = new Position();
		positionMetric.setCollection(inTrees);

		Map<String, Long> freqs = inTrees.stream()
				.map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity)
						.map(Entity::getEntityLabel)
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Report metrics for each collection
		writer.write("Scoring of entities\n");
		writer.write("num\tlabel\ttfidf\tpos\tlinear\trank\tf\tdf\tsim\tsimrel\n");
		entities.forEach(e -> {
			double tfIdf = corpusMetric.weight(e);
			double pos = positionMetric.weight(e);
			double linear = inWeighting.weight(e);
			double rank = rankedEntities.get(e);
			long f = freqs.get(e.getEntityLabel());
			long df = corpusMetric.corpus.getFrequency(e);
			double sim = simValues.get(entities.indexOf(e));
			double simrel = simRelValues.get(entities.indexOf(e));

			writer.write(entities.indexOf(e) +"\t" + e + "\t" + format.format(tfIdf) + "\t" + format.format(pos) +
					"\t" + format.format(linear) + "\t" + format.format(rank) + "\t" + f + "\t" + df + "\t" +
					format.format(sim) + "\t" + format.format(simrel) + "\n");
		});
		writer.write("\n");

		// Report similarity
/*		writer.write("Similarity table\n");
		IntStream.range(0, entities.size())
				.mapToObj(i -> i + "\t" + IntStream.range(0, entities.size())
						.mapToDouble(j -> inSimilarity.computeSimilarity(entities.get(i), entities.get(j)))
						.mapToObj(format::format)
						.collect(Collectors.joining("\t")))
				.forEach(row -> writer.write(row + "\n"));
		writer.write("\n");*/

		long numSenses = entities.stream()
				.filter(e -> ((AnnotatedEntity)e).getAnnotation().getSense() != null)
				.count();
		long numDefinedSEW = entities.stream()
				.filter(e -> ((AnnotatedEntity)e).getAnnotation().getSense() != null)
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.count();
		long numDefinedSenseEmbed = entities.stream()
				.filter(e -> ((AnnotatedEntity)e).getAnnotation().getSense() != null)
				.filter(inSimilarity::isDefinedFor)
				.count();
		writer.write(entities.size() + " entities " + numSenses + " senses " + numDefinedSEW + " in SEW " + numDefinedSenseEmbed + " in SenseEmbed\n");
		//entities.forEach(e -> writer.write(entities.indexOf(e) +"\t" + e + "\t" + inSimilarity.isDefinedFor(e) + "\n"));
		writer.write("\n");

		// Report patterns
//		writer.write("Patterns\n");
//		ItemSetMining miner = new ItemSetMining();
//		Set<SemanticTree> patterns = miner.getPatterns(inTrees);
//		patterns.forEach(p -> writer.write(p.toString() + "\n"));
//		writer.write("\n");

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
