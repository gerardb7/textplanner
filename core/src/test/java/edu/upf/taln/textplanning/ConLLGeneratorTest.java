package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.datastructures.AnnotationInfo;
import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import edu.upf.taln.textplanning.input.DocumentAccess;
import org.apache.commons.io.FileUtils;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Test for ConLLAcces class
 */
public class ConLLGeneratorTest
{
	@Test
	public void testConLLStructures() throws Exception
	{
		String read = FileUtils.readFileToString(new File("src/test/resources/test_b0b50d7481b9e1b9a2e071db654394c2c8e1fa1f.conll"), Charset.defaultCharset());

		ConLLAcces conll = new ConLLAcces();
		List<DirectedAcyclicGraph<AnnotationInfo, DocumentAccess.LabelledEdge>> structures =
				conll.readSemanticDAGs("src/test/resources/test_b0b50d7481b9e1b9a2e071db654394c2c8e1fa1f.conll");
		String written = conll.writeSemanticDAGs(structures);

		Assert.assertEquals(read, written);
	}

	@Test
	public void testConLLMessages() throws Exception
	{
		String read = FileUtils.readFileToString(new File("src/test/resources/test_b0b50d7481b9e1b9a2e071db654394c2c8e1fa1f.conll"), Charset.defaultCharset());

		ConLLAcces conll = new ConLLAcces();
		List<SemanticTree> messages = conll.readSemanticTrees("src/test/resources/test_b0b50d7481b9e1b9a2e071db654394c2c8e1fa1f.conll");
		String written = conll.writeSemanticTrees(messages);

		Assert.assertEquals(read, written);
	}

}