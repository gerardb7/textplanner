package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.*;
import edu.upf.taln.textplanning.corpora.SEWSolr;
import edu.upf.taln.textplanning.disambiguation.BabelNetAnnotator;
import edu.upf.taln.textplanning.input.CoNLLFormat;
import edu.upf.taln.textplanning.structures.AnnotatedWord;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.commons.io.FileUtils.readFileToString;

/**
 * Utils class for running tests with the Solr index of SEW.
 */
public class SEWSolrUtils
{
	public static class PathConverter implements IStringConverter<Path>
	{
		@Override
		public Path convert(String value)
		{
			return Paths.get(value);
		}
	}

	public static class PathToNewFile implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (Files.exists(path) || Files.isDirectory(path))
			{
				throw new ParameterException("Cannot create file " + name + " = " + value);
			}
		}
	}

	public static class PathToExistingFolder implements IParameterValidator
	{
		@Override
		public void validate(String name, String value) throws ParameterException
		{
			Path path = Paths.get(value);
			if (!Files.exists(path) || !Files.isDirectory(path))
			{
				throw new ParameterException("Cannot open folder " + name + " = " + value);
			}
		}
	}

	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private final static Logger log = LoggerFactory.getLogger(SEWSolrUtils.class);

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Input folder", arity = 1, required = true, converter = PathConverter.class,
				validateWith = PathToExistingFolder.class)
		private List<Path> input;
		@Parameter(names = {"-e", "-extension"}, description = "Extension used to filter files", arity = 1, required = true)
		private List<String> extension;
		@Parameter(names = {"-o", "-output"}, description = "Output file", arity = 1, required = true, converter = PathConverter.class,
				validateWith = PathToNewFile.class)
		private List<Path> output;
	}

	/**
	 * Reads conll files under a base folder and queries the frequency of item in them.
	 * @param basePath base path to be searched recursively
	 * @param extension only files matching this extension will be considered
	 * @param outputFile path to new file where frequencies are stored
	 */
	private static void queryFrequencies(Path basePath, String extension, Path outputFile) throws IOException
	{
		log.info("Quering frequencies for entities in " + basePath);
		CoNLLFormat conll = new CoNLLFormat(); // assuming surface conlls with forms in second column
		BabelNetAnnotator bn = new BabelNetAnnotator();
		SEWSolr sew = new SEWSolr(solrUrl);
		StringWriter w = new StringWriter();

		// first line contains number of docs
		w.append(Long.toString(sew.getNumDocs()));
		w.append("\n");

		// Second line is a long list of entity keys and corresponding frequencies
		log.info("Reading files");
		Set<LinguisticStructure> structures = Files.walk(basePath)
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

		// Annotate candidate entities
		log.info("Annotating candidates");
		bn.annotateCandidates(structures);

		List<String> entities = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.map(AnnotatedWord::getCandidates)
				.flatMap(Set::stream)
				.map(Candidate::getEntity)
				.map(Entity::getId)
				.distinct()
				.collect(Collectors.toList());

		structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.filter(n -> n.getCandidates().isEmpty())
				.map(AnnotatedWord::getForm)
				.distinct()
				.forEach(entities::add);

		log.info("Running queries");
		List<String> freqs = entities.stream()
				.peek(e -> log.info("Query for " + e + " " + entities.indexOf(e) + "/" + entities.size()))
				.mapToLong(sew::getFrequency)
				.peek(f -> log.info("f=" + f))
				.mapToObj(Long::toString)
				.collect(Collectors.toList());
		IntStream.range(0, entities.size())
				.forEach(i -> {
					w.append(entities.get(i));
					w.append("=");
					w.append(freqs.get(i));
					w.append("\n");
				});
		log.info("Done.");

		FileUtils.writeStringToFile(outputFile.toFile(), w.toString(), StandardCharsets.UTF_8);
	}

	public static void main(String[] args) throws IOException
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		SEWSolrUtils.queryFrequencies(cmlArgs.input.get(0), cmlArgs.extension.get(0), cmlArgs.output.get(0));
	}
}
