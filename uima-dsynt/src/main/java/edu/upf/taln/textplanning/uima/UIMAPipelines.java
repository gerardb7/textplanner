package edu.upf.taln.textplanning.uima;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.NGram;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.matetools.MateLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.ngrams.NGramAnnotator;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordPosTagger;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordSegmenter;
import de.tudarmstadt.ukp.dkpro.wsd.algorithm.TALNSenseBaseline;
import de.tudarmstadt.ukp.dkpro.wsd.annotator.WSDAnnotatorCollectiveCandidate;
import de.tudarmstadt.ukp.dkpro.wsd.resource.WSDResourceCollectiveCandidate;
import de.tudarmstadt.ukp.dkpro.wsd.type.WSDResult;
import edu.upf.taln.flask_wrapper.type.WSDSpan;
import edu.upf.taln.textplanning.common.BabelNetWrapper;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Meaning;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.uima.types.ConceptRelevance;
import edu.upf.taln.uima.flask_wrapper.ConceptExtractorAnnotator;
import edu.upf.taln.uima.wsd.annotation_extender.core.WSDResultExtender;
import edu.upf.taln.uima.wsd.candidateDetection.BabelNetCandidateIdentification;
import edu.upf.taln.uima.wsd.si.babelnet.resource.BabelnetSenseInventoryResource;
import edu.upf.taln.uima.wsd.types.BabelNetSense;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;

public class UIMAPipelines
{
	final private AnalysisEngine pipeline;
	private static final String language = "en";
	private static final int ngram_size = 3;
	private static final String concept_extraction_url = "http://server01-taln.s.upf.edu:8000";
	private final static Logger log = LogManager.getLogger();

	private UIMAPipelines(AnalysisEngine pipeline)
	{
		this.pipeline = pipeline;
	}

	public static UIMAPipelines createSpanPipeline(boolean use_concept_extractor)
	{
		try
		{
			AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(StanfordSegmenter.class,
					StanfordSegmenter.PARAM_LANGUAGE, language);
			AnalysisEngineDescription pos = AnalysisEngineFactory.createEngineDescription(StanfordPosTagger.class,
					StanfordPosTagger.PARAM_LANGUAGE, language);
			AnalysisEngineDescription lemma = AnalysisEngineFactory.createEngineDescription(MateLemmatizer.class,
					MateLemmatizer.PARAM_LANGUAGE, language,
					MateLemmatizer.PARAM_VARIANT, "default");
			AnalysisEngineDescription spans = use_concept_extractor ?
					AnalysisEngineFactory.createEngineDescription(ConceptExtractorAnnotator.class, ConceptExtractorAnnotator.PARAM_FLASK_URL, concept_extraction_url)
					: AnalysisEngineFactory.createEngineDescription(NGramAnnotator.class, NGramAnnotator.PARAM_N, 3);

			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();
			components.add(segmenter);
			components.add(pos);
			components.add(lemma);
			components.add(spans);
			AnalysisEngineDescription[] componentArray = components.toArray(new AnalysisEngineDescription[0]);
			AnalysisEngineDescription all = AnalysisEngineFactory.createEngineDescription(componentArray);

			return new UIMAPipelines(createEngine(all));
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public static UIMAPipelines createRankingPipeline(boolean use_concept_extractor, Path babel_config, Path freqs_file, Path vectors)
	{
		try
		{
			AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(StanfordSegmenter.class,
					StanfordSegmenter.PARAM_LANGUAGE, language);
			AnalysisEngineDescription pos = AnalysisEngineFactory.createEngineDescription(StanfordPosTagger.class,
					StanfordPosTagger.PARAM_LANGUAGE, language);
			AnalysisEngineDescription lemma = AnalysisEngineFactory.createEngineDescription(MateLemmatizer.class,
					MateLemmatizer.PARAM_LANGUAGE, language,
					MateLemmatizer.PARAM_VARIANT, "default");

			AnalysisEngineDescription spans = use_concept_extractor ?
					AnalysisEngineFactory.createEngineDescription(ConceptExtractorAnnotator.class, ConceptExtractorAnnotator.PARAM_FLASK_URL, concept_extraction_url)
					: AnalysisEngineFactory.createEngineDescription(NGramAnnotator.class, NGramAnnotator.PARAM_N, ngram_size);

			AnalysisEngineDescription babelnet_candidates = use_concept_extractor ?
					AnalysisEngineFactory.createEngineDescription(BabelNetCandidateIdentification.class, WSDSpan.class)
					: AnalysisEngineFactory.createEngineDescription(BabelNetCandidateIdentification.class, NGram.class);

			ExternalResourceDescription babelnet = ExternalResourceFactory.createExternalResourceDescription(BabelnetSenseInventoryResource.class,
					BabelnetSenseInventoryResource.PARAM_BABELNET_CONFIGPATH, babel_config.toString(),
					BabelnetSenseInventoryResource.PARAM_BABELNET_LANG, language.toUpperCase(),
					BabelnetSenseInventoryResource.PARAM_BABELNET_DESCLANG, language.toUpperCase());

			ExternalResourceDescription babelnetDisambiguationResources = ExternalResourceFactory.createExternalResourceDescription(WSDResourceCollectiveCandidate.class,
					WSDResourceCollectiveCandidate.SENSE_INVENTORY_RESOURCE, babelnet,
					WSDResourceCollectiveCandidate.DISAMBIGUATION_METHOD, TALNSenseBaseline.class.getName(),
					WSDResourceCollectiveCandidate.PARAM_FREQUENCIES_FILE, freqs_file.toString(),
					WSDResourceCollectiveCandidate.PARAM_SIMILARITIES_FILE, vectors.toString());

			AnalysisEngineDescription disambiguation = AnalysisEngineFactory.createEngineDescription(WSDAnnotatorCollectiveCandidate.class,
					WSDAnnotatorCollectiveCandidate.WSD_ALGORITHM_RESOURCE, babelnetDisambiguationResources,
					WSDAnnotatorCollectiveCandidate.PARAM_BEST_ONLY, false);

			AnalysisEngineDescription extender = AnalysisEngineFactory.createEngineDescription(WSDResultExtender.class,
					WSDResultExtender.PARAM_BABELNET, babelnet,
					WSDResultExtender.PARAM_LANGUAGES, new Language[]{Language.EN, Language.ES, Language.IT, Language.EL});

			AnalysisEngineDescription textplanner = AnalysisEngineFactory.createEngineDescription(TextPlanningAnnotator.class);

			ArrayList<AnalysisEngineDescription> components = new ArrayList<>();
			components.add(segmenter);
			components.add(pos);
			components.add(lemma);
			components.add(spans);
			components.add(babelnet_candidates);
			components.add(disambiguation);
			components.add(extender);
			components.add(textplanner);
			AnalysisEngineDescription[] componentArray = components.toArray(new AnalysisEngineDescription[0]);
			AnalysisEngineDescription all = AnalysisEngineFactory.createEngineDescription(componentArray);

			return new UIMAPipelines(createEngine(all));
		}
		catch (UIMAException e)
		{
			log.error("Cannot create UIMA pipeline: " + e);
			return null;
		}
	}

	public List<List<Set<Candidate>>> getCandidates(String text, BabelNetWrapper bn)
	{
		if (text.isEmpty())
			return Collections.emptyList();

		try
		{
			log.info("Processing text");
			final JCas doc = processText(text, pipeline);

			log.info("Collecting candidates");
			final List<Sentence> sentences = new ArrayList<>(JCasUtil.select(doc, Sentence.class));
			return sentences.stream()
					.map(sentence -> JCasUtil.selectCovered(NGram.class, sentence).stream()
							.map(span ->
							{
								final String surface_form = span.getCoveredText();
								final List<Token> surface_tokens = JCasUtil.selectCovered(Token.class, span);
								final String lemma = surface_tokens.stream()
										.map(Token::getLemma)
										.map(Lemma::getValue)
										.collect(Collectors.joining(" "));
								final String pos = surface_tokens.size() == 1 ? surface_tokens.get(0).getPos().getPosValue() : "NN";
								final List<Token> sentence_tokens = JCasUtil.selectCovered(Token.class, sentence);
								final int token_based_offset_begin = sentence_tokens.indexOf(surface_tokens.get(0));
								final int token_based_offset_end = sentence_tokens.indexOf(surface_tokens.get(surface_tokens.size() - 1)) + 1;
								final Pair<Integer, Integer> offsets = Pair.of(token_based_offset_begin, token_based_offset_end);
								final Mention mention = Mention.get("s" + sentences.indexOf(sentence), offsets, surface_form, lemma, pos, false, "");

								return bn.getSynsets(surface_form, pos).stream()
										.filter(bn::isValid)
										.map(s -> Meaning.get(s, bn.getLabel(s).orElse(""), bn.isNE(s).orElse(false)))
										.map(meaning -> new Candidate(meaning, mention))
										.collect(Collectors.toSet());
							})
							.filter(l -> !l.isEmpty())
							.collect(toList()))
					.collect(toList());
		}
		catch (UIMAException e)
		{
			log.error("Cannot get candidates from text " + text + ": " + e);
			return Collections.emptyList();
		}
	}

	public List<List<Map<Candidate, Double>>> getDisambiguatedCandidates(String text)
	{
		if (text.isEmpty())
			return Collections.emptyList();

		try
		{
			log.info("Processing text");
			final JCas doc = processText(text, pipeline);

			log.info("Collecting candidates");
			final List<Sentence> sentences = new ArrayList<>(JCasUtil.select(doc, Sentence.class));
			return sentences.stream()
					.map(sentence -> JCasUtil.selectCovered(NGram.class, sentence).stream()
							.map(span ->
							{
								final String surface_form = span.getCoveredText();
								final List<Token> surface_tokens = JCasUtil.selectCovered(Token.class, span);
								final String lemma = surface_tokens.stream()
										.map(Token::getLemma)
										.map(Lemma::getValue)
										.collect(Collectors.joining(" "));
								final String pos = surface_tokens.size() == 1 ? surface_tokens.get(0).getPos().getPosValue() : "NN";
								final List<Token> sentence_tokens = JCasUtil.selectCovered(Token.class, sentence);
								final int token_based_offset_begin = sentence_tokens.indexOf(surface_tokens.get(0));
								final int token_based_offset_end = sentence_tokens.indexOf(surface_tokens.get(surface_tokens.size() - 1));
								final Pair<Integer, Integer> offsets = Pair.of(token_based_offset_begin, token_based_offset_end);
								final Mention mention = Mention.get("s" + sentences.indexOf(sentence), offsets, surface_form, lemma, pos, false, "");

								return JCasUtil.selectAt(doc, WSDResult.class, span.getBegin(), span.getEnd()).stream()
										.map(a ->
										{
											final BabelNetSense s = (BabelNetSense) a.getBestSense();
											final Meaning meaning = Meaning.get(s.getId(), s.getLabel(), s.getNameEntity());
											final Candidate c = new Candidate(meaning, mention);
											final double rank = JCasUtil.selectAt(doc, ConceptRelevance.class, a.getBegin(), a.getEnd()).get(0).getDomainRelevance();
											return Pair.of(c, rank);
										})
										.collect(toMap(Pair::getLeft, Pair::getRight));
							})
							.filter(l -> !l.isEmpty())
							.collect(toList()))
					.collect(toList());
		}
		catch (UIMAException e)
		{
			log.error("Cannot get disambiguated meanings from text " + text + ": " + e);
			return Collections.emptyList();
		}
	}

	private static JCas processText(String text, AnalysisEngine pipeline) throws UIMAException
	{
		JCas jCas;
		jCas = JCasFactory.createText(text);
		jCas.setDocumentLanguage(language);
		DocumentMetaData.create(jCas);
		pipeline.process(jCas);

		return jCas;
	}
}
