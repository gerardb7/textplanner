package edu.upf.taln.textplanning.disambiguation;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.upf.taln.textplanning.structures.Candidate;
import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 */
public class DBPediaType
{
	private static final String url = "http://dbpedia.org/sparql"; //"https://query.wikidata.org/sparql"; //
	private final static String isPerson = "ASK{ <$r> a <http://dbpedia.org/ontology/Person> }";
	private final static String isPlace = "ASK{ <$r> a <http://dbpedia.org/ontology/Place> }";
	private final static String isOrganization = "ASK{ <$r> a <http://dbpedia.org/ontology/Organisation> }";
	private final RepositoryConnection conn;
	private final Map<String, Candidate.Type> types = new HashMap<>();
	private final static Logger log = LoggerFactory.getLogger(DBPediaType.class);


	public DBPediaType()
	{
		conn = createConnection();
	}

	public DBPediaType(Path types_file) throws IOException
	{
		log.info("Setting up DBpedia types");
		Stopwatch timer = Stopwatch.createStarted();
		conn = createConnection();

		log.info("Reading DBpedia types from file");
		String json = FileUtils.readFileToString(types_file.toFile(), UTF_8);
		Gson gson = new Gson();
		Type type = new TypeToken<Map<String,String>>() {}.getType();
		Map<String,String> types_in_file = gson.fromJson(json, type);
		types_in_file.forEach((key, value) -> types.put(key, Candidate.Type.valueOf(value)));
		log.info(types.size() + " DBpedia types read from file");
		log.info("DBpedia set up completed in " + timer.stop());
	}

	private static RepositoryConnection createConnection()
	{
		log.info("Setting up connection to DBpedia SPARQL endpoint");
		SPARQLRepository repo = new SPARQLRepository(url);
		repo.initialize();
		return repo.getConnection();
	}

	// Todo increase coverage with yago/Wordnet classes, see https://stackoverflow.com/questions/26008217/yago-ontology-for-entity-disambiguation
	public Candidate.Type getType(String r)
	{
		try
		{
			String r2 = r.replace("DBpedia", "dbpedia");
			BooleanQuery isPersonQuery = conn.prepareBooleanQuery(isPerson.replace("$r", r2));
			BooleanQuery isPlaceQuery = conn.prepareBooleanQuery(isPlace.replace("$r", r2));
			BooleanQuery isOrganizationQuery = conn.prepareBooleanQuery(isOrganization.replace("$r", r2));

			if (isPersonQuery.evaluate())
				return Candidate.Type.Person;
			else if (isPlaceQuery.evaluate())
				return Candidate.Type.Location;
			else if (isOrganizationQuery.evaluate())
				return Candidate.Type.Organization;
			else
				return Candidate.Type.Other;
		}
		catch (Exception e)
		{
			log.error("SPARQL queries for resource " + r + " failed, defaulting to type 'Other': " + e);
			return Candidate.Type.Other;
		}
	}
}
