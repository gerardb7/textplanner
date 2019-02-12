package edu.upf.taln.textplanning.uima;

import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.junit.Test;

import java.util.Map;

/**
 * @author rcarlini
 */
public class BeawareOutputTest
{

	@Test
	public void testBasic() throws Exception
	{
		Map<String, EntityExtractor.Entity> result = doTest("src/test/resources/",
				"testFile.txt.xmi",
				"src/test/resources/TypeSystem.xml",
				"en");

		// Do asserts
	}

	public Map<String, EntityExtractor.Entity> doTest(String folderPath, String xmiFilename, String typesystemPath, String language) throws Exception
	{
		CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
				XmiReader.class,
				XmiReader.PARAM_SOURCE_LOCATION, folderPath,
				XmiReader.PARAM_PATTERNS, xmiFilename,
				XmiReader.PARAM_TYPE_SYSTEM_FILE, typesystemPath,
				XmiReader.PARAM_LANGUAGE, language);

		final JCas jcas = SimplePipeline.iteratePipeline(reader).iterator().next();
		final JCas targetView = jcas.getView("TargetView");
		final Map<String, EntityExtractor.Entity> dsynt_tree = EntityExtractor.extract(targetView);
		final Map<String, EntityExtractor.Entity> simplified_tree = RelationExtractor.extract(dsynt_tree);
		return simplified_tree;
	}
}
