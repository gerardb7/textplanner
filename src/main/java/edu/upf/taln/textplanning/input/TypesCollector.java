package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.upf.taln.textplanning.structures.amr.Candidate;
import edu.upf.taln.textplanning.structures.Meaning;
import it.uniroma1.lcl.babelnet.BabelSynset;
import it.uniroma1.lcl.babelnet.BabelSynsetID;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

class TypesCollector
{
	private final DBPediaType dbpedia = new DBPediaType();
	private final BabelNetWrapper babelnet;
	private final Map<String, Candidate.Type> types = new HashMap<>();
	private final static Logger log = LogManager.getLogger(TypesCollector.class);

	TypesCollector(Path types_file, BabelNetWrapper babelnet) throws IOException
	{
		this.babelnet = babelnet;

		Stopwatch timer = Stopwatch.createStarted();
		String json = FileUtils.readFileToString(types_file.toFile(), UTF_8);
		Gson gson = new Gson();
		java.lang.reflect.Type type = new TypeToken<Map<String, String>>() { }.getType();
		Map<String, String> types_in_file = gson.fromJson(json, type);
		types_in_file.forEach((key, value) -> types.put(key, Candidate.Type.valueOf(value)));
		log.info(types.size() + " DBpedia types read from file in " + timer.stop());
	}

	void getMeaningTypes(Collection<Candidate> candidates)
	{
		Stopwatch timer = Stopwatch.createStarted();

		// Collect distinct references
		Set<String> refs = candidates.stream()
				.map(Candidate::getMeaning)
				.map(Meaning::getReference)
				.distinct()
				.collect(toSet());

		// Keep types of references in map
		Map<String, Candidate.Type> types = refs.stream()
				.filter(this.types::containsKey)
				.collect(toMap(identity(), this.types::get));

		// Query types for the rest
		Set<String> refs2query = refs.stream()
				.filter(r -> !types.containsKey(r))
				.collect(toSet());

		AtomicInteger counter = new AtomicInteger();
		refs2query.forEach(r -> {
			try
			{
				BabelSynset synset = babelnet.getSynset(new BabelSynsetID(r));
				List<String> dbPediaURIs = synset.getDBPediaURIs(Language.EN);
				Candidate.Type t = Candidate.Type.Other;
				if (!dbPediaURIs.isEmpty())
				{
					t = dbpedia.getType(dbPediaURIs.get(0));
				}
				types.put(r, t);

				if (counter.incrementAndGet() % 1000 == 0)
					log.info(counter.get() + " types queried");

			}
			catch (Exception e) { throw new RuntimeException(e); }
		});

		// Assign types to entities
		candidates.stream()
				.map(Candidate::getMeaning)
				.forEach(m -> m.setType(types.get(m.getReference())));

		log.info("Types determined in " + timer.stop());
	}
}
