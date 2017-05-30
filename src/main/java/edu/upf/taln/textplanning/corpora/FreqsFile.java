package edu.upf.taln.textplanning.corpora;

import edu.upf.taln.textplanning.datastructures.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Returns frequencies from a file with pre-computed values.
 */
public class FreqsFile implements Corpus
{
	private final long numDocs;
	private final Map<String, Long> freqs = new HashMap<>();
	private final static Logger log = LoggerFactory.getLogger(FreqsFile.class);

	public FreqsFile(Path file) throws IOException
	{
		log.info("Loading frequencies file " + file);
		FileInputStream fs = new FileInputStream(file.toFile());
		InputStreamReader isr = new InputStreamReader(fs, StandardCharsets.UTF_8);
		BufferedReader br = new BufferedReader(isr);

		// First line has number of docs
		String line = br.readLine();
		numDocs = Long.valueOf(line);

		// Remaining lines have frequencies
		while ((line = br.readLine()) != null)
		{
			String[] parts = line.split("=");
			freqs.put(parts[0].toLowerCase(), Long.valueOf(parts[1]));
		}
	}

	@Override
	public long getFrequency(Entity inEntity)
	{
		if (!freqs.containsKey(inEntity.getEntityLabel().toLowerCase()))
		{
			log.warn("No frequency for entity " + inEntity);
			return 0;
		}
		return freqs.get(inEntity.getEntityLabel().toLowerCase());
	}

	@Override
	public long getNumDocs()
	{
		return numDocs;
	}
}
