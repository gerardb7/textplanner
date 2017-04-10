package edu.upf.taln.textplanning;

import edu.upf.taln.textplanning.datastructures.SemanticTree;
import edu.upf.taln.textplanning.input.ConLLAcces;
import org.apache.commons.io.FileUtils;
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
	public void testConLLMessages() throws Exception
	{
		String read = FileUtils.readFileToString(new File("src/test/resources/test_b0b50d7481b9e1b9a2e071db654394c2c8e1fa1f.conll"), Charset.defaultCharset());

		ConLLAcces conll = new ConLLAcces();
		List<SemanticTree> messages = conll.readTrees("src/test/resources/test_b0b50d7481b9e1b9a2e071db654394c2c8e1fa1f.conll");
		String written = conll.writeTrees(messages);

		Assert.assertEquals(read, written);
	}

}