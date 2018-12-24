package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import it.uniroma1.lcl.babelnet.*;
import it.uniroma1.lcl.babelnet.data.BabelGloss;
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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static edu.upf.taln.textplanning.common.POSConverter.BN_POS_EN;

public class BabelNetWrapper
{
	private final it.uniroma1.lcl.babelnet.BabelNet bn;
	public static final AtomicLong num_queries = new AtomicLong();
	private final static Logger log = LogManager.getLogger();

	public BabelNetWrapper(Path config_folder)
	{
		this(config_folder, false);
	}

	public BabelNetWrapper(Path config_folder, boolean no_babelnet)
	{
		bn = no_babelnet ? null : getBabelNetInstance(config_folder);
	}

	private static it.uniroma1.lcl.babelnet.BabelNet getBabelNetInstance(Path config_folder)
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

	public Iterator<String> getSynsetIterator()
	{
		return Iterators.transform(bn.getSynsetIterator(), s -> s.getId().getID());

	}

	@SuppressWarnings("unused")
	public List<String> getSynsets(String form)
	{
		if (bn == null)
			return Collections.emptyList();

		// Get candidate entities using strict matching
		try
		{
			num_queries.getAndIncrement();
			return bn.getSynsets(form, Language.EN).stream()
					.map(BabelSynset::getId)
					.map(BabelSynsetID::getID)
					.collect(Collectors.toList());
		}
		catch (IOException e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	public List<String> getSynsets(String form, String pos)
	{
		if (bn == null)
			return Collections.emptyList();

		// Get candidate entities using strict matching
		try
		{
			BabelPOS bnPOS = BN_POS_EN.get(pos);
			num_queries.getAndIncrement();
			return bn.getSynsets(form, Language.EN, bnPOS).stream()
					.map(BabelSynset::getId)
					.map(BabelSynsetID::getID)
					.collect(Collectors.toList());
		}
		catch (Exception e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	public boolean isValid(String id)
	{
		if (bn == null)
			return false;

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			return synset != null;
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return false;
		}
	}

	public Optional<String> getLabel(String id)
	{
		if (bn == null)
			return Optional.empty();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Optional.empty();
			return Optional.of(synset.getSenses(Language.EN).iterator().next().toString());
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return Optional.empty();
		}
	}

	public Optional<Boolean> isNE(String id)
	{
		if (bn == null)
			return Optional.empty();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Optional.empty();
			return Optional.of(synset.getSynsetType() == BabelSynsetType.NAMED_ENTITY);
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return Optional.empty();
		}
	}

	public List<String> getdbPediaURIs(String id)
	{
		if (bn == null)
			return Collections.emptyList();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Collections.emptyList();
			return synset.getDBPediaURIs(Language.EN);
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return Collections.emptyList();
		}
	}

	public List<String> getGlosses(String id)
	{
		if (bn == null)
			return Collections.emptyList();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Collections.emptyList();
			return synset.getGlosses(Language.EN).stream().map(BabelGloss::getGloss).collect(Collectors.toList());
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return Collections.emptyList();
		}

	}
}
