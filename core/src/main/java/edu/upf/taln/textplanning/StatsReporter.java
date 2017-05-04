package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Annotation;
import edu.upf.taln.textplanning.datastructures.Entity;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.collections4.ListUtils;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates statistics about a document containing annotations of deep syntactic trees.
 */
public class StatsReporter
{
	public static String reportStats(List<SemanticTree> t, WeightingFunction rel,
	                                 EntitySimilarity sim, Map<Entity, Double> rankedEntities,
	                                 TextPlanner.Options o)
	{
		// Set up formatting
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		f.setMaximumFractionDigits(3);
		f.setMinimumFractionDigits(3);
		StringWriter w = new StringWriter();

		// Prepare similarity functions
		final SensEmbed sense;
		final SensEmbed merged;
		final Word2Vec word;
		if (sim instanceof SensEmbed)
		{

			SensEmbed s = ((SensEmbed) sim);
			if (s.isMerged())
			{
				merged = s;
				sense = null;
			}
			else
			{
				merged = null;
				sense = s;
			}
			word = null;
		}
		else if (sim instanceof Word2Vec)
		{
			sense = null;
			merged = null;
			word = ((Word2Vec) sim);
		}
		else
		{
			sense = null;
			merged = null;
			word = null;
		}

		// Collect entities, entitiesWithSense, entitiesWithoutSense, etc.
		List<AnnotatedEntity> entities = t.stream().map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity))
				.flatMap(Function.identity())
				.distinct()
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toList());
		List<AnnotatedEntity> entitiesWithSense = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() != null)
				.collect(Collectors.toList());
		List<AnnotatedEntity> entitiesWithNominalSense = entitiesWithSense.stream()
				.filter(e -> e.getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toList());
		List<AnnotatedEntity> entitiesWithoutSense = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() == null)
				.collect(Collectors.toList());
		List<AnnotatedEntity> entitiesWithNonNominalSense = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() == null || e.getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toList());

		// Collect similarity values
		List<Double> senseValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> sense != null ? sense.computeSimilarity(e1, e2) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> mergedValues = entitiesWithSense.stream()
				.mapToDouble(e1 -> entitiesWithSense.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> merged != null ? merged.computeSimilarity(e1, e2) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> wordValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> word != null ? word.computeSimilarity(e1, e2) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());

		// Collect similarity values weighted by relevance
		List<Double> senseRelValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = sense != null ? sense.computeSimilarity(e1, e2) : 0.0;
							double r = rel.weight(e2);
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> mergedRelValues = entitiesWithSense.stream()
				.mapToDouble(e1 -> entitiesWithSense.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = merged != null ? merged.computeSimilarity(e1, e2) : 0.0;
							double r = rel.weight(e2);
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> wordRelValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = word != null ? word.computeSimilarity(e1, e2) : 0.0;
							double r = rel.weight(e2);
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());

		// Set up metrics
		TFIDF corpusMetric = (TFIDF)rel;

		Map<String, Long> freqs = t.stream()
				.map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity)
						.map(Entity::getEntityLabel)
						.collect(Collectors.toList()))
				.flatMap(List::stream)
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		// Report metrics for each collection
		w.write("Scoring of entities\n");
		w.write("num\tlabel\ttfidf\trank\tf\tdf\tsense\tmerged\tword\tsenserel\tmergedrel\twordrel\n");
		entities.forEach(e -> {
			double tfIdf = corpusMetric.weight(e);
			double rank = rankedEntities.get(e);
			long fq = freqs.get(e.getEntityLabel());
			long df = corpusMetric.corpus.getFrequency(e);
			double ss = entities.contains(e) ? senseValues.get(entities.indexOf(e)) : -1.0;
			double ms = entitiesWithSense.contains(e) ? mergedValues.get(entitiesWithSense.indexOf(e)) : -1.0;
			double ws = entities.contains(e) ? wordValues.get(entities.indexOf(e)) : -1.0;
			double ssrel = entities.contains(e) ? senseRelValues.get(entities.indexOf(e)) : -1.0;
			double msrel = entitiesWithSense.contains(e) ? mergedRelValues.get(entitiesWithSense.indexOf(e)) : -1.0;
			double wsrel = entities.contains(e) ? wordRelValues.get(entities.indexOf(e)) : -1.0;

			w.write(entities.indexOf(e) +"\t" + e + "\t" + f.format(tfIdf) + "\t" + f.format(rank) + "\t" + fq + "\t" + df + "\t" +
					f.format(ss) + "\t" + f.format(ms) + "\t" + f.format(ws) + "\t" +
					f.format(ssrel) + "\t" + f.format(msrel) + "\t" + f.format(wsrel) + "\n");
		});
		w.write("\n");

		{
			double ratio = ((double) entitiesWithSense.size() / (double) entities.size()) * 100.0;
			w.write("Babelfy: " + f.format(ratio) + "% of entities have a sense\n");
		}
		{
			double ratio = ((double) entitiesWithNominalSense.size() / (double) entitiesWithSense.size()) * 100.0;
			w.write("Babelfy: " + f.format(ratio) + "% of senses are nominal\n");
		}

		Set<AnnotatedEntity> sensesInSEW = entitiesWithSense.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) sensesInSEW.size() / (double) entitiesWithSense.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% senses defined (" + sensesInSEW.size()
					+ "/" + entitiesWithSense.size() + ")\n");
		}
		Set<AnnotatedEntity> nominalSensesInSEW = entitiesWithNominalSense.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) nominalSensesInSEW.size() / (double) entitiesWithNominalSense.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% nominal senses defined (" + nominalSensesInSEW.size()
					+ "/" + entitiesWithNominalSense.size() + ")\n");
		}
		Set<AnnotatedEntity>formsInSEW = entitiesWithNonNominalSense.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) formsInSEW.size() / (double) entitiesWithNonNominalSense.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% forms (of words not annotated with nominal senses) defined ("
					+ formsInSEW.size()	+ "/" + entitiesWithNonNominalSense.size() + ")\n");
		}

		if (sense != null)
		{
			{
				Set<AnnotatedEntity> definedEntities = entities.stream()
						.filter(sense::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedEntities.size() / (double) entities.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% entities defined (" + definedEntities.size() + "/" +
						entities.size() + ")\n");
				w.write("sense: undefined entities " + ListUtils.removeAll(entitiesWithSense, definedEntities).stream()
						.map(AnnotatedEntity::toString)
						.collect(Collectors.joining(",")) + "\n");
			}
			{
				Set<AnnotatedEntity> definedSenses = entitiesWithSense.stream()
						.filter(sense::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedSenses.size() / (double) entitiesWithSense.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% senses defined (" + definedSenses.size() +
						"/" + entitiesWithSense.size() + ")\n");
			}
			{
				Set<AnnotatedEntity> definedForms = entitiesWithoutSense.stream()
						.filter(sense::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) entitiesWithoutSense.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% forms of entities without sense defined (" + definedForms.size() +
						"/" + entitiesWithoutSense.size() + ")\n");
			}
		}

		if (merged != null)
		{
			{
				Set<AnnotatedEntity> definedSenses = entitiesWithSense.stream()
						.filter(merged::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedSenses.size() / (double) entitiesWithSense.size()) * 100.0;
				w.write("merged: " + f.format(ratio) + "% senses defined (" + definedSenses.size() + "/" +
						entitiesWithSense.size() + ")\n");
				w.write("merged: undefined senses " + ListUtils.removeAll(entitiesWithSense, definedSenses).stream()
						.map(AnnotatedEntity::toString)
						.collect(Collectors.joining(",")) + "\n");
			}
			{
				Set<AnnotatedEntity> definedForms = entitiesWithNominalSense.stream()
						.filter(merged::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) entitiesWithNominalSense.size()) * 100.0;
				w.write("merged: " + f.format(ratio) + "% nominal senses defined (" + definedForms.size() +
						"/" + entitiesWithNominalSense.size() + ")\n");
			}
		}

		if (word != null)
		{
			{
				Set<AnnotatedEntity> definedForms = entities.stream()
						.filter(word::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) entities.size()) * 100.0;
				w.write("word: " + f.format(ratio) + "% forms defined ("
						+ definedForms.size() + "/" + entities.size() + ")\n");
			}
			{
				Set<AnnotatedEntity> definedForms = entitiesWithoutSense.stream()
						.filter(word::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) entitiesWithoutSense.size()) * 100.0;
				w.write("word: " + f.format(ratio) + "% forms of entitites with no sense defined ("
						+ definedForms.size() + "/" + entitiesWithoutSense.size() + ")\n");

				w.write("word: undefined forms " + ListUtils.removeAll(entitiesWithoutSense, definedForms).stream()
						.map(AnnotatedEntity::getAnnotation)
						.map(Annotation::getForm)
						.collect(Collectors.joining(",")) + "\n");
			}
		}

		// Report similarity
		w.write("\nSimilarity table\n");
		IntStream.range(0, entities.size())
				.mapToObj(i -> i + "\t" + entities.stream()
						.mapToDouble(entity -> sim.computeSimilarity(entities.get(i), entity))
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.mapToObj(f::format)
						.collect(Collectors.joining("\t")))
				.forEach(row -> w.write(row + "\n"));
		w.write("\n");
		return w.toString();
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
