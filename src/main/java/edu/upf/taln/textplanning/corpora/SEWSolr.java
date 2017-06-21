package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.datastructures.AnnotatedEntity;
import edu.upf.taln.textplanning.datastructures.Entity;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


/**
 * Wrapper class for the Solr index of the SEW corpus.
 * Immutable class.
 */
public final class SEWSolr implements Corpus
{
	private final HttpSolrClient server;
	private final long numDocs;
	private final static Logger log = LoggerFactory.getLogger(SEWSolr.class);
	private final static Cache freqCache = new Cache();
	private final static Cache coocurrenceCache = new Cache();
	private final static DebugAid debug = new DebugAid(SEWSolr.class.getName()); // encapsulates non-immutable behavior used for debugging purposes

	public SEWSolr(String inURL) throws RuntimeException
	{
		try
		{
			server = new HttpSolrClient(inURL);
			SolrQuery q = new SolrQuery("*:*");
			q.setRows(0);
			numDocs = server.query(q).getResults().getNumFound();
		}
		catch (Exception e)
		{
			log.error("Failed to set up access to solr index: " + e);
			throw new RuntimeException(e);
		}
	}

	public long getFrequency(Entity inEntity)
	{
		// Get key use in look ups in the freqCache and solr
		String key = getKey(inEntity);
		try
		{
			if (freqCache.containsKey(key))
				return freqCache.get(key);
			else
			{
				long freq = queryFrequency(key);
				freqCache.put(key, freq);
				return freq;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public long getCooccurrence(Entity e1, Entity e2)
	{
		// Get key use in look ups in the freqCache and solr
		String k1 = getKey(e1);
		String k2 = getKey(e2);
		String cacheKey = k1 + "-" + k2;
		try
		{
			if (coocurrenceCache.containsKey(cacheKey))
				return coocurrenceCache.get(cacheKey);
			else
			{
				long cooc = queryCooccurrence(k1, k2);
				coocurrenceCache.put(cacheKey, cooc);
				return cooc;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * BabelNet annotations in SEW have good coverage of nominal phrases but not so good for other grammatical categories.
	 * For this reason we look up BabelNet synsets when they annotate to NPs, and use the word forms otherwise.
	 * (Multiword expressions annotate by large nominal phrases)
	 * @return the string representation of a key corresponding to the given entity
	 */
	private static String getKey(Entity inEntity)
	{
		String label = inEntity.getEntityLabel();
		if (label.startsWith("bn:") && label.endsWith("n"))
		{
			return label;
		}
		else
		{
			return ((AnnotatedEntity)inEntity).getAnnotation().getForm();
		}
	}

	@Override
	public long getNumDocs() { return this.numDocs; }

	private long queryFrequency(String key) throws IOException, SolrServerException
	{
		String queryString;
		String k = key.replace(":", "\\:").replace("-", "\\-").replace("\"", "\\\"");
		if (k.startsWith("bn\\:"))
		{
			queryString = "annotationId:" + k;
		}
		else
			queryString = "text:" + k;

		SolrQuery query = new SolrQuery(queryString);
		query.setRows(0); // don't request  data
		Stopwatch timer = Stopwatch.createStarted();
		long count = 0;
		try
		{
			count = server.query(query).getResults().getNumFound();
		}
		catch (Exception e)
		{
			log.error("Query " + query + " failed: " + e);

		}
		debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));

		return count;
	}

	private long queryCooccurrence(String key1, String key2)
	{
		String k1 = key1.replace(":", "\\:").replace("-", "\\-").replace("\"", "\\\"");
		String k2 = key2.replace(":", "\\:").replace("-", "\\-").replace("\"", "\\\"");
		if (k1.startsWith("bn\\:"))
		{
			k1 = "annotationId:" + k1;
		}
		else
			k1 = "text:" + k1;
		if (k2.startsWith("bn\\:"))
		{
			k2 = "annotationId:" + k2;
		}
		else
			k2 = "text:" + k2;

		String queryString = k1 + " AND " + k2;
		SolrQuery query = new SolrQuery(queryString);
		query.setRows(0); // don't request  data
		Stopwatch timer = Stopwatch.createStarted();
		long count = 0;
		try
		{
			count = server.query(query).getResults().getNumFound();
		}
		catch (Exception e)
		{
			log.error("Query " + query + " failed: " + e);

		}
		debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));

		return count;
	}


}
