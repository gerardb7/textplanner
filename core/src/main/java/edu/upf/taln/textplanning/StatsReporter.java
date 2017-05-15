package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.datastructures.*;
import edu.upf.taln.textplanning.datastructures.SemanticGraph.Node;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbed;
import edu.upf.taln.textplanning.similarity.Word2Vec;
import edu.upf.taln.textplanning.weighting.TFIDF;
import edu.upf.taln.textplanning.weighting.WeightingFunction;
import org.apache.commons.collections4.ListUtils;

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
	public static String reportStats(List<SemanticTree> t, WeightingFunction rel,
	                                 EntitySimilarity sim, SemanticGraph g,
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

		// Collect nodes, nodesWithSense, nodesWithoutSense, etc.
		List<Node> nodes = new ArrayList<>(g.vertexSet());
		List<Node> nodesWithSense = nodes.stream()
				.filter(n -> ((AnnotatedEntity)n.getEntity()).getAnnotation().getSense() != null)
				.collect(Collectors.toList());
		List<Node> nodesWithNominalSense = nodesWithSense.stream()
				.filter(n -> ((AnnotatedEntity)n.getEntity()).getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toList());
		List<Node> nodesWithoutSense = nodes.stream()
				.filter(n -> ((AnnotatedEntity)n.getEntity()).getAnnotation().getSense() == null)
				.collect(Collectors.toList());
		List<Node> nodesWithNonNominalSense = nodes.stream()
				.filter(n -> ((AnnotatedEntity)n.getEntity()).getAnnotation().getSense() == null || ((AnnotatedEntity)n.getEntity()).getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toList());

		// Collect similarity values
	/*	List<Double> senseValues = nodes.stream()
				.mapToDouble(e1 -> nodes.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> sense != null ? sense.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());*/
		List<Double> mergedValues = nodesWithSense.stream()
				.mapToDouble(e1 -> nodesWithSense.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> merged != null ? merged.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
	/*	List<Double> wordValues = nodes.stream()
				.mapToDouble(e1 -> nodes.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> word != null ? word.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());*/

		// Collect similarity values weighted by relevance
/*		List<Double> senseRelValues = nodes.stream()
				.mapToDouble(e1 -> nodes.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = sense != null ? sense.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0;
							double r = rel.weight(e2.getEntity());
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());*/
		List<Double> mergedRelValues = nodesWithSense.stream()
				.mapToDouble(e1 -> nodesWithSense.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = merged != null ? merged.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0;
							double r = rel.weight(e2.getEntity());
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
/*		List<Double> wordRelValues = nodes.stream()
				.mapToDouble(e1 -> nodes.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = word != null ? word.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0;
							double r = rel.weight(e2.getEntity());
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());*/

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
		w.write("Scoring of nodes\n");
		w.write("num\tlabel\ttfidf\trank\tf\tdf\tsense\tmerged\tword\tsenserel\tmergedrel\twordrel\n");
		nodes.forEach(e -> {
			double tfIdf = corpusMetric.weight(e.getEntity());
			double rank = e.getWeight();
			long fq = freqs.get(e.getEntity().getEntityLabel());
			long df = corpusMetric.corpus.getFrequency(e.getEntity());
//			double ss = nodes.contains(e) ? senseValues.get(nodes.indexOf(e)) : -1.0;
			double ms = nodesWithSense.contains(e) ? mergedValues.get(nodesWithSense.indexOf(e)) : -1.0;
//			double ws = nodes.contains(e) ? wordValues.get(nodes.indexOf(e)) : -1.0;
//			double ssrel = nodes.contains(e) ? senseRelValues.get(nodes.indexOf(e)) : -1.0;
			double msrel = nodesWithSense.contains(e) ? mergedRelValues.get(nodesWithSense.indexOf(e)) : -1.0;
//			double wsrel = nodes.contains(e) ? wordRelValues.get(nodes.indexOf(e)) : -1.0;

			w.write(nodes.indexOf(e) +"\t" + e + "\t" + f.format(tfIdf) + "\t" + f.format(rank) + "\t" + fq + "\t" + df + "\t" +
					/*f.format(ss) + "\t" +*/ f.format(ms) + "\t" + /*1f.format(ws) + "\t" +*/
					/*f.format(ssrel) + "\t" +*/ f.format(msrel) /*+ "\t" + f.format(wsrel)*/ + "\n");
		});
		w.write("\n");

		{
			double ratio = ((double) nodesWithSense.size() / (double) nodes.size()) * 100.0;
			w.write("Babelfy: " + f.format(ratio) + "% of entities have a sense\n");
		}
		{
			double ratio = ((double) nodesWithNominalSense.size() / (double) nodesWithSense.size()) * 100.0;
			w.write("Babelfy: " + f.format(ratio) + "% of senses are nominal\n");
		}

		Set<AnnotatedEntity> sensesInSEW = nodesWithSense.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e.getEntity()) > 0)
				.map(Node::getEntity)
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) sensesInSEW.size() / (double) nodesWithSense.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% senses defined (" + sensesInSEW.size()
					+ "/" + nodesWithSense.size() + ")\n");
		}
		Set<AnnotatedEntity> nominalSensesInSEW = nodesWithNominalSense.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e.getEntity()) > 0)
				.map(Node::getEntity)
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) nominalSensesInSEW.size() / (double) nodesWithNominalSense.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% nominal senses defined (" + nominalSensesInSEW.size()
					+ "/" + nodesWithNominalSense.size() + ")\n");
		}
		Set<AnnotatedEntity>formsInSEW = nodesWithNonNominalSense.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e.getEntity()) > 0)
				.map(Node::getEntity)
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) formsInSEW.size() / (double) nodesWithNonNominalSense.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% forms (of words not annotated with nominal senses) defined ("
					+ formsInSEW.size()	+ "/" + nodesWithNonNominalSense.size() + ")\n");
		}

		if (sense != null)
		{
			{
				Set<AnnotatedEntity> definedEntities = nodes.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(sense::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedEntities.size() / (double) nodes.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% entities defined (" + definedEntities.size() + "/" +
						nodes.size() + ")\n");
				w.write("sense: undefined entities " + ListUtils.removeAll(nodesWithSense, definedEntities).stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.map(AnnotatedEntity::toString)
						.collect(Collectors.joining(",")) + "\n");
			}
			{
				Set<AnnotatedEntity> definedSenses = nodesWithSense.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(sense::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedSenses.size() / (double) nodesWithSense.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% senses defined (" + definedSenses.size() +
						"/" + nodesWithSense.size() + ")\n");
			}
			{
				Set<AnnotatedEntity> definedForms = nodesWithoutSense.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(sense::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) nodesWithoutSense.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% forms of entities without sense defined (" + definedForms.size() +
						"/" + nodesWithoutSense.size() + ")\n");
			}
		}

		if (merged != null)
		{
			{
				Set<AnnotatedEntity> definedSenses = nodesWithSense.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(merged::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedSenses.size() / (double) nodesWithSense.size()) * 100.0;
				w.write("merged: " + f.format(ratio) + "% senses defined (" + definedSenses.size() + "/" +
						nodesWithSense.size() + ")\n");
				w.write("merged: undefined senses " + ListUtils.removeAll(nodesWithSense, definedSenses).stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.map(AnnotatedEntity::toString)
						.collect(Collectors.joining(",")) + "\n");
			}
			{
				Set<AnnotatedEntity> definedForms = nodesWithNominalSense.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(merged::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) nodesWithNominalSense.size()) * 100.0;
				w.write("merged: " + f.format(ratio) + "% nominal senses defined (" + definedForms.size() +
						"/" + nodesWithNominalSense.size() + ")\n");
			}
		}

		if (word != null)
		{
			{
				Set<AnnotatedEntity> definedForms = nodes.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(word::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) nodes.size()) * 100.0;
				w.write("word: " + f.format(ratio) + "% forms defined ("
						+ definedForms.size() + "/" + nodes.size() + ")\n");
			}
			{
				Set<AnnotatedEntity> definedForms = nodesWithoutSense.stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.filter(word::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) nodesWithoutSense.size()) * 100.0;
				w.write("word: " + f.format(ratio) + "% forms of entitites with no sense defined ("
						+ definedForms.size() + "/" + nodesWithoutSense.size() + ")\n");

				w.write("word: undefined forms " + ListUtils.removeAll(nodesWithoutSense, definedForms).stream()
						.map(Node::getEntity)
						.map(AnnotatedEntity.class::cast)
						.map(AnnotatedEntity::getAnnotation)
						.map(Annotation::getForm)
						.collect(Collectors.joining(",")) + "\n");
			}
		}

		// Report similarity
		w.write("\nSimilarity table\n");
		IntStream.range(0, nodes.size())
				.mapToObj(i -> i + "\t" + nodes.stream()
						.mapToDouble(entity -> sim.computeSimilarity(nodes.get(i).getEntity(), entity.getEntity()))
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
