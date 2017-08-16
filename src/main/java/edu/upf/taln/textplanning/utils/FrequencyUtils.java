package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import edu.upf.taln.textplanning.corpora.FreqsFile;
import edu.upf.taln.textplanning.input.CoNLLFormat;
import edu.upf.taln.textplanning.structures.AnnotatedWord;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathConverter;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathToExistingFolder;
import edu.upf.taln.textplanning.utils.CMLCheckers.PathToNewFile;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.apache.commons.io.FileUtils.readFileToString;

/**
 * Utils class for running tests with resources related to frequencies, such as the Solr index of SEW or freq files.
 */
public class FrequencyUtils
{
//	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private final static Logger log = LoggerFactory.getLogger(FrequencyUtils.class);

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Structures folder", arity = 1, required = true, converter = PathConverter.class,
				validateWith = PathToExistingFolder.class)
		private List<Path> structures;
		@Parameter(names = {"-e", "-extension"}, description = "Extension used to filter files", arity = 1, required = true)
		private List<String> extension;
		@Parameter(names = {"-i", "-inputFile"}, description = "Input frequencies file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFile.class)
		private List<Path> inputFile;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output frequencies file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = PathToNewFile.class)
		private List<Path> outputFile;
	}

	/**
	 */
	private static void getFrequenciesSubset(Path structuresPath, String extension, Path inputFile, Path outputFile) throws IOException
	{
		log.info("Reading structures");
		CoNLLFormat conll = new CoNLLFormat();
		Set<LinguisticStructure> structures = Files.walk(structuresPath.toAbsolutePath())
				.filter(Files::isRegularFile)
				.filter(p -> p.toString().endsWith(extension))
				.map(p ->
				{
					try
					{
						return readFileToString(p.toFile(), StandardCharsets.UTF_8);
					}
					catch (Exception e)
					{
						throw new RuntimeException(e);
					}
				})
				.map(conll::readStructures)
				.flatMap(List::stream)
				.collect(Collectors.toSet());

		List<String> forms = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.map(AnnotatedWord::getForm)
				.distinct()
				.collect(Collectors.toList());

		log.info("Reading frequencies file");
		FreqsFile freqsFile = new FreqsFile(inputFile);

		Map<String, Map<String, Long>> formCounts = forms.stream()
				.collect(toMap(f -> f, f -> freqsFile.getEntitiesForForm(f).stream()
						.collect(toMap(e -> e, e -> freqsFile.getFormEntityCount(f, e)))));

		Map<String, Long> entityCounts = formCounts.values().stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.collect(toMap(e -> e, freqsFile::getEntityCount, (c1, c2) -> c1));

		JSONObject top = new JSONObject();
		top.put("docs", freqsFile.getNumDocs());
		top.put("entities", entityCounts);

		JSONObject jForms = new JSONObject();
		for (String form : formCounts.keySet())
		{
			Map<String, Long> counts = formCounts.get(form);
			JSONArray jCounts = new JSONArray();

			for (String entity : counts.keySet())
			{
				Long count = counts.get(entity);
				JSONArray jCount = new JSONArray();
				jCount.put(entity);
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


	public static void main(String[] args) throws IOException
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		FrequencyUtils.getFrequenciesSubset(cmlArgs.structures.get(0), cmlArgs.extension.get(0), cmlArgs.inputFile.get(0),
				cmlArgs.outputFile.get(0));
	}
}
