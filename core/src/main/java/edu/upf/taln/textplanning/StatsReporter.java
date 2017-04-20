package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.similarity.Combined;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;
import edu.upf.taln.textplanning.weighting.Position;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.collections4.SetUtils;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

		SensEmbed ssim = null;
		Word2Vec wsim = null;
		if (inSimilarity instanceof SensEmbed)
		{
			ssim = ((SensEmbed) inSimilarity);
		}
		else if (inSimilarity instanceof Word2Vec)
		{
			wsim = ((Word2Vec) inSimilarity);
		}
		else if (inSimilarity instanceof Combined)
		{
			for (EntitySimilarity sim : ((Combined)inSimilarity).functions)
			{
				if (sim instanceof SensEmbed)
				{
					ssim = ((SensEmbed) sim);
				}
				else if (sim instanceof Word2Vec)
				{
					wsim = ((Word2Vec) sim);
				}
			}
		}

		Set<AnnotatedEntity> senses = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() != null)
				.collect(Collectors.toSet());
		Set<AnnotatedEntity> nominalSenses = senses.stream()
				.filter(e -> e.getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toSet());
		Set<AnnotatedEntity> allForms = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toSet());
		Set<AnnotatedEntity> forms = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() == null)
				.collect(Collectors.toSet());
		Set<AnnotatedEntity> nonNominalSenseforms = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() == null || e.getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toSet());
		{
			double ratio = ((double) senses.size() / (double) entities.size()) * 100.0;
			writer.write("Babelfy: " + format.format(ratio) + "% of entities have a sense\n");
		}
		{
			double ratio = ((double) nominalSenses.size() / (double) senses.size()) * 100.0;
			writer.write("Babelfy: " + format.format(ratio) + "% of senses are nominal\n");
		}

		Set<AnnotatedEntity> sensesInSEW = senses.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) sensesInSEW.size() / (double) senses.size()) * 100.0;
			writer.write("SEW: " + format.format(ratio) + "% senses defined (" + sensesInSEW.size()
					+ "/" + senses.size() + ")\n");
		}
		Set<AnnotatedEntity> nominalSensesInSEW = nominalSenses.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) nominalSensesInSEW.size() / (double) nominalSenses.size()) * 100.0;
			writer.write("SEW: " + format.format(ratio) + "% nominal senses defined (" + nominalSensesInSEW.size()
					+ "/" + nominalSenses.size() + ")\n");
		}
		Set<AnnotatedEntity>formsInSEW = nonNominalSenseforms.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) formsInSEW.size() / (double) nonNominalSenseforms.size()) * 100.0;
			writer.write("SEW: " + format.format(ratio) + "% forms (of words not annotated with nominal senses) defined ("
					+ formsInSEW.size()	+ "/" + nonNominalSenseforms.size() + ")\n");
		}

		if (ssim != null)
		{
			{
				Set<AnnotatedEntity> definedSenses = senses.stream()
						.filter(ssim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedSenses.size() / (double) senses.size()) * 100.0;
				writer.write("SensEmbed: " + format.format(ratio) + "% senses defined (" + definedSenses.size() + "/" +
						senses.size() + ")\n");
				writer.write("SensEmbed: undefined senses " + SetUtils.difference(senses, definedSenses).stream()
						.map(AnnotatedEntity::toString)
						.collect(Collectors.joining(",")) + "\n");
			}
			{
				Set<AnnotatedEntity> definedForms = nominalSenses.stream()
						.filter(ssim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) nominalSenses.size()) * 100.0;
				writer.write("SensEmbed: " + format.format(ratio) + "% nominal senses defined (" + definedForms.size() +
						"/" + senses.size() + ")\n");
			}
		}
		if (wsim != null)
		{
			{
				Set<AnnotatedEntity> definedForms = allForms.stream()
						.filter(wsim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) allForms.size()) * 100.0;
				writer.write("Word2Vec: " + format.format(ratio) + "% forms defined ("
						+ definedForms.size() + "/" + allForms.size() + ")\n");
			}
			{
				Set<AnnotatedEntity> definedForms = forms.stream()
						.filter(wsim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) forms.size()) * 100.0;
				writer.write("Word2Vec: " + format.format(ratio) + "% forms (of words not annotated with a sense) defined ("
						+ definedForms.size() + "/" + forms.size() + ")\n");

				writer.write("Word2Vec: undefined forms " + SetUtils.difference(forms, definedForms).stream()
						.map(AnnotatedEntity::getAnnotation)
						.map(Annotation::getForm)
						.collect(Collectors.joining(",")) + "\n");
			}
		}

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
