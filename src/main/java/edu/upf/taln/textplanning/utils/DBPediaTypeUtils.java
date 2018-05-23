package edu.upf.taln.textplanning.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import edu.upf.taln.textplanning.input.DBPediaType;
import edu.upf.taln.textplanning.input.AMRReader;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.amr.GraphList;
import edu.upf.taln.textplanning.input.GraphListFactory;
import edu.upf.taln.textplanning.structures.Meaning;
import it.uniroma1.lcl.babelnet.BabelNet;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetID;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.io.FileUtils.readFileToString;

public class DBPediaTypeUtils
{
	private final static Logger log = LogManager.getLogger(DBPediaTypeUtils.class);

	private static void getTypes(Path amrPath, String extension, Path o, Path babel_config) throws IOException
	{
		log.info("Reading structures");
		GraphListFactory factory = new GraphListFactory(new AMRReader(), null, babel_config);
		List<GraphList> graphs = Files.walk(amrPath.toAbsolutePath())
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
				.map(factory::getGraphs) // includes NER+Coref processing with stanford
				.collect(Collectors.toList());

		log.info("Collecting meanings");
		List<String> meanings = graphs.stream()
				.map(GraphList::getCandidates)
				.flatMap(Collection::stream)
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toList());

		// Get their types
		log.info("Querying types for " + meanings.size() + " meanings");
		BabelNet bn = BabelNet.getInstance();
		DBPediaType type = new DBPediaType();
		AtomicInteger counter = new AtomicInteger();

		Map<String, String> types = meanings.stream()
				.map(r -> {
					try
					{
						BabelSynset synset = bn.getSynset(new BabelSynsetID(r));
						List<String> dbPediaURIs = synset.getDBPediaURIs(Language.EN);
						Candidate.Type t = Candidate.Type.Other;
						if (!dbPediaURIs.isEmpty())
						{
							t = type.getType(dbPediaURIs.get(0));
						}

						if (counter.incrementAndGet() % 1000 == 0)
							log.info(counter.get() + " types queried");

						return Pair.of(r, t.toString());
					}
					catch (Exception e) { throw new RuntimeException(e); }
				})
				.collect(toMap(Pair::getLeft, Pair::getRight));

		log.info("Writing file");
		Gson gson = new Gson();
		String jsonNames = gson.toJson(types);
		FileUtils.writeStringToFile(o.toFile(), jsonNames, StandardCharsets.UTF_8);

		long location_count = types.values().stream()
				.map(Candidate.Type::valueOf)
				.filter(t -> t == Candidate.Type.Location)
				.count();
		long person_count = types.values().stream()
				.map(Candidate.Type::valueOf)
				.filter(t -> t == Candidate.Type.Person)
				.count();
		long organization_count = types.values().stream()
				.map(Candidate.Type::valueOf)
				.filter(t -> t == Candidate.Type.Organization)
				.count();
		log.info("Got " + location_count + " locations, " + person_count + " persons, " + organization_count +
				" organizations and " + (types.size() - location_count - person_count - organization_count) + " other");
		log.info("Done.");
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
		@Parameter(names = {"-b", "-babelconfig"}, description = "BabelNet configuration folder", arity = 1, required = true, converter = CMLCheckers.PathConverter.class,
				validateWith = CMLCheckers.PathToExistingFolder.class)
		private Path bnFolder;
	}

	public static void main(String[] args) throws IOException
	{
		CMLArgs cmlArgs = new CMLArgs();
		new JCommander(cmlArgs, args);

		getTypes(cmlArgs.structures.get(0), cmlArgs.extension.get(0), cmlArgs.outputFile, cmlArgs.bnFolder);
	}
}
