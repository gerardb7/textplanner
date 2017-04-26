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
		final SensEmbed ssim;
		final Word2Vec wsim;
		if (sim instanceof SensEmbed)
		{
			ssim = ((SensEmbed) sim);
			wsim = null;
		}
		else if (sim instanceof Word2Vec)
		{
			ssim = null;
			wsim = ((Word2Vec) sim);
		}
		else if (sim instanceof Combined)
		{
			EntitySimilarity s1 = ((Combined)sim).functions.get(0);
			EntitySimilarity s2 = ((Combined)sim).functions.get(1);
			ssim = (SensEmbed) (s1 instanceof SensEmbed ? s1 : s2);
			wsim = (Word2Vec) (s1 instanceof Word2Vec ? s1 : s2);
		}
		else
		{
			ssim = null;
			wsim = null;
		}

		// Collect entities, senses, forms, etc.
		List<AnnotatedEntity> entities = t.stream().map(SemanticTree::vertexSet)
				.map(p -> p.stream()
						.map(Node::getEntity))
				.flatMap(Function.identity())
				.distinct()
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toList());
		List<AnnotatedEntity> senses = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() != null)
				.collect(Collectors.toList());
		List<AnnotatedEntity> nominalSenses = senses.stream()
				.filter(e -> e.getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toList());
		List<AnnotatedEntity> allForms = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.collect(Collectors.toList());
		List<AnnotatedEntity> forms = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() == null)
				.collect(Collectors.toList());
		List<AnnotatedEntity> nonNominalSenseforms = entities.stream()
				.map(AnnotatedEntity.class::cast)
				.filter(e -> e.getAnnotation().getSense() == null || e.getAnnotation().getSense().endsWith("n"))
				.collect(Collectors.toList());

		// Collect similarity values
		List<Double> simValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> sim.computeSimilarity(e1, e2))
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> sensEmbedValues = senses.stream()
				.mapToDouble(e1 -> senses.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> ssim != null ? ssim.computeSimilarity(e1, e2) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> word2VecValues = allForms.stream()
				.mapToDouble(e1 -> allForms.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> wsim != null ? wsim.computeSimilarity(e1, e2) : 0.0)
						.map(d -> d < o.simLowerBound ? 0.0 : d)
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());

		// Collect similarity values weighted by relevance
		List<Double> simRelValues = entities.stream()
				.mapToDouble(e1 -> entities.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = sim.computeSimilarity(e1, e2);
							double r = rel.weight(e2);
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> sensEmbedRelValues = senses.stream()
				.mapToDouble(e1 -> senses.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = ssim != null ? ssim.computeSimilarity(e1, e2) : 0.0;
							double r = rel.weight(e2);
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());
		List<Double> word2VecRelValues = allForms.stream()
				.mapToDouble(e1 -> allForms.stream()
						.filter(e2 -> e1 != e2)
						.mapToDouble(e2 -> {
							double s = wsim != null ? wsim.computeSimilarity(e1, e2) : 0.0;
							double r = rel.weight(e2);
							s = s < o.simLowerBound ? 0.0 : s;
							r = r < o.relevanceLowerBound ? 0.0 : r;
							return s*r;
						})
						.average().orElse(0.0))
				.boxed()
				.collect(Collectors.toList());

		// Set up metrics
//		Map<WeightingFunction, Double> functions = ((Linear) rel).getFunctions();
//		Iterator<WeightingFunction> it = functions.keySet().iterator();
		TFIDF corpusMetric = (TFIDF)rel;
		Position positionMetric = new Position();
		positionMetric.setCollection(t);

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
		w.write("num\tlabel\ttfidf\tpos\tlinear\trank\tf\tdf\tsim\tsensEmbed\tword2vec\tsimRel\tsensEmbedRel\tword2VecRel\n");
		entities.forEach(e -> {
			double tfIdf = corpusMetric.weight(e);
			double pos = positionMetric.weight(e);
			double linear = rel.weight(e);
			double rank = rankedEntities.get(e);
			long fq = freqs.get(e.getEntityLabel());
			long df = corpusMetric.corpus.getFrequency(e);
			double s = simValues.get(entities.indexOf(e));
			double ss = senses.contains(e) ? sensEmbedValues.get(senses.indexOf(e)) : -1.0;
			double ws = allForms.contains(e) ? word2VecValues.get(allForms.indexOf(e)) : -1.0;
			double srel = simRelValues.get(entities.indexOf(e));
			double ssrel = senses.contains(e) ? sensEmbedRelValues.get(senses.indexOf(e)) : -1.0;
			double wsrel = allForms.contains(e) ? word2VecRelValues.get(allForms.indexOf(e)) : -1.0;

			w.write(entities.indexOf(e) +"\t" + e + "\t" + f.format(tfIdf) + "\t" + f.format(pos) +
					"\t" + f.format(linear) + "\t" + f.format(rank) + "\t" + fq + "\t" + df + "\t" +
					f.format(s) + "\t" + f.format(ss) + "\t" + f.format(ws) + "\t" +
					f.format(srel) + "\t" + f.format(ssrel) + "\t" + f.format(wsrel) + "\n");
		});
		w.write("\n");

		{
			double ratio = ((double) senses.size() / (double) entities.size()) * 100.0;
			w.write("Babelfy: " + f.format(ratio) + "% of entities have a sense\n");
		}
		{
			double ratio = ((double) nominalSenses.size() / (double) senses.size()) * 100.0;
			w.write("Babelfy: " + f.format(ratio) + "% of senses are nominal\n");
		}

		Set<AnnotatedEntity> sensesInSEW = senses.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) sensesInSEW.size() / (double) senses.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% senses defined (" + sensesInSEW.size()
					+ "/" + senses.size() + ")\n");
		}
		Set<AnnotatedEntity> nominalSensesInSEW = nominalSenses.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) nominalSensesInSEW.size() / (double) nominalSenses.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% nominal senses defined (" + nominalSensesInSEW.size()
					+ "/" + nominalSenses.size() + ")\n");
		}
		Set<AnnotatedEntity>formsInSEW = nonNominalSenseforms.stream()
				.filter(e -> corpusMetric.corpus.getFrequency(e) > 0)
				.collect(Collectors.toSet());
		{
			double ratio = ((double) formsInSEW.size() / (double) nonNominalSenseforms.size()) * 100.0;
			w.write("SEW: " + f.format(ratio) + "% forms (of words not annotated with nominal senses) defined ("
					+ formsInSEW.size()	+ "/" + nonNominalSenseforms.size() + ")\n");
		}

		if (ssim != null)
		{
			{
				Set<AnnotatedEntity> definedSenses = senses.stream()
						.filter(ssim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedSenses.size() / (double) senses.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% senses defined (" + definedSenses.size() + "/" +
						senses.size() + ")\n");
				w.write("sense: undefined senses " + ListUtils.removeAll(senses, definedSenses).stream()
						.map(AnnotatedEntity::toString)
						.collect(Collectors.joining(",")) + "\n");
			}
			{
				Set<AnnotatedEntity> definedForms = nominalSenses.stream()
						.filter(ssim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) nominalSenses.size()) * 100.0;
				w.write("sense: " + f.format(ratio) + "% nominal senses defined (" + definedForms.size() +
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
				w.write("word: " + f.format(ratio) + "% forms defined ("
						+ definedForms.size() + "/" + allForms.size() + ")\n");
			}
			{
				Set<AnnotatedEntity> definedForms = forms.stream()
						.filter(wsim::isDefinedFor)
						.collect(Collectors.toSet());
				double ratio = ((double) definedForms.size() / (double) forms.size()) * 100.0;
				w.write("word: " + f.format(ratio) + "% forms (of words not annotated with a sense) defined ("
						+ definedForms.size() + "/" + forms.size() + ")\n");

				w.write("word: undefined forms " + ListUtils.removeAll(forms, definedForms).stream()
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
