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
public final class SolrCounts implements CorpusCounts
{
	private final HttpSolrClient server;
	private final static Logger log = LoggerFactory.getLogger(SolrCounts.class);
	private final static Cache cache = new Cache();
	public final static DebugAid debug = new DebugAid(SolrCounts.class.getName()); // encapsulates non-immutable behavior used for debugging purposes

	public SolrCounts(String inURL) throws Exception
	{
		server = new HttpSolrClient(inURL);
	}

	@Override
	public Counts getCounts(String inEntity1, String inEntity2, String inDomain)
	{
		if (cache.containsCounts(inEntity1, inEntity2, inDomain, 0))
		{
			log.debug("Returning cached solr counts");
			return cache.get(inEntity1, inEntity2, inDomain, 0);
		}

		try
		{
			// This method assumes that references are BabelNet ids encoded as 'bn:' followed by 8 digits and a letter
			String r1 = inEntity1;
			if (r1.startsWith("s"))
			{
				r1 = "bn:" + r1.substring(1, r1.length());
			}
			if (!r1.startsWith("bn:"))
			{
				r1 = "bn:" + r1;
			}

			String r2 = inEntity2;
			if (r2.startsWith("s"))
			{
				r2 = "bn:" + r2.substring(1, r2.length());
			}
			if (!r2.startsWith("bn:"))
			{
				r2 = "bn:" + r2;
			}


			long freq;
			if (cache.containsCounts(inEntity2, inDomain, 0))
			{
				freq = cache.get(inEntity2, inDomain, 0);
			}
			else
			{
				freq = getFrequency(r2);
				cache.put(inEntity2, inDomain, 0, freq);
			}

			Counts c;
			if (cache.containsCounts(inEntity1, inEntity2, inDomain, 0))
			{
				c = cache.get(inEntity1, inEntity2, inDomain, 0);
			}
			else
			{
				long cooccur = getCoocurrence(r1, r2);
				c = new Counts(r1, r2, freq, cooccur);
				cache.put(inEntity1, inEntity2, inDomain, 0, c);
			}

			return c;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public Counts getCounts(String inEntity1, String inEntity2, String inDomain, int inDistance)
	{
		throw new RuntimeException("Not implemented");
	}

	@Override
	public Counts getOrderedCounts(String inEntity1, String inEntity2, String inDomain, int inDistance)
	{
		throw new RuntimeException("Not implemented");
	}

	private long getFrequency(String inEntity) throws IOException, SolrServerException
	{
		return queryCountServer("annotationId:" + inEntity.replace(":", "\\:"));
	}

	private long getCoocurrence(String inEntity1, String inEntity2) throws IOException, SolrServerException
	{
		return queryCountServer("annotationId:" + inEntity1.replace(":", "\\:") + " AND annotationId:" + inEntity2.replace(":", "\\:"));
	}

	private long queryCountServer(String inQueryString) throws IOException, SolrServerException
	{
		SolrQuery query = new SolrQuery(inQueryString);
		query.setRows(0); // don't request  data
		Stopwatch timer = Stopwatch.createStarted();
		long results = server.query(query).getResults().getNumFound();
		debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));

		return results;
	}
}
