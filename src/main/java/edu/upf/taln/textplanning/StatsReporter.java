package edu.upf.taln.textplanning;

import Jama.Matrix;
import edu.upf.taln.textplanning.similarity.EntitySimilarity;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import edu.upf.taln.textplanning.weighting.WeightingFunction;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Set;

/**
 * Generates statistics about a document containing annotations of deep syntactic trees.
 */
public class StatsReporter
{
	public static String reportStats(Set<LinguisticStructure> structures, WeightingFunction rel,
	                                 EntitySimilarity sim, LinguisticStructure g,
	                                 TextPlanner.Options o)
	{

		// todo rethink stats reporting
//		// Set up formatting
//		NumberFormat f = NumberFormat.getInstance();
//		f.setRoundingMode(RoundingMode.UP);
//		f.setMaximumFractionDigits(6);
//		f.setMinimumFractionDigits(6);
//		StringWriter w = new StringWriter();
//
//		// Collect nodes, nodesWithSense, nodesWithoutSense, etc.
//		List<AnnotatedWord> nodes = new ArrayList<>(g.vertexSet());
//		List<AnnotatedWord> nodesWithSense = nodes.stream()
//				.filter(n -> n.getBestCandidate().isPresent())
//				.filter(n -> n.getBestCandidate().map(Candidate::getEntity).map(Entity::getId).orElse("").startsWith("bn:"))
//				.collect(Collectors.toList());
//		List<AnnotatedWord> nodesWithNominalSense = nodesWithSense.stream()
//				.filter(n -> {
//					String l = n.getBestCandidate().map(Candidate::getEntity).map(Entity::getId).orElse("");
//					return l.startsWith("bn:") && l.endsWith("n");
//				})
//				.collect(Collectors.toList());
//
//		List<AnnotatedWord> nodesWithNonNominalSense = nodes.stream()
//				.filter(n -> {
//					String l = n.getBestCandidate().map(Candidate::getEntity).map(Entity::getId).orElse("");
//					return l.startsWith("bn:") && l.endsWith("n");
//				})
//				.collect(Collectors.toList());
//
//		List<Double> mergedValues = nodesWithSense.stream()
//				.map(AnnotatedWord::getBestCandidate)
//				.filter(Optional::isPresent)
//				.map(Optional::get)
//				.map(Candidate::getEntity)
//				.distinct()
//				.mapToDouble(e1 -> nodesWithSense.stream()
//						.map(AnnotatedWord::getBestCandidate)
//						.filter(Optional::isPresent)
//						.map(Optional::get)
//						.map(Candidate::getEntity)
//						.distinct()
//						.filter(e2 -> e2 != e1)
//						.mapToDouble(e2 -> sim.computeSimilarity(e1, e2))
//						.map(d -> d < o.simLowerBound ? 0.0 : d)
//						.average().orElse(0.0))
//				.boxed()
//				.collect(Collectors.toList());
//
//		List<Double> mergedRelValues = nodesWithSense.stream()
//				.map(AnnotatedWord::getBestCandidate)
//				.filter(Optional::isPresent)
//				.map(Optional::get)
//				.map(Candidate::getEntity)
//				.distinct()
//				.mapToDouble(e1 -> nodesWithSense.stream()
//						.map(AnnotatedWord::getBestCandidate)
//						.filter(Optional::isPresent)
//						.map(Optional::get)
//						.map(Candidate::getEntity)
//						.distinct()
//						.filter(e2 -> e2 != e1)
////						.mapToDouble(e2 -> sim.computeSimilarity(e1, e2))
//						.mapToDouble(e2 -> {
//							double s = sim != null ? sim.computeSimilarity(e1.getEntity(), e2.getEntity()) : 0.0;
//							double r = rel.weight(e2.getEntity().getId());
//							s = s < o.simLowerBound ? 0.0 : s;
//							r = Math.max(o.minRelevance, r);
//							return s*r;
//						})
//						.average().orElse(0.0))
//				.boxed()
//				.collect(Collectors.toList());
//
//
//		// Set up metrics
//		TFIDF corpusMetric = (TFIDF)rel;
//
//		Map<String, Long> freqs = structures.stream()
//				.map(LinguisticStructure::vertexSet)
//				.map(p -> p.stream()
//						.map(AnnotatedWord::getEntity)
//						.map(Entity::getId)
//						.collect(Collectors.toList()))
//				.flatMap(List::stream)
//				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
//
//		// Report metrics for each collection
//		w.write("Scoring of nodes\n");
//		w.write("num\tlabel\tid\ttfidf\trank\tf\tdf\tsim\tsim+rel\n");
//		nodes.forEach(e -> {
//			int node = nodes.indexOf(e);
//			String id  = nodes.get(node).toString();
//			double tfIdf = corpusMetric.weight(e.getEntity().getId());
//			double rank = e.getEntity().getWeight();
//			long fq = freqs.get(e.getEntity().getId());
//			long df = corpusMetric.getFrequency(e.getEntity().getId());
//			double ms = nodesWithSense.contains(e) ? mergedValues.get(nodesWithSense.indexOf(e)) : -1.0;
//			double msrel = nodesWithSense.contains(e) ? mergedRelValues.get(nodesWithSense.indexOf(e)) : -1.0;
//
//			w.write(node +"\t" + e + "\t" + id + "\t" + f.format(tfIdf) + "\t" + f.format(rank) + "\t" + fq + "\t" + df + "\t" +
//					 f.format(ms) + "\t" + f.format(msrel) + "\n");
//		});
//		w.write("\n");
//
//		{
//			double ratio = ((double) nodesWithSense.size() / (double) nodes.size()) * 100.0;
//			w.write("Babelfy: " + f.format(ratio) + "% of entities have a sense\n");
//		}
//		{
//			double ratio = ((double) nodesWithNominalSense.size() / (double) nodesWithSense.size()) * 100.0;
//			w.write("Babelfy: " + f.format(ratio) + "% of senses are nominal\n");
//		}
//
//		Set<Entity> sensesInSEW = nodesWithSense.stream()
//				.filter(e -> corpusMetric.getFrequency(e.getEntity().getId()) > 0)
//				.map(AnnotatedWord::getEntity)
//				.collect(Collectors.toSet());
//		{
//			double ratio = ((double) sensesInSEW.size() / (double) nodesWithSense.size()) * 100.0;
//			w.write("SEW: " + f.format(ratio) + "% senses defined (" + sensesInSEW.size()
//					+ "/" + nodesWithSense.size() + ")\n");
//		}
//		Set<Entity> nominalSensesInSEW = nodesWithNominalSense.stream()
//				.filter(e -> corpusMetric.getFrequency(e.getEntity().getId()) > 0)
//				.map(AnnotatedWord::getEntity)
//				.collect(Collectors.toSet());
//		{
//			double ratio = ((double) nominalSensesInSEW.size() / (double) nodesWithNominalSense.size()) * 100.0;
//			w.write("SEW: " + f.format(ratio) + "% nominal senses defined (" + nominalSensesInSEW.size()
//					+ "/" + nodesWithNominalSense.size() + ")\n");
//		}
//		Set<Entity>formsInSEW = nodesWithNonNominalSense.stream()
//				.filter(e -> corpusMetric.getFrequency(e.getEntity().getId()) > 0)
//				.map(AnnotatedWord::getEntity)
//				.collect(Collectors.toSet());
//		{
//			double ratio = ((double) formsInSEW.size() / (double) nodesWithNonNominalSense.size()) * 100.0;
//			w.write("SEW: " + f.format(ratio) + "% forms (of words not annotated with nominal senses) defined ("
//					+ formsInSEW.size()	+ "/" + nodesWithNonNominalSense.size() + ")\n");
//		}
//
//
//
//		if (sim != null)
//		{
//			{
//				Set<String> definedSenses = nodesWithSense.stream()
//						.map(AnnotatedWord::getEntity)
//						.filter(sim::isDefinedFor)
//						.map(Entity::getId)
//						.collect(Collectors.toSet());
//				double ratio = ((double) definedSenses.size() / (double) nodesWithSense.size()) * 100.0;
//				w.write("Merged SensEmbed: " + f.format(ratio) + "% senses defined (" + definedSenses.size() + "/" +
//						nodesWithSense.size() + ")\n");
//				w.write("Merged SensEmbed: undefined senses " + ListUtils.removeAll(nodesWithSense, definedSenses).stream()
//						.map(AnnotatedWord::getEntity)
//						.map(Entity::getId)
//						.collect(Collectors.joining(",")) + "\n");
//			}
//			{
//				Set<String> definedForms = nodesWithNominalSense.stream()
//						.map(AnnotatedWord::getEntity)
//						.filter(sim::isDefinedFor)
//						.map(Entity::getId)
//						.collect(Collectors.toSet());
//				double ratio = ((double) definedForms.size() / (double) nodesWithNominalSense.size()) * 100.0;
//				w.write("Merged SensEmbed: " + f.format(ratio) + "% nominal senses defined (" + definedForms.size() +
//						"/" + nodesWithNominalSense.size() + ")\n");
//			}
//		}
//
//		return w.toString();

		return "";
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
