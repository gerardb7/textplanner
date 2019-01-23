package edu.upf.taln.textplanning.uima;


import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.wsd.type.WSDResult;
import edu.upf.taln.parser.deep_parser.types.DeepDependency;
import edu.upf.taln.parser.deep_parser.types.DeepToken;
import edu.upf.taln.parser.deep_parser.types.ROOT;
import edu.upf.taln.uima.wsd.types.BabelNetSense;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EntityExtractor
{
	public static class Entity
	{
		public final String id;
		public final String label;
		public final List<String> type = new ArrayList<>();
		public final List<String> refs = new ArrayList<>();
		public final List<Participant> participants = new ArrayList<>();
		public boolean incident = false;
		public Location location = null;
		public String seeAlso = null;
		public int cardinality = 0;
		public int number = 0;

		public Entity(String id, String label)
		{
			this.id = id;
			this.label = label;
		}

		// shallow copy constructor
		public Entity(Entity other)
		{
			id = other.id;
			label = other.label;
			type.addAll(other.type);
			refs.addAll(other.refs);
			incident = other.incident;
			location = other.location;
			seeAlso = other.seeAlso;
			cardinality = other.cardinality;
			number = other.number;
		}
	}

	public static class Participant
	{
		public final String role;
		public final Entity participant;

		public Participant(String role, Entity participant)
		{
			this.role = role;
			this.participant = participant;
		}
	}

	public static class Location
	{
		public final double latitude;
		public final double longitude;

		public Location(double latitude, double longitude)
		{
			this.latitude = latitude;
			this.longitude = longitude;
		}
	}

	public static long id_counter = 0;
	private static final String verb_pos_tag = "VB"; // assuming Penn Tree Bank tagset
	private final static Logger log = LogManager.getLogger();

	public static Map<String, Entity> extract(JCas jcas)
	{
		Map<DeepToken, Entity> tokens2entities = new HashMap<>();

		JCasUtil.select(jcas, DeepToken.class).forEach(dt ->
		{
			final String id = createId(dt);
			final Entity entity = new Entity(id, dt.getCoveredText());
			addInfo(jcas, dt, entity);
			tokens2entities.put(dt, entity);
		});

		JCasUtil.select(jcas, Sentence.class).forEach(sentence ->
		{
			try
			{
				JCasUtil.selectCovered(DeepDependency.class, sentence).stream()
						.filter(d -> !(d instanceof ROOT))
						.forEach(d ->
						{
							final DeepToken governor = d.getGovernor();
							final Entity governor_entity = tokens2entities.get(governor);

							final DeepToken dependent = d.getDependent();
							final Entity dependent_entity = tokens2entities.get(dependent);

							Participant participant = new Participant(d.getDependencyType(), dependent_entity);
							governor_entity.participants.add(participant);
						});
			}
			catch (Exception e)
			{
				log.error("Error encountered while reading sentence " + sentence.getId() + ": " + e);
			}
		});

		return tokens2entities.values().stream()
				.collect(Collectors.toMap(e -> e.id, Function.identity()));
	}

	private static String createId(DeepToken dt)
	{
		return dt.getCoveredText().replaceAll("\\s", "_") + "_" + id_counter++;
	}

	private static void addInfo(JCas jcas, DeepToken deep_token, Entity entity)
	{
		final List<Token> surface_tokens = JCasUtil.selectAt(jcas, Token.class, deep_token.getBegin(), deep_token.getEnd());
		final String lemma = (surface_tokens.size() == 1) ? surface_tokens.get(0).getLemmaValue() : deep_token.getCoveredText();
		final boolean isPredicate =  deep_token.getPos().getPosValue().startsWith(verb_pos_tag);
		JCasUtil.selectAt(jcas, WSDResult.class, deep_token.getBegin(), deep_token.getEnd()).forEach(ann ->
		{
			BabelNetSense babel_synset = (BabelNetSense) ann.getBestSense();
			entity.refs.add(babel_synset.getId());
			final String type = TypeMapper.map(babel_synset.getId(), lemma, isPredicate);
			entity.type.add(type);
		});
	}
}
