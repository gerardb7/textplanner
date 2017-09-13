package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Stopwatch;
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
	private static final String solrUrl = "http://10.55.0.41:443/solr/sewDataAnnSen";
	private final HttpSolrClient server;
	private final long numDocs;
	private final static Logger log = LoggerFactory.getLogger(SEWSolr.class);
	private final static Cache freqCache = new Cache();
	private final static Cache coocurrenceCache = new Cache();
	private final static DebugAid debug = new DebugAid(SEWSolr.class.getName()); // encapsulates non-immutable behavior used for debugging purposes

	public SEWSolr() throws RuntimeException
	{
		try
		{
			server = new HttpSolrClient(solrUrl);
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

	@Override
	public long getEntityDocumentCount(String entity)
	{
		try
		{
			if (freqCache.containsKey(entity))
				return freqCache.get(entity);
			else
			{
				long freq = queryFrequency(entity);
				freqCache.put(entity, freq);
				return freq;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean hasEntity(String entity)
	{
		throw new RuntimeException("Method not implemented yet");
	}

	@Override
	public boolean hasEntityDocument(String entity)
	{
		throw new RuntimeException("Method not implemented yet");
	}

	@Override
	public boolean hasFormEntity(String form, String entity)
	{
		throw new RuntimeException("Method not implemented yet");
	}

	@Override
	public boolean hasForm(String form)
	{
		throw new RuntimeException("Method not implemented yet");
	}

	@Override public long getEntityCount(String entity) { return 0; }
	@Override public long getFormEntityCount(String form, String entity) {  return 0; }
	@Override public long getFormCount(String form) { return 0; }

	public long getCooccurrence(String i1, String i2)
	{
		String cacheKey = i1 + "-" + i2;
		try
		{
			if (coocurrenceCache.containsKey(cacheKey))
				return coocurrenceCache.get(cacheKey);
			else
			{
				long cooc = queryCooccurrence(i1, i2);
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
