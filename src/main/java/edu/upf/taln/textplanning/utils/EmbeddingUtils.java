package edu.upf.taln.textplanning.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private static final int dimension = 400;
//	private static final int expectedVectors = 1734862;
	private final static Logger log = LoggerFactory.getLogger(EmbeddingUtils.class);

	/**
	 * Given a file containing vectors for pairs of word and senses, produces a file with a mean vector for each sense
	 *
	 * @param inEmbeddingsPath path to the input file
	 * @param inOutPath        path to the merged file
	 */
	private static void mergeEmbeddings(Path inEmbeddingsPath, Path inOutPath) throws IOException
	{
		Map<String, List<double[]>> allVectors = parseEmbeddingsFile(inEmbeddingsPath, true, true);
		Map<String, double[]> meanVectors = averageEmbeddings(allVectors);
		writeEmbeddingsToFile(meanVectors, inOutPath);
	}

	/**
	 * Given a file containing sense embeddings and a conll file, produces a smaller embeddings file containing only
	 * the vectors for the senses found in the conll file
	 *
	 * @param embeddingsPath path to the file containing the embeddings
	 * @param synsetsPath path to file containing list of BabelNetWrapper synset ids
	 * @param outPath path to the new embeddings file
	 */
	private static void subsetEmbeddings(Path embeddingsPath, Path synsetsPath, Path outPath) throws Exception
	{
		Map<String, List<double[]>> allVectors = parseEmbeddingsFile(embeddingsPath, true, true);

		log.info("Calculating subset");
		Stopwatch timer = Stopwatch.createStarted();
		List<String> synsets = parseSysnsetsFile(synsetsPath);


		// Filter vectors to those in the conll
		Map<String, List<double[]>> subsetVectors = allVectors.entrySet().stream()
				.filter(e -> synsets.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		long numNominalSynsets = synsets.stream().filter(s -> s.endsWith("n")).count();
		long numNominalVectors = subsetVectors.keySet().stream().filter(s -> s.endsWith("n")).count();
		log.info("Read " + synsets.size() + " synset ids (" + numNominalSynsets + " nominal) and found vectors for " +
				subsetVectors.keySet().size() + " (" + numNominalVectors + " nominal)");
		log.info("Subset creation took " + timer.stop());


		// Average and store vectors to file
		Map<String, double[]> meanVectors = averageEmbeddings(subsetVectors);
		writeEmbeddingsToFile(meanVectors, outPath);
	}

	/**
	 * Reads a file containing embeddings.
	 *
	 * @param embeddings path to file containing the embeddings
	 * @param sensesOnly     if true vectors for words are discarded
	 * @param useSynsetsAsKeys true -> BabelNetWrapper synset ids used as keys, false -> pairs of word and ids used as keys
	 * @return a map containing all vectors
	 */
	public static Map<String, List<double[]>> parseEmbeddingsFile(Path embeddings, @SuppressWarnings("SameParameterValue") boolean sensesOnly,
	                                                              boolean useSynsetsAsKeys) throws IOException
	{
		int numVectorsRead = 0;
		int numVectorsKept = 0;

		HashMap<String, List<double[]>> allVectors = new HashMap<>();
		FileInputStream fs = new FileInputStream(embeddings.toFile());
		InputStreamReader isr = new InputStreamReader(fs, "UTF-8");
		BufferedReader br = new BufferedReader(isr);
		String line;

		while ((line = br.readLine()) != null)
		{
			if (line.isEmpty())
			{
				continue;
			}

			String[] columns = line.split(" ");
			if (columns.length != dimension + 1)
			{
				log.error("Cannot parse line " + line);
				continue;
			}

			// For performance reasons, let's avoid regular expressions
			if (!sensesOnly || columns[0].contains("bn:"))
			{
				String babelnetId = columns[0];
				if (useSynsetsAsKeys && !babelnetId.startsWith("bn:"))
				{
					babelnetId = babelnetId.substring(babelnetId.indexOf("bn:"));
				}

				double[] vector = new double[dimension];
				for (int i = 0; i < dimension; ++i)
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

	private static List<String> parseSysnsetsFile(Path p) throws IOException
	{
		LineIterator it = FileUtils.lineIterator(p.toFile(), "UTF-8");
		boolean firstLine = true;
		List<String> synsets = new ArrayList<>();
		try
		{
			while (it.hasNext())
			{
				String line = it.nextLine();
				if (firstLine)
					firstLine = false;
				else
					synsets.add(line.substring(0, line.indexOf('=')));
			}
		}
		finally
		{
			LineIterator.closeQuietly(it);
		}

		return synsets;
	}

	/**
	 * Writes veectors to a file
	 *
	 * @param inVectors the vectors
	 * @param inOutPath path to the output file
	 */
	private static void writeEmbeddingsToFile(Map<String, double[]> inVectors, Path inOutPath) throws FileNotFoundException
	{
		// Create String representation of each averaged vector and writeGraphs them to a file
		log.info("Writing to file");
		Stopwatch timer = Stopwatch.createStarted();
		PrintWriter out = new PrintWriter(inOutPath.toFile());
		int numVectorsRead = 0;
		DecimalFormat formatter = new DecimalFormat("#0.000000");
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
		symbols.setDecimalSeparator('.');
		formatter.setDecimalFormatSymbols(symbols);

		for (Iterable<Map.Entry<String, double[]>> chunk : Iterables.partition(inVectors.entrySet(), 10000))
		{
			StringBuilder builder = new StringBuilder(); // Use StringBuilder for efficiency reasons
			for (Map.Entry<String, double[]> e : chunk)
			{
				builder.append(e.getKey());
				builder.append(" ");

				double[] v = e.getValue();
				for (int i = 0; i < dimension; ++i)
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
		log.info("Writing complete: " + inVectors.size() + " vectors written");
		log.info("Writing took " + timer.stop());
	}

	/**
	 * Does an arithmetic average of each list of vectors associated to a key
	 *
	 * @param inVectors the vectors to average
	 * @return the averaged vectors
	 */
	private static Map<String, double[]> averageEmbeddings(Map<String, List<double[]>> inVectors)
	{
		// Merge vectors corresponding to the same babelnet sense
		log.info("Merging vectors");
		Stopwatch timer = Stopwatch.createStarted();
		Map<String, double[]> meanVectors = new HashMap<>();

		for (Map.Entry<String, List<double[]>> entry : inVectors.entrySet())
		{
			if (entry.getValue().size() == 1)
			{
				meanVectors.put(entry.getKey(), entry.getValue().get(0));
			}
			else
			{
				double[] sum = new double[dimension];
				List<double[]> vectors = entry.getValue();
				for (double[] vector : vectors)
				{
					for (int j = 0; j < dimension; ++j)
					{
						sum[j] += vector[j];
					}
				}

				double[] mean = new double[dimension];
				for (int i = 0; i < dimension; ++i)
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
		if (args.length < 2)
		{
			System.err.println("Wrong number of args. Usage: embedutils command [params]");
			System.exit(-1);
		}

		switch (args[0])
		{
			case "merge":
			{
				if (args.length != 3)
				{
					System.err.println("Wrong number of args. Usage: embedutils merge embeddings_file output_file");
					System.exit(-1);
				}

				// Read command-line args
				Path embeddingsPath = Paths.get(args[1]);
				if (!Files.exists(embeddingsPath) || !Files.isRegularFile(embeddingsPath))
				{
					System.err.println("Cannot open " + embeddingsPath);
					System.exit(-1);
				}

				Path outPath = Paths.get(args[2]);
				if (!Files.isRegularFile(outPath))
				{
					System.err.println("Cannot create " + outPath);
					System.exit(-1);
				}

				mergeEmbeddings(embeddingsPath, outPath);
				break;
			}
			case "subset":
			{
				if (args.length != 4)
				{
					System.err.println("Wrong number of args." +
							"Usage: embedutils subset embeddings_file synsets_file output_file");
					System.exit(-1);
				}

				Path embeddingsPath = Paths.get(args[1]);
				if (!Files.exists(embeddingsPath) || !Files.isRegularFile(embeddingsPath))
				{
					System.err.println("Cannot open " + embeddingsPath);
					System.exit(-1);
				}

				Path synsetsPath = Paths.get(args[2]);
				if (!Files.exists(synsetsPath) || !Files.isRegularFile(synsetsPath))
				{
					System.err.println("Cannot open " + synsetsPath);
					System.exit(-1);
				}

				Path outPath = Paths.get(args[3]);
				subsetEmbeddings(embeddingsPath, synsetsPath, outPath);
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
