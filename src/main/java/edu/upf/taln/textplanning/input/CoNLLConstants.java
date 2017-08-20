package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.structures.LinguisticStructure;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CoNLLConstants
{
	public static final String BN_ID = "bnId";
	public static final String NE_CLASS = "ne_class";
	public static final String ROOT = "ROOT";
	public static final String MENTION_FORM = "original_slex";
	public static final String MENTION_LEMMA = "word";
	public static final String START_STRING = "start_string";
	public static final String END_STRING = "end_string";
	public static final String OFFSETS = "offsets";
	public static final String SPANS = "spans";
	public static final String REFERENCES = "references";
	public static final String COREFERENCES = "coreferences";
	public static final String TYPES = "types";
	public static final String WEIGHTS = "weights";

	// For testing purposes
	public static void main(String[] args) throws IOException
	{
		String in_conll = FileUtils.readFileToString(new File(args[0]), StandardCharsets.UTF_8);
		CoNLLReader reader = new CoNLLReader();
		List<LinguisticStructure> structures = reader.readStructures(in_conll);

		CoNLLWriter writer = new CoNLLWriter();
		String out_conll = writer.writeStructures(structures);
		System.out.println(out_conll);
	}
}
