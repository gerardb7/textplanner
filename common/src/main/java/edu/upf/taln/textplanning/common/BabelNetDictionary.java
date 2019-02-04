package edu.upf.taln.textplanning.common;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import com.ibm.icu.util.ULocale;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static edu.upf.taln.textplanning.common.POSConverter.BN_POS_EN;
import static java.util.stream.Collectors.toList;

public class BabelNetDictionary implements MeaningDictionary
{
	private final it.uniroma1.lcl.babelnet.BabelNet bn;
	public static final AtomicLong num_queries = new AtomicLong();
	private final static Logger log = LogManager.getLogger();

	public BabelNetDictionary(Path config_folder)
	{
		this(config_folder, false);
	}

	public BabelNetDictionary(Path config_folder, boolean no_babelnet)
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

	@Override
	public Iterator<String> iterator()
	{
		return Iterators.transform(bn.getSynsetIterator(), s -> Objects.requireNonNull(s).getId().getID());

	}

	@Override
	public Iterator<Info> infoIterator(ULocale language)
	{
		final Language babel_language = Language.fromISO(language.toLanguageTag());
		return Iterators.transform(bn.getSynsetIterator(), s -> {
			if (s == null)
				return null;

			final List<String> glosses = new ArrayList<>();
			try
			{
				s.getGlosses(babel_language).stream()
						.map(BabelGloss::toString)
						.forEach(glosses::add);
			}
			catch (IOException e)
			{
				log.warn("Cannot get glosses for synset " + s.getId() + ": " + e);
			}

			final List<String> lemmas = s.getSenses(babel_language).stream()
					.map(BabelSense::getLemma)
					.collect(toList());
			return new Info(s.getId().getID(), glosses, lemmas);
		});
	}

	@Override
	@SuppressWarnings("unused")
	public List<String> getMeanings(String form, ULocale language)
	{
		if (bn == null)
			return Collections.emptyList();

		// Get candidate entities using strict matching
		try
		{
			num_queries.getAndIncrement();
			return bn.getSynsets(form, Language.fromISO(language.toLanguageTag())).stream()
					.map(BabelSynset::getId)
					.map(BabelSynsetID::getID)
					.collect(toList());
		}
		catch (IOException e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	@Override
	public List<String> getMeanings(String form, String pos, ULocale language)
	{
		if (bn == null)
			return Collections.emptyList();

		// Get candidate entities using strict matching
		try
		{
			BabelPOS bnPOS = BN_POS_EN.get(pos);
//			if (bnPOS == null)
//				log.warn("Cannot recognize " + pos + " POS tag");
			num_queries.getAndIncrement();
			return bn.getSynsets(form, Language.fromISO(language.toLanguageTag()), bnPOS).stream()
					.map(BabelSynset::getId)
					.map(BabelSynsetID::getID)
					.collect(toList());
		}
		catch (Exception e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	@Override
	public boolean contains(String id)
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

	@Override
	public Optional<String> getLabel(String id, ULocale language)
	{
		if (bn == null)
			return Optional.empty();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Optional.empty();
			final List<BabelSense> senses = synset.getSenses(Language.fromISO(language.toLanguageTag()));
			if (senses.isEmpty())
				return Optional.empty();
			return Optional.of(senses.iterator().next().toString());
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			log.info("Cannot get label for synset " + id + ": " + e);
			return Optional.empty();
		}
	}

	@Override
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

	@Override
	public List<String> getGlosses(String id, ULocale language)
	{
		if (bn == null)
			return Collections.emptyList();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Collections.emptyList();
			return synset.getGlosses(Language.fromISO(language.toLanguageTag())).stream().map(BabelGloss::getGloss).collect(toList());
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return Collections.emptyList();
		}
	}

	@Override
	public List<String> getLemmas(String id, ULocale language)
	{
		if (bn == null)
			return Collections.emptyList();

		try
		{
			num_queries.getAndIncrement();
			final BabelSynset synset = bn.getSynset(new BabelSynsetID(id));
			if (synset == null)
				return Collections.emptyList();
			return synset.getSenses(Language.fromISO(language.toLanguageTag())).stream().map(BabelSense::getLemma).collect(toList());
		}
		catch (InvalidBabelSynsetIDException | IOException e)
		{
			return Collections.emptyList();
		}

	}
}
