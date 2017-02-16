package edu.upf.taln.textplanning.corpora;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.input.SPARQLQueries;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper class for query the MULTISENSOR semantic repository for counts of annotated BabelNet entities.
 * Immutable class
 */
public class MSSparqlCounts implements CorpusCounts
{
	private final HTTPRepository repo;
	private final static Logger log = LoggerFactory.getLogger(MSSparqlCounts.class);
	private final static Cache cache = new Cache();
	public final static DebugAid debug = new DebugAid(MSSparqlCounts.class.getName()); // encapsulates non-immutable beahvior used for debugging purposes

	public MSSparqlCounts(String inURL)
	{
		repo = new HTTPRepository(inURL);
	}

	@Override
	public Counts getCounts(String inEntity1, String inEntity2, String inDomain)
	{
		if (cache.containsCounts(inEntity1, inEntity2, inDomain, 0))
		{
			log.debug("Returning cached sparql counts");
			return cache.get(inEntity1, inEntity2, inDomain, 0);
		}

		try
		{
			// This method assumes that references are BabelNet ids encoded as 'bn:s' followed by 8 digits and a letter
			String r1 = inEntity1;
			if (r1.startsWith("http://babelnet.org/rdf/"))
			{
				r1 = SimpleValueFactory.getInstance().createIRI(r1).getLocalName();
			}
			if (r1.startsWith("s"))
			{
				r1 = "bn:" + r1;
			}
			else
			{
				r1 = "bn:s" + r1;
			}

			String r2 = inEntity2;
			if (r2.startsWith("s"))
			{
				r2 = "bn:" + r2;
			}
			else
			{
				r2 = "bn:s" + r2;
			}

			long freq;
			if (cache.containsCounts(inEntity2, inDomain, 0))
			{
				freq = cache.get(inEntity2, inDomain, 0);
			}
			else
			{
				SPARQLQueries.Query queryFreq = SPARQLQueries.getQuery("countFrequency");

				String qf = queryFreq.getQuery(new String[]{r2, inDomain});
				Stopwatch timer = Stopwatch.createStarted();
				Set<Map<String, Value>> resFreq = SPARQLQueries.queryRepoSelect(repo, qf);
				debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));
				freq = resFreq.size();
				cache.put(inEntity2, inDomain, 0, freq);
			}

			Counts c;
			if (cache.containsCounts(inEntity1, inEntity2, inDomain, 0))
			{
				c = cache.get(inEntity1, inEntity2, inDomain, 0);
			}
			else
			{
				SPARQLQueries.Query queryCooccur = SPARQLQueries.getQuery("countCoccurrence");
				String qc = queryCooccur.getQuery(new String[]{r1, r2, inDomain});
				Stopwatch timer = Stopwatch.createStarted();
				Set<Map<String, Value>> resCooccur = SPARQLQueries.queryRepoSelect(repo, qc);
				debug.registerQuery(timer.stop().elapsed(TimeUnit.MILLISECONDS));
				long cooccur = resCooccur.size();

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
}
