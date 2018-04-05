package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetID;
import it.uniroma1.lcl.babelnet.data.BabelPOS;
import it.uniroma1.lcl.jlt.util.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import static edu.upf.taln.textplanning.input.POSConverter.BN_POS_EN;

public class BabelNetWrapper
{
	private final it.uniroma1.lcl.babelnet.BabelNet bn;
	private final static Logger log = LoggerFactory.getLogger(BabelNetWrapper.class);

	public BabelNetWrapper()
	{
		bn = getBabelNetInstance();
	}

	private static it.uniroma1.lcl.babelnet.BabelNet getBabelNetInstance()
	{
		Stopwatch timer = Stopwatch.createStarted();
		PrintStream oldOut = System.err;
		System.setErr(new PrintStream(new OutputStream() { public void write(int b) {} })); // shut up, BabelNet
		it.uniroma1.lcl.babelnet.BabelNet instance = it.uniroma1.lcl.babelnet.BabelNet.getInstance();
		System.setErr(oldOut);
		log.info("BabelNet set up in " + timer.stop());

		return instance;
	}

	@SuppressWarnings("unused")
	public List<BabelSynset> getSynsets(String form)
	{
		// Get candidate entities using strict matching
		try
		{
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
		BabelPOS bnPOS = BN_POS_EN.get(pos);

		// Get candidate entities using strict matching
		try
		{
			return bn.getSynsets(form, Language.EN, bnPOS);
		}
		catch (IOException e)
		{
			log.error("Error while getting synsets for " + form + ": " + e);
			return Collections.emptyList();
		}
	}

	BabelSynset getSynset(BabelSynsetID id) throws IOException
	{
		return bn.getSynset(id);
	}
}
