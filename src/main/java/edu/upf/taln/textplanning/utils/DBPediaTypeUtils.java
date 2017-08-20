package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import edu.upf.taln.textplanning.input.CoNLLReader;
import edu.upf.taln.textplanning.structures.AnnotatedWord;
import edu.upf.taln.textplanning.structures.Candidate;
import edu.upf.taln.textplanning.structures.Entity;
import edu.upf.taln.textplanning.structures.LinguisticStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.readFileToString;

public class DBPediaTypeUtils
{
	private final static Logger log = LoggerFactory.getLogger(DBPediaTypeUtils.class);

	private static void getTypes(Path structuresPath, String extension, Path o) throws IOException
	{
		log.info("Reading structures");
		CoNLLReader conll = new CoNLLReader();
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

		List<String> references = structures.stream()
				.map(LinguisticStructure::vertexSet)
				.flatMap(Set::stream)
				.map(AnnotatedWord::getCandidates)
				.flatMap(Set::stream)
				.map(Candidate::getEntity)
				.map(Entity::getReference)
				.distinct()
				.collect(Collectors.toList());
	}

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static class CMLArgs
	{
		@Parameter(description = "Structures folder", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFolder.class)
		private List<Path> structures;
		@Parameter(names = {"-e", "-extension"}, description = "Extension used to filter files", arity = 1, required = true)
		private List<String> extension;
		@Parameter(names = {"-o", "-outputFile"}, description = "Output types file", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToNewFile.class)
		private Path outputFile;
	}

	public static void main(String[] args) throws IOException
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		getTypes(cmlArgs.structures.get(0), cmlArgs.extension.get(0), cmlArgs.outputFile);
	}
}
