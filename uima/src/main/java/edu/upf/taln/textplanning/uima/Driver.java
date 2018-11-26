package edu.upf.taln.textplanning.uima;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.matetools.MateLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordPosTagger;
import de.tudarmstadt.ukp.dkpro.core.stanfordnlp.StanfordSegmenter;
import de.tudarmstadt.ukp.dkpro.wsd.algorithm.TALNSenseBaseline;
import de.tudarmstadt.ukp.dkpro.wsd.annotator.WSDAnnotatorCollectiveCandidate;
import de.tudarmstadt.ukp.dkpro.wsd.resource.WSDResourceCollectiveCandidate;
import edu.upf.taln.uima.wsd.annotation_extender.core.WSDResultExtender;
import edu.upf.taln.uima.wsd.candidateDetection.BabelNetCandidateIdentification;
import edu.upf.taln.uima.wsd.si.babelnet.resource.BabelnetSenseInventoryResource;
import it.uniroma1.lcl.jlt.util.Language;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.ExternalResourceFactory.createExternalResourceDescription;

import java.util.ArrayList;

public class Driver
{
	public static void doit() throws UIMAException
	{
		String babelnetConfig = "/home/gerard/data/babelconfig";
		String reqFile = "/home/gerard/data/freqs.bin";
		String similFile = "/home/gerard/data/sew-embed.nasari_bin";


		ArrayList<AnalysisEngineDescription> components = new ArrayList<>();

		AnalysisEngineDescription segmenter = createEngineDescription(StanfordSegmenter.class,
				StanfordSegmenter.PARAM_LANGUAGE, "en");
		AnalysisEngineDescription pos = createEngineDescription(StanfordPosTagger.class,
				StanfordPosTagger.PARAM_LANGUAGE, "en");
		AnalysisEngineDescription lemma = createEngineDescription(MateLemmatizer.class,
				MateLemmatizer.PARAM_LANGUAGE, "en",
				MateLemmatizer.PARAM_VARIANT, "default");

		ExternalResourceDescription BabelNet = createExternalResourceDescription(BabelnetSenseInventoryResource.class,
				BabelnetSenseInventoryResource.PARAM_BABELNET_CONFIGPATH, babelnetConfig,
				BabelnetSenseInventoryResource.PARAM_BABELNET_LANG, "EN",
				BabelnetSenseInventoryResource.PARAM_BABELNET_DESCLANG, "EN");

		AnalysisEngineDescription candidates = createEngineDescription(BabelNetCandidateIdentification.class,
				BabelNetCandidateIdentification.PARAM_BABELNET, BabelNet,
				BabelNetCandidateIdentification.PARAM_MAX_WORDS, 3);

		ExternalResourceDescription mfsBaselineResourceBabelNet = createExternalResourceDescription(WSDResourceCollectiveCandidate.class,
				WSDResourceCollectiveCandidate.SENSE_INVENTORY_RESOURCE, BabelNet,
				WSDResourceCollectiveCandidate.DISAMBIGUATION_METHOD, TALNSenseBaseline.class.getName(),
				WSDResourceCollectiveCandidate.PARAM_FREQUENCIES_FILE, reqFile,
				WSDResourceCollectiveCandidate.PARAM_SIMILARITIES_FILE, similFile);

		AnalysisEngineDescription mfsBaselineBabelNet = createEngineDescription(WSDAnnotatorCollectiveCandidate.class,
				WSDAnnotatorCollectiveCandidate.WSD_ALGORITHM_RESOURCE, mfsBaselineResourceBabelNet,
				WSDAnnotatorCollectiveCandidate.PARAM_BEST_ONLY, false);

		AnalysisEngineDescription extender = AnalysisEngineFactory.createEngineDescription(WSDResultExtender.class,
				WSDResultExtender.PARAM_BABELNET, BabelNet,
				WSDResultExtender.PARAM_LANGUAGES, new Language[]{Language.EN, Language.ES, Language.IT, Language.EL});

		AnalysisEngineDescription textplanner = createEngineDescription(TextPlanningAnnotator.class);


		components.add(segmenter);
		components.add(pos);
		components.add(lemma);
		components.add(candidates);
		components.add(mfsBaselineBabelNet);
		components.add(extender);
		components.add(textplanner);

		JCas jCas;
		jCas = JCasFactory.createText("The punishment of those who wage war against Allah and His messenger and strive to make mischief in the land is only this.");
		jCas.setDocumentLanguage("en");
		DocumentMetaData.create(jCas);

		AnalysisEngineDescription[] componentArray = components.toArray(new AnalysisEngineDescription[0]);
		AnalysisEngineDescription all = createEngineDescription(componentArray);
		AnalysisEngine ae = createEngine(all);
		ae.process(jCas);
	}

	public static void main(String[] args) throws UIMAException
	{
		Driver.doit();
	}
}
