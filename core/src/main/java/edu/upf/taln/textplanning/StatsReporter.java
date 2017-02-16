package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.corpora.CorpusCounts;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.metrics.CorpusMetric;
import edu.upf.taln.textplanning.metrics.PatternMetric;
import edu.upf.taln.textplanning.similarity.PatternSimilarity;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates statistics about a document containing semantic trees.
 */
public class StatsReporter
{
	public static String reportStats(Set<SemanticTree> inTrees,
	                                 Set<String> inReferences, CorpusCounts inCounts, PatternSimilarity inSimilarity
	)
	{
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);

		StringWriter writer = new StringWriter();
		inReferences.forEach(r -> writer.write("Frequency of " + r + ": " + inCounts.getCounts(r, r, "").freq));

		PatternMetric cmetric = new CorpusMetric(inCounts, CorpusMetric.Metric.Cooccurrence, "");
		//PatternMetric pmetric = new PositionMetric();

		List<Pair<SemanticTree, Double>> sortedMsgs =
				cmetric.assess(inReferences, inTrees)
						.entrySet().stream()
						.sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
						.map(e -> Pair.of(e.getKey(), e.getValue()))
						.collect(Collectors.toList());
		List<SemanticTree> trees =
				sortedMsgs.stream().map(Pair::getLeft).collect(Collectors.toList());

		writer.write("Semantic trees");
		IntStream.range(0, trees.size())
				.forEach(i -> writer.write("\tTree " + (i + 1) + ": " + trees.get(i)));
		writer.write("\n");

		writer.write("Scores");
		sortedMsgs.forEach(p -> writer.write(format.format(p.getValue())));
		writer.write("\n");

		writer.write("Similarity table");
		String b = "\t" + IntStream.range(0, trees.size())
				.mapToObj(Integer::toString)
				.collect(Collectors.joining("\t\t")) + "\n" +
				IntStream.range(0, trees.size())
						.mapToObj(i -> Integer.toString(i) + "\t" + IntStream.range(0, trees.size())
								.mapToDouble(j -> inSimilarity.getSimilarity(trees.get(i), trees.get(j)))
								.mapToObj(format::format)
								.map(s -> s.replace(',', '.'))
								.collect(Collectors.joining("\t")))
						.collect(Collectors.joining("\n"));
		writer.write(b);
		writer.write("\n");

		writer.write("Word forms and senses");
		IntStream.range(0, trees.size())
				.forEach(i -> IntStream.range(i + 1, sortedMsgs.size())
						.forEach(j -> {
							writer.write("Trees " + (i + 1) + " and " + (j + 1));
							writer.write("\tCommon senses :" +
									CollectionUtils.intersection(getSensesAndForms(trees.get(i)),
											getSensesAndForms(trees.get(j))).stream()
											.map(StatsReporter::printSenseFormPair)
											.collect(Collectors.joining(",")));
							writer.write("\tCommon word forms :" +
									CollectionUtils.intersection(trees.get(i).getWordForms(),
											trees.get(j).getWordForms()).stream()
											.collect(Collectors.joining(",")));

							writer.write("\tSenses " + (i + 1) + ": " + getSensesAndForms(trees.get(i)).stream()
									.map(p -> {
										CorpusCounts.Counts counts = inCounts.getCounts("01929539n", p.getLeft(), "");
										double sim = inSimilarity.getSimilarity("01929539n", p.getLeft());
										return printSenseFormPair(p) + " c=" + counts.cooccur + " f=" + counts.freq + " s=" + format.format(sim).replace(',', '.');
									})
									.collect(Collectors.joining(", ")));

							writer.write("\tSenses " + (j + 1) + ": " + getSensesAndForms(trees.get(j)).stream()
									.map(p -> {
										CorpusCounts.Counts counts = inCounts.getCounts(p.getLeft(), "01929539n", "");
										double sim = inSimilarity.getSimilarity("01929539n", p.getLeft());
										return printSenseFormPair(p) + " c=" + counts.cooccur + " f=" + counts.freq + " s=" + format.format(sim).replace(',', '.');
									})
									.collect(Collectors.joining(", ")));
						}));

		return writer.toString();
	}

	private static List<Pair<String, String>> getSensesAndForms(SemanticTree inTree)
	{
		return inTree.getPreOrder().stream()
				.filter(SemanticTree::isEntity)
				.map(n -> Pair.of(n.getData().getLeft().getReference(), n.getData().getLeft().getForm()))
				.distinct()
				.collect(Collectors.toList());
	}

	private static String printSenseFormPair(Pair<String, String> inPair)
	{
		return inPair.getLeft() + "-" + inPair.getRight();
	}
}
