package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
	private final static Logger log = LogManager.getLogger(FreqsFile.class);

	public FreqsFile(Path file) throws IOException
	{
		Stopwatch timer = Stopwatch.createStarted();

		String json = FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8);
		JSONObject obj = new JSONObject(json);
		numDocs = obj.getLong("docs");
		JSONObject form_counts = obj.getJSONObject("mentions");

		for (String form : form_counts.keySet())
		{
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

		JSONObject meaning_counts = obj.getJSONObject("meanings");
		for (String id : meaning_counts.keySet())
		{
			long count = meaning_counts.getLong(id);
			sense_doc_counts.put(id, count);
		}

		log.info(   "Loaded " + sense_counts.size() + " meanings and " + form_sense_counts.size() +
					" forms from frequencies file in " + timer.stop());
	}

	@Override
	public boolean hasMeaning(String meaning)
	{
		return sense_counts.containsKey(meaning);
	}

	@Override
	public boolean hasMeaningDocument(String meaning)
	{
		return sense_doc_counts.containsKey(meaning);
	}

	@Override
	public boolean hasFormMeaning(String form, String meaning)
	{
		return form_sense_counts.containsKey(form) && form_sense_counts.get(form).containsKey(meaning);
	}

	@Override
	public boolean hasForm(String form)
	{
		return form_sense_counts.containsKey(form);
	}

	@Override
	public long getMeaningCount(String meaning)
	{
		return sense_counts.getOrDefault(meaning, 0L);
	}

	@Override
	public long getMeaningDocumentCount(String meaning)
	{
		return sense_doc_counts.getOrDefault(meaning, 0L);
	}

	@Override
	public long getFormMeaningCount(String form, String meaning)
	{
		return form_sense_counts.getOrDefault(form, new HashMap<>()).getOrDefault(meaning, 0L);
	}

	@Override
	public long getFormCount(String form)
	{
		return form_sense_counts.getOrDefault(form, new HashMap<>()).values().stream().mapToLong(l -> l).sum();
	}

	@Override
	public long getNumDocs()
	{
		return numDocs;
	}

	public Set<String> getMeaningsForForm(String form)
	{
		if (form_sense_counts.containsKey(form))
			return form_sense_counts.get(form).keySet();
		else
			return Collections.emptySet();
	}

	// for testing
	public static void main(String[] args) throws IOException
	{
		new FreqsFile(Paths.get(args[0]));
	}
}
