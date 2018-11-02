package edu.upf.taln.textplanning.amr.input;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.core.structures.Candidate;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
	private final static Logger log = LogManager.getLogger();

	public DBPediaType()
	{
		log.info("Setting up connection to DBpedia SPARQL endpoint");
		Stopwatch timer = Stopwatch.createStarted();
		SPARQLRepository repo = new SPARQLRepository(url);
		repo.initialize();
		conn = repo.getConnection();
		log.info("DBpedia access set up in " + timer.stop());
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
			log.error("SPARQL queries for resource " + r + " failed, defaulting to type 'Binary_RandomAccess': " + e);
			return Candidate.Type.Other;
		}
	}
}
