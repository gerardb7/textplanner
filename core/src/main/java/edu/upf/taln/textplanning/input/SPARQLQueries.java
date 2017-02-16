package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable class implementing access to the Multisensor RDF dataset through a SPARQL endpoint.
 * This class defines a set of queries which can be used to retrieve information relevant to text planning,
 * i.e. relations and arguments.
 */
public final class SPARQLQueries
{
	/**
	 * Wrapper class for SPARQL queries
	 */
	public final static class Query
	{
		private final String queryString;
		private final String[] placeHolders;
		private final String[] vars;

		public Query(String inQueryString, String[] inPlaceHolders, String[] inVars)
		{
			this.queryString = inQueryString;
			this.placeHolders = inPlaceHolders;
			this.vars = inVars;
		}

		public String getQuery(String[] inParams)
		{
			String query = this.queryString;
			int i = 0;
			for (String placeholder : this.placeHolders)
			{
				String param = inParams[i];
				// Support for absolute IRIs
				if (inParams[i].startsWith("http://"))
				{
					param = "<" + param + ">";
				}

				query = query.replaceAll("\\$" + placeholder, param);
				++i;
			}

			return query;
		}
	}

	private static final Map<String, Query> queries = new HashMap<>();
	private static final String prefixes =
				"PREFIX nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> \n" +
						"PREFIX fn: <http://www.ontologydesignpatterns.org/ont/framenet/tbox/> \n" +
						"PREFIX olia: <http://purl.org/olia/olia.owl#> \n" +
						"PREFIX ms-content: <http://data.multisensorproject.eu/content/> \n" +
						"PREFIX its: <http://www.w3.org/2005/11/its/rdf#> \n" +
						"PREFIX dbr: <http://dbpedia.org/resource/> \n" +
						"PREFIX bn: <http://babelnet.org/rdf/> \n" +
						"PREFIX dc: <http://purl.org/dc/elements/1.1/> \n" +
						"PREFIX nif-ann: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-annotation#> \n" +
						"PREFIX ms: <http://data.multisensorproject.eu/ontology#> \n\n";
	private static final Logger log = LoggerFactory.getLogger(SPARQLQueries.class);

	// Static initalization
	static
	{
		{
			final String query =
					"SELECT  ?g ?ann WHERE { \n" +
							"	GRAPH  ?g { \n" +
							"	    ?g dc:subject ?uc . \n" +
							"	    FILTER (?uc = \"$usecase\") \n" +
							"		?ann its:taIdentRef $id . \n" +
							"	} \n" +
							"}";
			final String[] placeholders = {"id", "usecase"};
			final String[] vars = {"g", "ann"};
			queries.put("countFrequency", new Query(prefixes + query, placeholders, vars));
		}

		{
			final String query =
					"SELECT  ?g ?nifAnn1 ?nifSent1 ?nifAnn2 ?nifSent2 WHERE { " +
							"   GRAPH  ?g { " +
							"       ?g dc:subject ?uc . " +
							"       FILTER (?uc = \"$usecase\") " +
							"       ?ann1 its:taIdentRef $id1 . " +
							"       {?nifAnn1 nif-ann:annotationUnit ?ann1} union {bind(?ann1 as ?nifAnn1)} " +
							"       ?ann2 its:taIdentRef $id2 . " +
							"       {?nifAnn2 nif-ann:annotationUnit ?ann2} union {bind(?ann2 as ?nifAnn2)} " +
							"       ?nifAnn1 nif:beginIndex ?a1b; nif:endIndex ?a1e . " +
							"       ?nifAnn2 nif:beginIndex ?a2b; nif:endIndex ?a2e . " +
							"       ?nifSent1 a nif:Sentence; nif:beginIndex ?s1b; nif:endIndex ?s1e . " +
							"       ?nifSent2 a nif:Sentence; nif:beginIndex ?s2b; nif:endIndex ?s2e . " +
							"       FILTER (?s1b <= ?a1b && ?s1e >= ?a1e && ?s2b <= ?a2b && ?s2e >= ?a2e) " +
							"   } " +
							"}";
			final String[] placeholders = {"id1", "id2", "usecase"};
			final String[] vars = {"g", "nifAnn1", "nifSent1", "nifAnn2", "nifSent2"};
			queries.put("countCoccurrence", new Query(prefixes + query, placeholders, vars));
		}
	}

	public static Query getQuery(String inId)
	{
		return queries.get(inId);
	}

	/**
	 * Creates an in-memory model containing all statements belonging to a graph in the repo
	 * @param inRepo repository to be queried
	 * @param inGraph graph for which all statements are returned
	 * @return a model containing all statements in the graph
	 */
	public static Model getRepoGraph(Repository inRepo, String inGraph)
	{
		try
		{
			String graph = inGraph.startsWith("http://") ? "<" + inGraph + ">" : inGraph;
				final String query = prefixes + " SELECT ?s ?p ?o WHERE { GRAPH " + graph + " { ?s ?p ?o }}";
			Stopwatch timer = Stopwatch.createStarted();

			final RepositoryConnection connection = inRepo.getConnection();
			final TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
			final TupleQueryResult response = tupleQuery.evaluate();

//			TupleQueryResult response = Repositories.tupleQuery(inRepo, query, r -> r);

			Model model = new TreeModel();
			QueryResults.asList(response)
					.forEach(bs -> {
						Resource s = (Resource) bs.getBinding("s").getValue();
						IRI p = (IRI) bs.getBinding("p").getValue();
						Value o = bs.getBinding("o").getValue();
						model.add(SimpleValueFactory.getInstance().createStatement(s, p, o));
					});
			log.debug("SPARQL query took: " + timer.stop());

			return model;
		}
		catch (Exception e)
		{
			log.error("Getting graph model FAILED");
			throw new RuntimeException("Cannot query the RDF repository :" + e);
		}
	}

	/**
	 * Launches a SELECT query against a repository
	 *
	 * @param inRepo        repository to be queried
	 * @param inQueryString a SELECT query
	 * @return a set of binding sets returned by query
	 */
	public static Set<Map<String, Value>> queryRepoSelect(Repository inRepo, String inQueryString) throws Exception
	{
		try
		{
			Stopwatch timer = Stopwatch.createStarted();
			final RepositoryConnection connection = inRepo.getConnection();
			final TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, inQueryString);
			final TupleQueryResult response = tupleQuery.evaluate();


			Set<Map<String, Value>> results = QueryResults.asList(response).stream()
					.map(bs -> bs.getBindingNames().stream()
							.collect(Collectors.toMap(b -> b, b -> (Value) bs.getBinding(b).getValue())))
					.collect(Collectors.toSet());

			log.debug("SPARQL: query took: " + timer.stop());
			return results;
		}
		catch (Exception e)
		{
			log.error("The following query FAILED: " + inQueryString);
			throw new Exception("Cannot query the RDF repository :" + e);
		}
	}
}
