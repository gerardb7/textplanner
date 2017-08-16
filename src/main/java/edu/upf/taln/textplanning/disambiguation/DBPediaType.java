package edu.upf.taln.textplanning.disambiguation;

import edu.upf.taln.textplanning.structures.Candidate.Type;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

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


	public DBPediaType()
	{
		SPARQLRepository repo = new SPARQLRepository(url);
		repo.initialize();
		conn = repo.getConnection();
	}

	// Todo increase coverage with yago/Wordnet classes, see https://stackoverflow.com/questions/26008217/yago-ontology-for-entity-disambiguation
	public Type getType(String r)
	{
		String uri = r.replace("DBpedia", "dbpedia");
		BooleanQuery isPersonQuery = conn.prepareBooleanQuery(isPerson.replace("$r", uri));
		BooleanQuery isPlaceQuery = conn.prepareBooleanQuery(isPlace.replace("$r", uri));
		BooleanQuery isOrganizationQuery = conn.prepareBooleanQuery(isOrganization.replace("$r", uri));

		if (isPersonQuery.evaluate())
			return Type.Person;
		else if (isPlaceQuery.evaluate())
			return Type.Location;
		else if (isOrganizationQuery.evaluate())
			return Type.Organization;
		else
			return Type.Other;
	}
}
