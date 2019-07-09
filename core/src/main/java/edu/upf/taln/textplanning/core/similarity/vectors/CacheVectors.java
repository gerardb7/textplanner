package edu.upf.taln.textplanning.core.similarity.vectors;

import com.google.common.collect.Iterables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Proxy class for caching vectors and storing them into a text file.
 * Useful for creating subsets of large embeddings.
 */
public class CacheVectors extends Vectors
{
	private final Vectors base;
	private final Map<String, double []> cache = new HashMap<>();
	private final Path output_file;
	private final static Logger log = LogManager.getLogger();

	public CacheVectors(Vectors base, Path output_file)
	{
		this.base = base;
		this.output_file = output_file;
	}

	@Override
	public boolean isDefinedFor(String item)
	{
		return base.isDefinedFor(item);
	}

	@Override
	public int getNumDimensions()
	{
		return base.getNumDimensions();
	}

	@Override
	public Optional<double[]> getVector(String item)
	{
		final Optional<double[]> vector = base.getVector(item);
		vector.ifPresent(a -> cache.putIfAbsent(item, a));
		return vector;
	}

	// Writes in a word2vec text format
	public void writeVectors() throws FileNotFoundException
	{
		// Create String representation of each averaged vector and writeGraphs them to a file
		PrintWriter out = new PrintWriter(output_file.toFile());
		DecimalFormat formatter = new DecimalFormat("#0.000000");
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
		symbols.setDecimalSeparator('.');
		formatter.setDecimalFormatSymbols(symbols);

		int num_dimensions = cache.values().iterator().next().length;

		for (Iterable<Map.Entry<String, double[]>> chunk : Iterables.partition(cache.entrySet(), 10000))
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
		}

		out.close();
		log.info("Wrote " + cache.size() + " vectors to " + output_file);
	}
}
