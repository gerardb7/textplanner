package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Returns frequencies from a JSON file with pre-computed values.
 */
public class FreqsFile implements Corpus
{
	private final long numDocs;
	private final Map<String, Long> sense_counts = new HashMap<>();
	private final Map<String, Long> sense_doc_counts = new HashMap<>();
	private final Map<String, Map<String, Long>> form_sense_counts = new HashMap<>();
	private final static Logger log = LoggerFactory.getLogger(FreqsFile.class);

	public FreqsFile(Path file) throws IOException
	{
		log.info("Loading frequencies file " + file);
		Stopwatch timer = Stopwatch.createStarted();

		String json = FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8);
		JSONObject obj = new JSONObject(json);
		numDocs = obj.getLong("docs");
		JSONObject form_counts = obj.getJSONObject("mentions");

		int c = 0;
		for (String form : form_counts.keySet())
		{
			if (++c % 100000 == 0)
				log.info(c + " mentions loaded");
			JSONArray arr = form_counts.getJSONArray(form);
			Map<String, Long> counts = new HashMap<>();

			for (int i = 0; i < arr.length(); ++i)
			{
				JSONArray form_count = arr.getJSONArray(i);
				String key = form_count.getString(0);
				long value = form_count.getLong(1);
				counts.put(key, value);
				sense_counts.merge(key, 1L, (c1, c2) -> c1 + c2);
			}
			form_sense_counts.put(form, counts);
		}

		JSONObject entity_counts = obj.getJSONObject("entities");
		c = 0;

		for (String id : entity_counts.keySet())
		{
			if (++c % 100000 == 0)
				log.info(c + " entities loaded");
			long count = entity_counts.getLong(id);
			sense_doc_counts.put(id, count);
		}

		log.info("Loaded " + sense_counts.size() + " entities and " + form_sense_counts.size() + " forms in " + timer.stop());
	}

	@Override
	public long getEntityCount(String entity)
	{
		if (!sense_counts.containsKey(entity))
			log.warn("No counts for entity " + entity);
		return sense_counts.getOrDefault(entity, 0L);
	}

	@Override
	public long getEntityDocumentCount(String entity)
	{
		if (!sense_doc_counts.containsKey(entity))
			log.warn("No document counts for entity " + entity);
		return sense_doc_counts.getOrDefault(entity, 0L);
	}

	@Override
	public long getFormEntityCount(String form, String entity)
	{
		if (!form_sense_counts.containsKey(form) || !form_sense_counts.get(form).containsKey(entity))
			log.warn("No counts for form " + form + " and entity " + entity);

		return form_sense_counts.getOrDefault(form, new HashMap<>()).getOrDefault(entity, 0L);
	}

	@Override
	public long getNumDocs()
	{
		return numDocs;
	}

	public Set<String> getEntitiesForForm(String form)
	{
		if (form_sense_counts.containsKey(form))
			return form_sense_counts.get(form).keySet();
		else
			return Collections.emptySet();
	}

	// for testing
	public static void main(String[] args) throws IOException
	{
		FreqsFile f = new FreqsFile(Paths.get(args[0]));
	}
}
