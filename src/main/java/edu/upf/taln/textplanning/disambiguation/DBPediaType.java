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
	private static final String url = "https://query.wikidata.org/sparql"; // http://dbpedia.org/sparql
	private final static String isPerson = "ASK{ <$r> a dbo:Location }";
	private final static String isPlace = "ASK{ <$r> a dbo:Location }";
	private final static String isOrganization = "ASK{ <$r> a dbo:Location }";
	private final RepositoryConnection conn;


	public DBPediaType()
	{
		SPARQLRepository repo = new SPARQLRepository(url);
		conn = repo.getConnection();
	}

	public Type getType(String r)
	{
		BooleanQuery isPersonQuery = conn.prepareBooleanQuery(isPerson.replace("$r", r));
		BooleanQuery isPlaceQuery = conn.prepareBooleanQuery(isPlace.replace("$r", r));
		BooleanQuery isOrganizationQuery = conn.prepareBooleanQuery(isOrganization.replace("$r", r));

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
