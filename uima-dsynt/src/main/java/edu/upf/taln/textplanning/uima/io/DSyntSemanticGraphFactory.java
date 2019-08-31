package edu.upf.taln.textplanning.uima.io;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.wsd.type.WSDResult;
import edu.upf.taln.parser.deep_parser.types.DeepDependency;
import edu.upf.taln.parser.deep_parser.types.DeepToken;
import edu.upf.taln.textplanning.core.structures.SemanticGraphFactory;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.structures.Role;
import edu.upf.taln.textplanning.core.structures.SemanticGraph;
import edu.upf.taln.textplanning.core.utils.POS;
import edu.upf.taln.textplanning.uima.types.ConceptRelevance;
import edu.upf.taln.uima.wsd.types.BabelNetSense;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class DSyntSemanticGraphFactory implements SemanticGraphFactory<JCas>
{
	private final POS.Tagset tagset;
	private final static Logger log = LogManager.getLogger();

	public DSyntSemanticGraphFactory(POS.Tagset tagset)
	{
		this.tagset = tagset;
	}

	@Override
	public SemanticGraph create(JCas jcas)
	{
		SemanticGraph graph = new SemanticGraph();
		for (Sentence sentence : JCasUtil.select(jcas, Sentence.class))
		{
			try
			{
				JCasUtil.selectCovered(DeepDependency.class, sentence).forEach(d ->
				{
					try
					{
						if (!d.getDependencyType().equals("ROOT"))
						{
							// Add vertices
							final String governor_id = createVertex(graph, jcas, sentence, d.getGovernor(), tagset);
							final String dependent_id = createVertex(graph, jcas, sentence, d.getDependent(), tagset);

							// Add edge
							Role role = Role.create(d.getDependencyType());
							graph.addEdge(governor_id, dependent_id, role);
						}
					}
					catch (Exception e)
					{
						log.warn("Failed to add edge for deep dependency " + d + " in sentence " + createId(sentence) + ": " + e);
					}
				});
			}
			catch (Exception e)
			{
				log.error("Reading DSynt tree file for sentence " + createId(sentence) + ": " + e);
			}
		}

		return graph;
	}

	private static String createVertex(SemanticGraph graph, JCas jcas, Sentence sentence, DeepToken deep_token, POS.Tagset tagset)
	{
		final Mention mention = createMention(jcas, sentence, deep_token, tagset);
		final String id = mention.toString();
		final Optional<Pair<Meaning, Double>> meaning = createMeaning(jcas, deep_token);

		graph.addVertex(id);
		graph.addMention(id, mention);
		meaning.ifPresent(p -> graph.setMeaning(id, p.getLeft()));
		meaning.ifPresent(p -> graph.setWeight(id, p.getRight()));
		// TODO decide if type needs setting

		return id;
	}

	private static Mention createMention(JCas jcas, Sentence sentence, DeepToken deep_token, POS.Tagset tagset)
	{
		// Collect surface linguistic info and create mention object
		final String surface_form = deep_token.getCoveredText();
		final List<Token> surface_tokens = JCasUtil.selectAt(jcas, Token.class, deep_token.getBegin(), deep_token.getEnd());
		final List<String> token_forms = surface_tokens.stream()
				.map(Token::getFormValue)
				.collect(toList());
		final String lemma = surface_tokens.stream()
				.map(Token::getLemma)
				.map(Lemma::getValue)
				.collect(Collectors.joining(" "));
		final String pos = deep_token.getPos().getPosValue();
		final POS.Tag tag = POS.get(pos, tagset);
		final List<Token> tokens = new ArrayList<>(JCasUtil.select(jcas, Token.class));
		final int token_based_offset_begin = tokens.indexOf(surface_tokens.get(0));
		final int token_based_offset_end = tokens.indexOf(surface_tokens.get(surface_tokens.size() - 1));
		if (token_based_offset_begin == -1 || token_based_offset_end == -1)
			throw new RuntimeException("Cannot get offsets for \"" + surface_form + "\" in sentence " + createId(sentence));
		final Pair<Integer, Integer> offsets = Pair.of(token_based_offset_begin, token_based_offset_end);

		return new Mention(String.valueOf(deep_token.getAddress()), createId(sentence), offsets, token_forms, lemma, tag, false, "");
	}

	private static Optional<Pair<Meaning, Double>> createMeaning(JCas jcas, DeepToken deep_token)
	{
		final List<WSDResult> annotations = JCasUtil.selectAt(jcas, WSDResult.class, deep_token.getBegin(), deep_token.getEnd());
		if (!annotations.isEmpty())
		{
			final WSDResult ann = annotations.get(0);
			BabelNetSense babel_synset = (BabelNetSense) ann.getBestSense();
			final Meaning meaning = Meaning.get(createId(babel_synset), babel_synset.getLabel(), babel_synset.getNameEntity());
			final double rank = babel_synset.getConfidence();
			return Optional.of(Pair.of(meaning, rank));
		}

		return Optional.empty();
	}

	private static OptionalDouble getConceptScore(JCas jcas, DeepToken deep_token)
	{

		final List<ConceptRelevance> annotations = JCasUtil.selectAt(jcas, ConceptRelevance.class, deep_token.getBegin(), deep_token.getEnd());
		if (!annotations.isEmpty())
		{
			final ConceptRelevance ann = annotations.get(0);
			return OptionalDouble.of(ann.getDomainRelevance());
		}

		return OptionalDouble.empty();
	}

	private static String createId(Annotation ann)
	{
		return "a" + ann.getBegin() + "-" + ann.getEnd();
	}
}
