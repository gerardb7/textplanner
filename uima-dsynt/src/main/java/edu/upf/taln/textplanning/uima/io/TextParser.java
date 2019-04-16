package edu.upf.taln.textplanning.uima.io;

import com.ibm.icu.text.CharsetDetector;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.MimeTypes;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CompressionUtils;
import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import org.apache.commons.io.IOUtils;
import org.apache.uima.cas.CAS;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.MimeTypeCapability;
import org.apache.uima.fit.descriptor.ResourceMetaData;
import org.apache.uima.fit.descriptor.TypeCapability;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * UIMA collection reader for plain text files.
 */
@ResourceMetaData(name = "Document Reader")
@MimeTypeCapability(MimeTypes.TEXT_PLAIN)
@TypeCapability(outputs = {"de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData"})
public class TextParser extends TextReader
{
	@ConfigurationParameter(name = PARAM_SOURCE_ENCODING, mandatory = true,	defaultValue = ComponentParameters.DEFAULT_ENCODING)
	private String sourceEncoding;
	public static final String ENCODING_AUTO = "auto";
	public static final String PARAM_SOURCE_ENCODING = ComponentParameters.PARAM_SOURCE_ENCODING;

	@Override
	public void getNext(CAS aJCas) throws IOException
	{
		Resource res = nextFile();
		initCas(aJCas, res);

		try (InputStream is = new BufferedInputStream(
				CompressionUtils.getInputStream(res.getLocation(), res.getInputStream())))
		{
			String text;

			if (ENCODING_AUTO.equals(sourceEncoding))
			{
				CharsetDetector detector = new CharsetDetector();
				text = IOUtils.toString(detector.getReader(is, null));
			}
			else
			{
				text = IOUtils.toString(is, sourceEncoding);
			}

			aJCas.setDocumentText(parse(text));
		}
	}

	protected String parse(String text) { return text; }
}
