package edu.upf.taln.textplanning.amr.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import edu.upf.taln.textplanning.amr.structures.AMRGraphList;
import edu.upf.taln.textplanning.core.similarity.TextVectorsSimilarity;
import edu.upf.taln.textplanning.core.similarity.VectorsTypes.Format;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.utils.Serializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Provides methods for manipulation of text files containing distributional vectors.
 */
public class VectorsTextFileUtils
{
	private final static Logger log = LogManager.getLogger();

	/**
	 * Given a file containing vectors for pairs of word and senses, produces a file with a mean vector for each sense
	 */
	private static void mergeVectors(Function<String, String> key_reducer, Path vectors_path, Format format, Path out_path) throws Exception
	{

		Map<String, double[]> vectors = TextVectorsSimilarity.readVectorsFromFile(vectors_path, format);
		Map<String, List<double[]>> groupedVectors = vectors.keySet().stream()
				.collect(groupingBy(key_reducer, mapping(vectors::get, toList())));
		Map<String, double[]> meanVectors = averageVectors(groupedVectors);
		writeVectorsToFile(meanVectors, format, out_path);
	}

	private static void subsetVectors(Path vectors_path, Format format, Path graphs_file, Path outPath) throws Exception
	{
		Map<String, double[]> vectors = TextVectorsSimilarity.readVectorsFromFile(vectors_path, format);
		log.info("Calculating subset");
		Stopwatch timer = Stopwatch.createStarted();
		log.info("Reading graphs");
		AMRGraphList graphs = (AMRGraphList) Serializer.deserialize(graphs_file);

		List<String> meanings = graphs.getCandidates().stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toList());

		// Filter vectors
		Map<String, double[]> subsetVectors = vectors.keySet().stream()
				.filter(meanings::contains)
				.collect(Collectors.toMap(e -> e, vectors::get));

		long numNominalSynsets = meanings.stream().filter(s -> s.endsWith("n")).count();
		long numNominalVectors = subsetVectors.keySet().stream().filter(s -> s.endsWith("n")).count();
		log.info("Read " + meanings.size() + " synset ids (" + numNominalSynsets + " nominal) and found vectors for " +
				subsetVectors.keySet().size() + " (" + numNominalVectors + " nominal)");
		log.info("Subset creation took " + timer.stop());

		// Write vectors to file
		writeVectorsToFile(subsetVectors, format, outPath);
	}



	/**
	 * Writes distributional vectors to a text file
	 */
	private static void writeVectorsToFile(Map<String, double[]> vectors, Format format, Path out_path) throws FileNotFoundException
	{
		// Create String representation of each averaged vector and writeGraphs them to a file
		log.info("Writing to file");
		Stopwatch timer = Stopwatch.createStarted();

		PrintWriter out = new PrintWriter(out_path.toFile());
		int numVectorsRead = 0;
		DecimalFormat formatter = new DecimalFormat("#0.000000");
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
		symbols.setDecimalSeparator('.');
		formatter.setDecimalFormatSymbols(symbols);

		int num_dimensions = vectors.values().iterator().next().length;

		for (Iterable<Map.Entry<String, double[]>> chunk : Iterables.partition(vectors.entrySet(), 10000))
		{
			StringBuilder builder = new StringBuilder(); // Use StringBuilder for efficiency reasons
			for (Map.Entry<String, double[]> e : chunk)
			{
				builder.append(e.getKey());
				builder.append(" ");

				double[] v = e.getValue();
				for (int i = 0; i < num_dimensions; ++i)
				{
					builder.append(formatter.format(v[i]));
					builder.append(' ');
				}
				builder.append('\n');
			}
			out.print(builder.toString());

			numVectorsRead += 10000;
			if (numVectorsRead % 100000 == 0)
			{
				log.info(timer.toString() + ": " + numVectorsRead + " vectors written");
			}
		}

		out.close();
		log.info("Writing complete: " + vectors.size() + " vectors written");
		log.info("Writing took " + timer.stop());
	}

	/**
	 * Does an arithmetic average of the lists of vectors associated to each key
	 */
	private static Map<String, double[]> averageVectors(Map<String, List<double[]>> vectors)
	{
		log.info("Merging vectors");
		Stopwatch timer = Stopwatch.createStarted();
		Map<String, double[]> meanVectors = new HashMap<>();
		int num_dimensions = vectors.values().iterator().next().get(0).length;

		for (Map.Entry<String, List<double[]>> entry : vectors.entrySet())
		{
			if (entry.getValue().size() == 1)
			{
				meanVectors.put(entry.getKey(), entry.getValue().get(0));
			}
			else
			{
				double[] sum = new double[num_dimensions];
				List<double[]> vects = entry.getValue();
				for (double[] vector : vects)
				{
					for (int j = 0; j < num_dimensions; ++j)
					{
						sum[j] += vector[j];
					}
				}

				double[] mean = new double[num_dimensions];
				for (int i = 0; i < num_dimensions; ++i)
				{
					mean[i] = sum[i] / vectors.size();
				}
				meanVectors.put(entry.getKey(), mean);
			}
		}

		log.info("Merging took " + timer.stop());
		return meanVectors;
	}


	@Parameters(commandDescription = "Create a subset of a text file containing distributional vectors bases on a file containing semantic graphs")
	private static class SubsetCommand
	{
		@Parameter(description = "Graphs file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> graphs;
		@Parameter(names = {"-i", "-inputFile"}, description = "Input vectors file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output vectors file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToNewFile.class)
		private List<Path> outputFile;
	}

	@Parameters(commandDescription = "Merges vectors in a text file")
	private static class MergeCommand
	{
		@Parameter(names = {"-i", "-inputFile"}, description = "Input vectors file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output vectors file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToNewFile.class)
		private List<Path> outputFile;
	}

	public static void main(String[] args) throws Exception
	{
		SubsetCommand subset = new SubsetCommand();
		MergeCommand merge = new MergeCommand();

		JCommander jc = new JCommander();
		jc.addCommand("subset", subset);
		jc.addCommand("convert", merge);
		jc.parse(args);

		Function<String, String> key_merger = k -> k.substring(k.indexOf("bn:"));

		if (jc.getParsedCommand().equals("subset"))
			VectorsTextFileUtils.subsetVectors(subset.graphs.get(0), Format.Text_Glove, subset.inputFile.get(0), subset.outputFile.get(0));
		else if (jc.getParsedCommand().equals("convert"))
			VectorsTextFileUtils.mergeVectors(key_merger, merge.inputFile.get(0), Format.Text_Glove, subset.outputFile.get(0));
	}
}
