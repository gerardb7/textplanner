package edu.upf.taln.textplanning.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.structures.Meaning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides methods for manipulation of word and sense embedding files.
 */
public class EmbeddingUtils
{
//	private static final int expectedVectors = 1734862;
	private final static Logger log = LogManager.getLogger(EmbeddingUtils.class);

	/**
	 * Given a file containing vectors for pairs of word and senses, produces a file with a mean vector for each sense
	 *
	 * @param vectors_path path to the input file
	 * @param out_path        path to the merged file
	 */
	private static void mergeEmbeddings(Path vectors_path, int num_dimensions, Path out_path) throws IOException
	{
		Map<String, List<double[]>> allVectors = parseEmbeddingsFile(vectors_path, num_dimensions, true, true);
		Map<String, double[]> meanVectors = averageEmbeddings(allVectors, num_dimensions);
		writeEmbeddingsToFile(meanVectors, num_dimensions, out_path);
	}

	private static void subsetEmbeddings(Path embeddingsPath, int num_dimensions, Path graphs_file, Path outPath) throws Exception
	{
		Map<String, List<double[]>> allVectors = parseEmbeddingsFile(embeddingsPath, num_dimensions, true, true);

		log.info("Calculating subset");
		Stopwatch timer = Stopwatch.createStarted();
		log.info("Reading graphs");
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		List<String> meanings = graphs.getCandidates().stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(Collectors.toList());

		// Filter vectors to those in the conll
		Map<String, List<double[]>> subsetVectors = allVectors.entrySet().stream()
				.filter(e -> meanings.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		long numNominalSynsets = meanings.stream().filter(s -> s.endsWith("n")).count();
		long numNominalVectors = subsetVectors.keySet().stream().filter(s -> s.endsWith("n")).count();
		log.info("Read " + meanings.size() + " synset ids (" + numNominalSynsets + " nominal) and found vectors for " +
				subsetVectors.keySet().size() + " (" + numNominalVectors + " nominal)");
		log.info("Subset creation took " + timer.stop());

		// Average and store vectors to file
		Map<String, double[]> meanVectors = averageEmbeddings(subsetVectors, num_dimensions);
		writeEmbeddingsToFile(meanVectors, num_dimensions, outPath);
	}

	/**
	 * Reads a file containing embeddings.
	 *
	 * @param embeddings path to file containing the embeddings
	 * @param sensesOnly     if true vectors for words are discarded
	 * @param useSynsetsAsKeys true -> BabelNetWrapper synset ids used as keys, false -> pairs of word and ids used as keys
	 * @return a map containing all vectors
	 */
	public static Map<String, List<double[]>> parseEmbeddingsFile(Path embeddings, int numDimensions,
	                                                              @SuppressWarnings("SameParameterValue") boolean sensesOnly,
	                                                              boolean useSynsetsAsKeys) throws IOException
	{
		int numVectorsRead = 0;
		int numVectorsKept = 0;

		HashMap<String, List<double[]>> allVectors = new HashMap<>();
		FileInputStream fs = new FileInputStream(embeddings.toFile());
		InputStreamReader isr = new InputStreamReader(fs, "UTF-8");
		BufferedReader br = new BufferedReader(isr);
		String line;
		boolean first_line = true;

		while ((line = br.readLine()) != null)
		{
			if (line.isEmpty())
			{
				first_line = false;
				continue;
			}

			String[] columns = line.split(" ");
			if (!first_line && columns.length != numDimensions + 1)
			{
				log.error("Cannot parse line " + line);
				continue;
			}

			first_line = false;

			// For performance reasons, let's avoid regular expressions
			if (!sensesOnly || columns[0].contains("bn:"))
			{
				String babelnetId = columns[0];
				if (useSynsetsAsKeys && !babelnetId.startsWith("bn:"))
				{
					babelnetId = babelnetId.substring(babelnetId.indexOf("bn:"));
				}

				double[] vector = new double[numDimensions];
				for (int i = 0; i < numDimensions; ++i)
				{
					vector[i] = Double.parseDouble(columns[i + 1]);
				}

				allVectors.computeIfAbsent(babelnetId, v -> new ArrayList<>()).add(vector);
				++numVectorsKept;
			}

			if (++numVectorsRead % 100000 == 0)
			{
				log.info(numVectorsRead + " vectors read");
			}
		}
		log.info("Parsing complete: " + numVectorsRead + " vectors read and " + numVectorsKept + " vectors stored");

		return allVectors;
	}


	/**
	 * Writes veectors to a file
	 *
	 * @param vectors the vectors
	 * @param out_path path to the output file
	 */
	private static void writeEmbeddingsToFile(Map<String, double[]> vectors, int num_dimensions, Path out_path) throws FileNotFoundException
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
	 * Does an arithmetic average of each list of vectors associated to a key
	 *
	 * @param vectors the vectors to average
	 * @return the averaged vectors
	 */
	private static Map<String, double[]> averageEmbeddings(Map<String, List<double[]>> vectors, int num_dimensions)
	{
		// Merge vectors corresponding to the same babelnet sense
		log.info("Merging vectors");
		Stopwatch timer = Stopwatch.createStarted();
		Map<String, double[]> meanVectors = new HashMap<>();

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

	public static void main(String[] args) throws Exception
	{
		if (args.length < 3)
		{
			System.err.println("Wrong number of args. Usage: embedutils command [params]");
			System.exit(-1);
		}

		switch (args[0])
		{
			case "merge":
			{
				if (args.length != 4)
				{
					System.err.println("Wrong number of args. Usage: embedutils merge embeddings_file num_dimensions output_file");
					System.exit(-1);
				}

				// Read command-line args
				Path embeddingsPath = Paths.get(args[1]);
				if (!Files.exists(embeddingsPath) || !Files.isRegularFile(embeddingsPath))
				{
					System.err.println("Cannot open " + embeddingsPath);
					System.exit(-1);
				}

				int numDimensions = Integer.parseInt(args[2]);

				Path outPath = Paths.get(args[3]);
				if (!Files.isRegularFile(outPath))
				{
					System.err.println("Cannot create " + outPath);
					System.exit(-1);
				}

				mergeEmbeddings(embeddingsPath,numDimensions, outPath);
				break;
			}
			case "subset":
			{
				if (args.length != 5)
				{
					System.err.println("Wrong number of args." +
							"Usage: embedutils subset embeddings_file num_dimensions graphs_file output_file");
					System.exit(-1);
				}

				Path embeddingsPath = Paths.get(args[1]);
				if (!Files.exists(embeddingsPath) || !Files.isRegularFile(embeddingsPath))
				{
					System.err.println("Cannot open " + embeddingsPath);
					System.exit(-1);
				}

				int numDimensions = Integer.parseInt(args[2]);

				Path graphs_path = Paths.get(args[3]);
				if (!Files.exists(graphs_path) || !Files.isRegularFile(graphs_path))
				{
					System.err.println("Cannot open " + graphs_path);
					System.exit(-1);
				}

				Path outPath = Paths.get(args[4]);
				subsetEmbeddings(embeddingsPath, numDimensions, graphs_path, outPath);
				break;
			}
			default:
			{
				System.err.println("Unrecognized command " + args[0] + ". Available commands: merge, subset");
				System.exit(-1);
			}
		}
	}
}
