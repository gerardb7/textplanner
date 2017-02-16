package edu.upf.taln.textplanning.utils;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.similarity.ItemSimilarity;
import edu.upf.taln.textplanning.similarity.SensEmbedSimilarity;
import edu.upf.taln.textplanning.similarity.TreeEditSimilarity;
import edu.upf.taln.textplanning.similarity.Word2VecSimilarity;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This evaluation class uses the similarity metric used by the text planner to calculate similarity between plans.
 */
public class Evaluator
{
	private final static Logger log = LoggerFactory.getLogger(Evaluator.class);

	public static void evaluate(Path inGoldPath, Path inSystemPath, Path inSenseVectors, Path inWordVectors) throws Exception
	{
		// Collect files from folders
		List<Path> goldFiles = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(inGoldPath, 1))
		{
			goldFiles.addAll(paths.filter(Files::isRegularFile)
					.collect(Collectors.toList()));
		}

		List<Path> systemFiles = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(inSystemPath, 1))
		{
			systemFiles.addAll(paths.filter(Files::isRegularFile)
					.collect(Collectors.toList()));
		}

		// Find pairs of files in both folders with the same name
		ConLLAcces reader = new ConLLAcces();
		List<Pair<Path, Path>> pairedFiles = goldFiles.stream()
				.map(gf -> Pair.of(gf, systemFiles
						.stream()
						.filter(sf -> sf.getFileName().equals(gf.getFileName()))
						.findAny()))
				.filter(p -> p.getRight().isPresent())
				.map(p -> Pair.of(p.getLeft(), p.getRight().get()))
				.collect(Collectors.toList());

		// Set up similarity metric
		ItemSimilarity senseSim = inSenseVectors == null ? null : new SensEmbedSimilarity(inSenseVectors);
		Word2VecSimilarity wordSim = inWordVectors == null ? null : new Word2VecSimilarity(inWordVectors);
		TreeEditSimilarity similarity = new TreeEditSimilarity(wordSim, senseSim);

		int numPairsSim = 0;
		int numPairsStats = 0;
		double accumulatedAverages = 0.0;
		double accumulatedPrecision = 0.0;
		double accumulatedRecall = 0.0;
		double accumulatedAccuracy = 0.0;
		double accumulatedFscore = 0.0;
		NumberFormat format = NumberFormat.getInstance();
		format.setRoundingMode(RoundingMode.UP);
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);

		for (Pair<Path, Path> p : pairedFiles)
		{
			// Load files into Strings
			String gold = null;
			String system = null;
			try
			{
				gold = new String(Files.readAllBytes(p.getLeft()));
				system = new String(Files.readAllBytes(p.getRight()));
			}
			catch (IOException e)
			{
				System.err.print("Error reading content of file " + p.getLeft() + " : " + e);
			}
			if (gold == null || system == null)
				continue;

			// Read trees from files
			List<SemanticTree> goldTrees = new ArrayList<>();
			List<SemanticTree> systemTrees = new ArrayList<>();
			try
			{
				goldTrees.addAll(reader.readSemanticTrees(gold));
				systemTrees.addAll(reader.readSemanticTrees(system));
				systemTrees = systemTrees.subList(0, Math.min(goldTrees.size(), systemTrees.size()));
			}
			catch (Exception e)
			{
				System.err.print("Error reading content of file " + p.getLeft() + " : " + e);
			}
			if (goldTrees.isEmpty() || systemTrees.isEmpty())
			{
				log.info("Summaries for file " + p.getLeft().getFileName().toString() + " have no trees");
				continue;
			}

			// Calculate average similarity between N sentences in gold and N first sentences in system
			double average = systemTrees.stream()
					.mapToDouble(st -> goldTrees.stream()
							.mapToDouble(gt -> similarity.getSimilarity(st, gt))
							.average().orElse(0.0))
					.average().orElse(0.0);
			log.info(   "Average similarity for summary " + p.getLeft().getFileName().toString() +
						" with " + systemTrees.size() + " trees is: " + format.format(average));
			++numPairsSim;
			accumulatedAverages += average;

			// Calculate precision, recall and f-score of entities
			Set<String> goldEntities = goldTrees.stream()
					.map(SemanticTree::getEntities)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());

			Set<String> systemEntities = systemTrees.stream()
					.map(SemanticTree::getEntities)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());

			Set<String> union = new HashSet<>(goldEntities);
			union.addAll(systemEntities);
			Set<String> intersection = new HashSet<>(goldEntities);
			intersection.retainAll(systemEntities);

			Set<String> truePositives = new HashSet<>(systemEntities);
			truePositives.retainAll(goldEntities);
			Set<String> falsePositives = new HashSet<>(systemEntities);
			falsePositives.removeAll(goldEntities);
			Set<String> trueNegatives = new HashSet<>(union);
			trueNegatives.removeAll(goldEntities);
			trueNegatives.removeAll(systemEntities);
			Set<String> falseNegatives = new HashSet<>(goldEntities);
			falseNegatives.removeAll(systemEntities);

			if (union.isEmpty())
			{
				log.info("Summaries for file " + p.getLeft().getFileName().toString() + " have no entities");
				continue;
			}
			double precision = (double)truePositives.size() / (double)(truePositives.size() + falsePositives.size());
			double recall = (double)truePositives.size() / (double)(truePositives.size() + falseNegatives.size());
			double accuracy = (double)(truePositives.size() + trueNegatives.size()) /
				(double)(truePositives.size() + falsePositives.size() + trueNegatives.size() + falseNegatives.size());
			double fscore = (precision + recall == 0.0) ? 0.0 : 2*(precision * recall) / (precision + recall);
			log.info(   "Entity-based stats for summary " + p.getLeft().getFileName().toString() +
						" with " + union.size() + " entities: p=" + format.format(precision) +
						" r=" + format.format(recall) +
						" a=" + format.format(accuracy) + " f=" + format.format(fscore));
			accumulatedPrecision += precision;
			accumulatedRecall += recall;
			accumulatedAccuracy += accuracy;
			accumulatedFscore += fscore;
			++numPairsStats;
		}

		double totalAverage = accumulatedAverages / (double) numPairsSim;
		log.info("Total average for " + numPairsSim + " summaries is " + format.format(totalAverage));
		log.info(   "Total entity-based stats : p=" + format.format(accumulatedPrecision / (double) numPairsStats) +
					" r=" + format.format(accumulatedRecall / (double) numPairsStats) +
					" a=" + format.format(accumulatedAccuracy / (double) numPairsStats) +
					" f=" + format.format(accumulatedFscore / (double) numPairsStats));

	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 3)
		{
			System.err.println("Wrong number of parameters. Usage: evaluator path_gold path_system path_sense_vectors [path_word_vectors]");
			System.exit(-1);
		}

		Path goldPath = Paths.get(args[0]);
		Path systemPath = Paths.get(args[1]);
		if (!Files.exists(goldPath) || !Files.isDirectory(goldPath))
		{
			System.err.println("Cannot open " + goldPath);
			System.exit(-1);
		}
		if (!Files.exists(systemPath) || !Files.isDirectory(systemPath))
		{
			System.err.println("Cannot open " + systemPath);
			System.exit(-1);
		}

		Path senseVectors = Paths.get(args[2]);
		if (!Files.exists(senseVectors) || !Files.isRegularFile(senseVectors))
		{
			System.err.println("Cannot open " + senseVectors);
			System.exit(-1);
		}

		Path wordVectors = null;
		if (args.length == 4)
		{
			wordVectors = Paths.get(args[3]);
			if (!Files.exists(wordVectors) || !Files.isRegularFile(wordVectors))
			{
				System.err.println("Cannot open " + wordVectors);
				System.exit(-1);
			}
		}

		Evaluator.evaluate(goldPath, systemPath, senseVectors, wordVectors);
	}
}
