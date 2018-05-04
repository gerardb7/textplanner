package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.corpora.CompactFrequencies;
import edu.upf.taln.textplanning.corpora.FreqsFile;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.amr.GraphList;
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
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * Utils class for running tests with resources related to frequencies, such as the Solr index of SEW or freq files.
 */
public class FrequencyUtils
{
	private final static Logger log = LogManager.getLogger(FrequencyUtils.class);

	/**
	 */
	private static void getFrequenciesSubset(Path graphs_file, Path inputFile, Path outputFile) throws IOException, ClassNotFoundException
	{
		log.info("Reading graphs");
		GraphList graphs = (GraphList) Serializer.deserialize(graphs_file);

		List<String> forms = graphs.getCandidates().stream()
				.map(Candidate::getMention)
				.map(Mention::getSurface_form)
				.distinct()
				.collect(Collectors.toList());

		log.info("Reading frequencies file");
		FreqsFile freqsFile = new FreqsFile(inputFile);

		Map<String, Map<String, OptionalInt>> formCounts = forms.stream()
				.collect(toMap(f -> f, f -> freqsFile.getMeaningsForForm(f).stream()
						.collect(toMap(e -> e, e -> freqsFile.getFormMeaningCount(f, e)))));

		Map<String, OptionalInt> meaningCountsOpt = formCounts.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(toMap(e -> e, freqsFile::getMeaningCount, (c1, c2) -> c1));

		@SuppressWarnings("ConstantConditions")
		Map<String, Integer> meaningCounts = meaningCountsOpt.entrySet().stream()
				.filter(e -> e.getValue().isPresent())
				.collect(toMap(Map.Entry::getKey, e-> e.getValue().getAsInt()));

		// @todo rewrite using GJson
		JSONObject top = new JSONObject();
		top.put("docs", freqsFile.getNumDocs());
		top.put("meanings", meaningCounts);

		JSONObject jForms = new JSONObject();
		for (String form : formCounts.keySet())
		{
			Map<String, OptionalInt> counts = formCounts.get(form);
			JSONArray jCounts = new JSONArray();

			for (String meaning : counts.keySet())
			{
				OptionalInt count = counts.get(meaning);
				if (count.isPresent())
				{
					JSONArray jCount = new JSONArray();
					jCount.put(meaning);
					jCount.put(count.getAsInt());
					jCounts.put(jCount);
				}
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
		int numDocs = (int) obj.getLong("docs");

		Map<String, Pair<Integer, Integer>> sense_counts = new HashMap<>();
		JSONObject meaning_counts = obj.getJSONObject("meanings");
		for (String id : meaning_counts.keySet())
		{
			int count = (int) meaning_counts.getLong(id);
			sense_counts.put(id, Pair.of(0, count));
		}

		JSONObject form_counts = obj.getJSONObject("mentions");

		Map<String, Map<String, Integer>> form_sense_counts = new HashMap<>();
		for (String form : form_counts.keySet())
		{
			JSONArray arr = form_counts.getJSONArray(form);
			Map<String, Integer> counts = new HashMap<>();

			for (int i = 0; i < arr.length(); ++i)
			{
				JSONArray form_count = arr.getJSONArray(i);
				String key = form_count.getString(0);
				int value = (int) form_count.getLong(1);
				counts.put(key, value);
				Pair<Integer, Integer> ci = sense_counts.get(key);
				sense_counts.put(key, Pair.of(ci.getLeft() + 1, ci.getRight()));
			}
			form_sense_counts.put(form, counts);
		}

		log.info(   "Loaded " + sense_counts.size() + " meanings and " + form_sense_counts.size() +
				" forms from frequencies file in " + timer.stop());

		CompactFrequencies freqs = new CompactFrequencies(numDocs, sense_counts, form_sense_counts);
		Serializer.serialize(freqs, binary_file);

		log.info("Done");
	}

	@Parameters(commandDescription = "Create a subset of a JSON frequencies file based on a file containing graphs")
	private static class SubsetCommand
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

	@Parameters(commandDescription = "Convert a JSON frequencies file to a compact binary file")
	private static class ConvertCommand
	{
		@Parameter(names = {"-i", "-inputFile"}, description = "Input JSON file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output binary file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = PathToNewFile.class)
		private List<Path> outputFile;
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException
	{
		SubsetCommand subset = new SubsetCommand();
		ConvertCommand convert = new ConvertCommand();

		JCommander jc = new JCommander();
		jc.addCommand("subset", subset);
        jc.addCommand("convert", convert);
		jc.parse(args);

		if (jc.getParsedCommand().equals("subset"))
			FrequencyUtils.getFrequenciesSubset(subset.graphs.get(0), subset.inputFile.get(0), subset.outputFile.get(0));
		else if (jc.getParsedCommand().equals("convert"))
			FrequencyUtils.compactJSONFreqs(convert.inputFile.get(0), subset.outputFile.get(0));
	}
}
