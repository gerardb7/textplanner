package edu.upf.taln.textplanning.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.AnnotatedTree;
import edu.upf.taln.textplanning.datastructures.OrderedTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides methods for manipulation of word and sense embedding files.
 */
public class EmbeddingUtils
{
	private static final int dimension = 400;
	private static final int expectedVectors = 1734862;
	private final static Logger log = LoggerFactory.getLogger(EmbeddingUtils.class);

	/**
	 * Given a file containing vectors for pairs of word and senses, produces a file with a mean vector for each sense
	 *
	 * @param inEmbeddingsPath path to the input file
	 * @param inOutPath        path to the merged file
	 * @throws IOException
	 */
	public static void mergeEmbeddings(Path inEmbeddingsPath, Path inOutPath) throws IOException
	{
		Map<String, List<double[]>> allVectors = parseEmbeddingsFile(inEmbeddingsPath, true);
		Map<String, double[]> meanVectors = averageEmbeddings(allVectors);
		writeEmbeddingsToFile(meanVectors, inOutPath);
	}

	/**
	 * Given a file containing sense embeddings and a conll file, produces a smaller embeddings file containing only
	 * the vectors for the senses found in the conll file
	 *
	 * @param inEmbeddingsPath path to the file containing the embeddings
	 * @param inConllPath      path to the conll file
	 * @param inOutPath        path to the new embeddings file
	 * @throws Exception
	 */
	public static void subsetEmbeddings(Path inEmbeddingsPath, Path inConllPath, Path inOutPath) throws Exception
	{
		Map<String, List<double[]>> allVectors = parseEmbeddingsFile(inEmbeddingsPath, true);

		log.info("Calculating subset");
		Stopwatch timer = Stopwatch.createStarted();
		ConLLAcces reader = new ConLLAcces();
		String conll = new String(Files.readAllBytes(inConllPath), Charset.forName("UTF-8"));
		List<AnnotatedTree> trees = reader.readSemanticTrees(conll);

		//TODO consider removing code manipulating IRIs
		// Collect all senses in conll
		Set<String> senses = trees.stream()
				.map(AnnotatedTree::getPreOrder)
				.flatMap(List::stream)
				.map(OrderedTree.Node::getData)
				.map(AnnotatedEntity::getEntityLabel)
//				.map(factory::createIRI)
//				.map(IRI::getLocalName)
				.map(s -> {
					if (s.startsWith("s"))
					{
						s = "bn:" + s.substring(1, s.length());
					}
					if (!s.startsWith("bn:"))
					{
						s = "bn:" + s;
					}

					return s;
				}) // concat with '+' inefficient but not that many senses
				.distinct()
				.collect(Collectors.toSet());

		// Filter vectors to those in the conll
		Map<String, List<double[]>> subsetVectors = allVectors.entrySet().stream()
				.filter(e -> senses.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		log.info("Subset creation took " + timer.stop());


		// Average and store vectors to file
		Map<String, double[]> meanVectors = averageEmbeddings(subsetVectors);
		writeEmbeddingsToFile(meanVectors, inOutPath);
	}

	/**
	 * Reads a file containing embeddings.
	 *
	 * @param inEmbeddingsPath path to file containing the embeddings
	 * @param inSensesOnly     if true vectors for words are discarded
	 * @return a map containing all vectors
	 * @throws IOException
	 */
	public static Map<String, List<double[]>> parseEmbeddingsFile(Path inEmbeddingsPath, boolean inSensesOnly) throws IOException
	{
		int numVectorsRead = 0;
		int numVectorsKept = 0;

		HashMap<String, List<double[]>> allVectors = new HashMap<>();
		FileReader fileReader = new FileReader(inEmbeddingsPath.toFile());
		BufferedReader bufferReader = new BufferedReader(fileReader);
		String line;

		while ((line = bufferReader.readLine()) != null)
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
			if (!inSensesOnly || columns[0].contains("bn:"))
			{
				String babelnetId = columns[0];
				if (!babelnetId.startsWith("bn:"))
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
				log.debug(numVectorsRead + " vectors readGraphs");
			}
		}
		log.info("Parsing complete: " + numVectorsRead + " vectors readGraphs (expected " + expectedVectors + "), and " + numVectorsKept + " vectors stored");

		return allVectors;
	}

	/**
	 * Writes veectors to a file
	 *
	 * @param inVectors the vectors
	 * @param inOutPath path to the output file
	 * @throws FileNotFoundException
	 */
	public static void writeEmbeddingsToFile(Map<String, double[]> inVectors, Path inOutPath) throws FileNotFoundException
	{
		// Create String representation of each averaged vector and writeSemanticDAGs them to a file
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
	public static Map<String, double[]> averageEmbeddings(Map<String, List<double[]>> inVectors)
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
							"Usage: embedutils subset embeddings_file conll_file output_file");
					System.exit(-1);
				}

				Path embeddingsPath = Paths.get(args[1]);
				if (!Files.exists(embeddingsPath) || !Files.isRegularFile(embeddingsPath))
				{
					System.err.println("Cannot open " + embeddingsPath);
					System.exit(-1);
				}

				Path conllPath = Paths.get(args[2]);
				if (!Files.exists(conllPath) || !Files.isRegularFile(conllPath))
				{
					System.err.println("Cannot open " + conllPath);
					System.exit(-1);
				}

				Path outPath = Paths.get(args[3]);
				if (!Files.isRegularFile(outPath))
				{
					System.err.println("Cannot create " + outPath);
					System.exit(-1);
				}

				subsetEmbeddings(embeddingsPath, conllPath, outPath);
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
