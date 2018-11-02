package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import it.uniroma1.lcl.babelnet.BabelNetConfiguration;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetID;
import it.uniroma1.lcl.babelnet.data.BabelPOS;
import it.uniroma1.lcl.jlt.Configuration;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static edu.upf.taln.textplanning.input.POSConverter.BN_POS_EN;

public class BabelNetWrapper
{
	private final it.uniroma1.lcl.babelnet.BabelNet bn;
	static final AtomicLong num_queries = new AtomicLong();
	private final static Logger log = LogManager.getLogger();

	public BabelNetWrapper(Path config_folder, boolean no_babelnet)
	{
		bn = no_babelnet ? null : getBabelNetInstance(config_folder);
	}

	public static it.uniroma1.lcl.babelnet.BabelNet getBabelNetInstance(Path config_folder)
	{
		log.info("Setting up BabelNet");
		Stopwatch timer = Stopwatch.createStarted();
		PrintStream oldOut = System.err;
		System.setErr(new PrintStream(new OutputStream()
		{
			public void write(int b) {}
		})); // shut up, BabelNet

		it.uniroma1.lcl.babelnet.BabelNet instance = null;
		try
		{
			Path jlt_path = config_folder.resolve("jlt.properties");
			Configuration jltConf = Configuration.getInstance();
			jltConf.setConfigurationFile(jlt_path.toFile());
			BabelNetConfiguration bnConf = BabelNetConfiguration.getInstance();
			Path properties_path = config_folder.resolve("babelnet.properties");
			bnConf.setConfigurationFile(properties_path.toFile());
			bnConf.setBasePath(config_folder.toAbsolutePath() + "/");

			instance = it.uniroma1.lcl.babelnet.BabelNet.getInstance();
		}
		catch (Exception e)
		{
			System.setErr(oldOut);
			log.error("BabelNet set up failed: " + e);
		}
		finally
		{
			System.setErr(oldOut);
		}

		log.info("BabelNet set up in " + timer.stop());
		return instance;
	}

	@SuppressWarnings("unused")
	public List<BabelSynset> getSynsets(String form)
	{
		if (bn == null)
			return Collections.emptyList();

		// Get candidate entities using strict matching
		try
		{
			num_queries.getAndIncrement();
			return bn.getSynsets(form, Language.EN);
		}
		catch (IOException e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	List<BabelSynset> getSynsets(String form, String pos)
	{
		if (bn == null)
			return Collections.emptyList();

		// Get candidate entities using strict matching
		try
		{
			BabelPOS bnPOS = BN_POS_EN.get(pos);
			num_queries.getAndIncrement();
			return bn.getSynsets(form, Language.EN, bnPOS);
		}
		catch (Exception e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	BabelSynset getSynset(BabelSynsetID id) throws IOException
	{
		if (bn == null)
			return null;

		num_queries.getAndIncrement();
		return bn.getSynset(id);
	}
}
