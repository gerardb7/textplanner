package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.corpora.FreqsFile;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.GraphList;
import edu.upf.taln.textplanning.structures.Mention;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathConverter;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathToNewFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * Utils class for running tests with resources related to frequencies, such as the Solr index of SEW or freq files.
 */
public class FrequencyUtils
{
//	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private final static Logger log = LogManager.getLogger(FrequencyUtils.class);

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Graphs file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> graphs;
		@Parameter(names = {"-i", "-inputFile"}, description = "Input frequencies file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output frequencies file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = PathToNewFile.class)
		private List<Path> outputFile;
	}

	/**
	 */
	private static void getFrequenciesSubset(Path graphs_file, Path inputFile, Path outputFile) throws IOException, ClassNotFoundException
	{
		log.info("Reading graphs");
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		List<String> forms = graphs.getCandidates().stream()
				.map(Candidate::getMention)
				.map(Mention::getSurfaceForm)
				.distinct()
				.collect(Collectors.toList());

		log.info("Reading frequencies file");
		FreqsFile freqsFile = new FreqsFile(inputFile);

		Map<String, Map<String, Long>> formCounts = forms.stream()
				.collect(toMap(f -> f, f -> freqsFile.getMeaningsForForm(f).stream()
						.collect(toMap(e -> e, e -> freqsFile.getFormMeaningCount(f, e)))));

		Map<String, Long> meaningCounts = formCounts.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(toMap(e -> e, freqsFile::getMeaningCount, (c1, c2) -> c1));

		// @todo rewrite using GJson
		JSONObject top = new JSONObject();
		top.put("docs", freqsFile.getNumDocs());
		top.put("meanings", meaningCounts);

		JSONObject jForms = new JSONObject();
		for (String form : formCounts.keySet())
		{
			Map<String, Long> counts = formCounts.get(form);
			JSONArray jCounts = new JSONArray();

			for (String meaning : counts.keySet())
			{
				Long count = counts.get(meaning);
				JSONArray jCount = new JSONArray();
				jCount.put(meaning);
				jCount.put(count);
				jCounts.put(jCount);
			}

			jForms.put(form, jCounts);
		}

		top.put("mentions", jForms);
		log.info("Done.");

		FileUtils.writeStringToFile(outputFile.toFile(), top.toString(), StandardCharsets.UTF_8);
		log.info("Json written to file" + outputFile);
	}

	private static void compactJSONFreqs(Path json_file, Path binary_file) throws IOException
	{
		Stopwatch timer = Stopwatch.createStarted();

		String json = FileUtils.readFileToString(json_file.toFile(), StandardCharsets.UTF_8);
		JSONObject obj = new JSONObject(json);
		long numDocs = obj.getLong("docs");

		Map<String, Pair<Long, Long>> sense_counts = new HashMap<>();
		JSONObject meaning_counts = obj.getJSONObject("meanings");
		for (String id : meaning_counts.keySet())
		{
			long count = meaning_counts.getLong(id);
			sense_counts.put(id, Pair.of(0L, count));
		}

		JSONObject form_counts = obj.getJSONObject("mentions");

		Map<String, Map<String, Long>> form_sense_counts = new HashMap<>();
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
				Pair<Long, Long> ci = sense_counts.get(key);
				sense_counts.put(key, Pair.of(ci.getLeft() + 1, ci.getRight()));
			}
			form_sense_counts.put(form, counts);
		}

		log.info(   "Loaded " + sense_counts.size() + " meanings and " + form_sense_counts.size() +
				" forms from frequencies file in " + timer.stop());

		CompactFrequencies freqs = new CompactFrequencies(sense_counts);
		Serializer.serialize(freqs, binary_file);

		log.info("Done");
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		FrequencyUtils.getFrequenciesSubset(cmlArgs.graphs.get(0), cmlArgs.inputFile.get(0),
				cmlArgs.outputFile.get(0));
	}
}
