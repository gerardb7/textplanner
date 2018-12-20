package edu.upf.taln.textplanning.common;

import Jama.Matrix;
import edu.upf.taln.textplanning.core.ranking.JamaPowerIteration;
import edu.upf.taln.textplanning.core.ranking.MatrixFactory;
import edu.upf.taln.textplanning.core.similarity.SimilarityFunction;
import edu.upf.taln.textplanning.core.weighting.WeightingFunction;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ujmp.core.doublematrix.DoubleMatrix2D;
import org.ujmp.core.doublematrix.impl.DefaultDenseDoubleMatrix2D;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VisualizationUtils
{
	private final static Logger log = LogManager.getLogger();

	public static void visualizeSimilarityMatrix(String label, List<String> items, List<String> labels, SimilarityFunction sim)
	{

		final List<String> defined_items = items.stream()
				.filter(sim::isDefinedFor)
				.collect(Collectors.toList());
		int n = defined_items.size();
		log.info(n + " defined items out of " + items.size());

		// create vectors
		final double[][] sim_matrix = MatrixFactory.createMeaningsSimilarityMatrix(defined_items, sim, (a, b) -> true, 0.0, false, false, true);
		final double[] values = Arrays.stream(sim_matrix)
				.flatMapToDouble(Arrays::stream)
				.toArray();

		// prepare visualuzation matrix
		DoubleMatrix2D m = new DefaultDenseDoubleMatrix2D(values, n, n);
		IntStream.range(0, n).forEach(i ->
		{
			m.setColumnLabel(i, labels.get(i));
			m.setRowLabel(i, labels.get(i));
		});
		m.setLabel(label);
		m.showGUI();
	}


	public static void visualizeRankingVector(String label, List<String> synsets, List<String> labels, WeightingFunction w, SimilarityFunction sim,
	                                                 BiPredicate<String, String> f, double t, double d)
	{
		final double[][] ranking_arrays = MatrixFactory.createMeaningRankingMatrix(synsets, w, sim, f, t, d);
		Jama.Matrix ranking_matrix = new Jama.Matrix(ranking_arrays);

		JamaPowerIteration alg = new JamaPowerIteration();
		Matrix finalDistribution = alg.run(ranking_matrix, labels);
		double[] ranking = finalDistribution.getColumnPackedCopy();
		final List<Triple<String, String, Double>> sorted = IntStream.range(0, synsets.size())
				.mapToObj(i -> Triple.of(synsets.get(i), labels.get(i), ranking[i]))
				.sorted(Comparator.comparingDouble(Triple::getRight))
				.collect(Collectors.toList());
		Collections.reverse(sorted);

		int n = sorted.size();
		DoubleMatrix2D m = new DefaultDenseDoubleMatrix2D(n, 1);
		m.setColumnLabel(0, "rank");
		IntStream.range(0, n).forEach(i -> m.setRowLabel(i, sorted.get(i).getMiddle()));
		IntStream.range(0, n).forEach(i -> m.setDouble(sorted.get(i).getRight(), i, 0));

		m.setLabel(label);
		m.showGUI();
	}
}
