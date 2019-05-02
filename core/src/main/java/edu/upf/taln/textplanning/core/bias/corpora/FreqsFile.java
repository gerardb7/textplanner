package edu.upf.taln.textplanning.core.bias.corpora;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads frequencies from JSON file with pre-computed values and stores them into memory.
 */
public class FreqsFile implements Corpus
{
	private final int numDocs;
	private final Map<String, Integer> sense_counts = new HashMap<>();
	private final Map<String, Integer> sense_doc_counts = new HashMap<>();
	private final Map<String, Map<String, Integer>> form_sense_counts = new HashMap<>();
	private final static Logger log = LogManager.getLogger();

	public FreqsFile(Path file) throws IOException
	{
		Stopwatch timer = Stopwatch.createStarted();

		String json = FileUtils.readFileToString(file.toFile(), StandardCharsets.UTF_8);
		JSONObject obj = new JSONObject(json);
		numDocs = (int) obj.getLong("docs");
		JSONObject form_counts = obj.getJSONObject("mentions");

		for (String form : form_counts.keySet())
		{
			JSONArray arr = form_counts.getJSONArray(form);
			Map<String, Integer> counts = new HashMap<>();

			for (int i = 0; i < arr.length(); ++i)
			{
				JSONArray form_count = arr.getJSONArray(i);
				String key = form_count.getString(0);
				int value = (int)form_count.getLong(1);
				counts.put(key, value);
				sense_counts.merge(key, 1, (c1, c2) -> c1 + c2);
			}
			form_sense_counts.put(form, counts);
		}

		JSONObject meaning_counts = obj.getJSONObject("meanings");
		for (String id : meaning_counts.keySet())
		{
			int count = (int)meaning_counts.getLong(id);
			sense_doc_counts.put(id, count);
		}

		log.info(   "Loaded " + sense_counts.size() + " meanings and " + form_sense_counts.size() +
					" forms from frequencies file in " + timer.stop());
	}

	@Override
	public OptionalInt getMeaningCount(String meaning)
	{
		if (!sense_counts.containsKey(meaning))
			return OptionalInt.empty();

		return OptionalInt.of(sense_counts.get(meaning));
	}

	@Override
	public OptionalInt getMeaningDocumentCount(String meaning)
	{
		if (!sense_doc_counts.containsKey(meaning))
			return OptionalInt.empty();
		return OptionalInt.of(sense_doc_counts.get(meaning));
	}

	@Override
	public OptionalInt getFormMeaningCount(String form, String meaning)
	{
		if (!form_sense_counts.containsKey(form) || !form_sense_counts.get(form).containsKey(meaning))
			return OptionalInt.empty();
		return OptionalInt.of(form_sense_counts.get(form).get(meaning));
	}

	@Override
	public OptionalInt getFormCount(String form)
	{
		if (!form_sense_counts.containsKey(form))
			return OptionalInt.empty();
		return  OptionalInt.of(form_sense_counts.get(form).values().stream().mapToInt(l -> l).sum());
	}

	@Override
	public int getNumDocs()
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

}
