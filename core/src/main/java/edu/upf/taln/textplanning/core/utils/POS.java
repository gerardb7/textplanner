package edu.upf.taln.textplanning.core.utils;


import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Various Tag tagsets and their conversions to UD 2
 * See https://universaldependencies.org/tagset-conversion/index.html
 */
public class POS
{
	public enum Tag
	{ADJ, ADP, PUNCT, ADV, AUX, SYM, INTJ, CCONJ, X, NOUN, DET, PROPN, NUM, VERB, PART, PRON, SCONJ}

	public enum Tagset
	{EnglishPTB, SpanishAncora, FrenchPTB, GermanSTTS, BabelNet, Simple}

	public static Tag get(String tag, Tagset tagset)
	{
		switch (tagset)
		{
			case EnglishPTB:
				return EnglishPTB.get(tag);
			case SpanishAncora:
				return SpanishAncora.get(tag);
			case FrenchPTB:
				return FrenchPTB.get(tag);
			case GermanSTTS:
				return GermanSTTS.get(tag);
			case BabelNet:
				return BabelNet.get(tag);
			case Simple:
				return Simple.get(tag);
			default:
				return null;
		}
	}

	public static Map<Tag, Character> toTag = Map.ofEntries(
			entry(Tag.ADV, 'r'),
			entry(Tag.ADJ, 'a'),
			entry(Tag.ADP, 'p'),
			entry(Tag.CCONJ, 'c'),
			entry(Tag.SCONJ, 'c'),
			entry(Tag.DET, 'd'),
			entry(Tag.INTJ, 'i'),
			entry(Tag.NUM, 'u'),
			entry(Tag.NOUN, 'n'),
			entry(Tag.PROPN, 'n'),
			entry(Tag.PRON, 'o'),
			entry(Tag.PART, 'l'),
			entry(Tag.PUNCT, 't'),
			entry(Tag.SYM, 's'),
			entry(Tag.AUX, 'v'),
			entry(Tag.VERB, 'v'),
			entry(Tag.X, 'x'));

	public static Map<String, Tag> EnglishPTB = Map.ofEntries(
			entry("#", Tag.SYM),
			entry("$", Tag.SYM),
			entry("''", Tag.PUNCT),
			entry(",", Tag.PUNCT),
			entry("-LRB-", Tag.PUNCT),
			entry("-RRB-", Tag.PUNCT),
			entry(".", Tag.PUNCT),
			entry(":", Tag.PUNCT),
			entry("AFX", Tag.ADJ),
			entry("CC", Tag.CCONJ),
			entry("CD", Tag.NUM),
			entry("DT", Tag.DET),
			entry("EX", Tag.PRON),
			entry("FW", Tag.X),
			entry("HYPH", Tag.PUNCT),
			entry("IN", Tag.ADP),
			entry("JJ", Tag.ADJ),
			entry("JJR", Tag.ADJ),
			entry("JJS", Tag.ADJ),
			entry("LS", Tag.X),
			entry("MD", Tag.VERB),
			entry("NIL", Tag.X),
			entry("NN", Tag.NOUN),
			entry("NNP", Tag.PROPN),
			entry("NNPS", Tag.PROPN),
			entry("NNS", Tag.NOUN),
			entry("PDT", Tag.DET),
			entry("Tag", Tag.PART),
			entry("PRP", Tag.PRON),
			entry("PRP$", Tag.DET),
			entry("RB", Tag.ADV),
			entry("RBR", Tag.ADV),
			entry("RBS", Tag.ADV),
			entry("RP", Tag.ADP),
			entry("SYM", Tag.SYM),
			entry("TO", Tag.PART),
			entry("UH", Tag.INTJ),
			entry("VB", Tag.VERB),
			entry("VBD", Tag.VERB),
			entry("VBG", Tag.VERB),
			entry("VBN", Tag.VERB),
			entry("VBP", Tag.VERB),
			entry("VBZ", Tag.VERB),
			entry("WDT", Tag.DET),
			entry("WP", Tag.PRON),
			entry("WP$", Tag.DET),
			entry("WRB", Tag.ADV),
			entry("``", Tag.PUNCT));

	public static Map<String, Tag> SpanishAncora = Map.ofEntries(
			entry("ao", Tag.ADJ),
			entry("aq", Tag.ADJ),
			entry("cc", Tag.CCONJ),
			entry("cs", Tag.CCONJ),
			entry("da", Tag.PART),
			entry("dd", Tag.DET),
			entry("de", Tag.DET),
			entry("di", Tag.PART),
			entry("dn", Tag.DET),
			entry("dp", Tag.PART),
			entry("dt", Tag.DET),
			entry("f0", Tag.X), //punctuations
			entry("fa", Tag.X),
			entry("fc", Tag.X),
			entry("fd", Tag.X),
			entry("fe", Tag.X),
			entry("fg", Tag.X),
			entry("fh", Tag.X),
			entry("fi", Tag.X),
			entry("fp", Tag.X),
			entry("fs", Tag.X),
			entry("ft", Tag.X),
			entry("fx", Tag.X),
			entry("fz", Tag.X),
			entry("index", Tag.INTJ),
			entry("nc", Tag.NOUN),
			entry("np", Tag.NOUN),
			entry("p0", Tag.PRON),
			entry("pd", Tag.PRON),
			entry("pe", Tag.PRON),
			entry("pi", Tag.PRON),
			entry("pn", Tag.PRON),
			entry("pp", Tag.PRON),
			entry("pr", Tag.PRON),
			entry("pt", Tag.PRON),
			entry("px", Tag.PRON),
			entry("rg", Tag.ADV),
			entry("rn", Tag.ADV),
			entry("sp", Tag.PART),
			entry("va", Tag.VERB),
			entry("vm", Tag.VERB),
			entry("vs", Tag.VERB),
			entry("w", Tag.X), //dates
			entry("z0", Tag.NOUN), //numerals
			entry("zm", Tag.NOUN),
			entry("zu", Tag.NOUN),
			entry("joker", Tag.NOUN) //artificially added
	);

	public static Map<String, Tag> FrenchPTB = Map.ofEntries(
			entry("V", Tag.VERB),
			entry("VIMP", Tag.VERB),
			entry("VINF", Tag.VERB),
			entry("VS", Tag.VERB),
			entry("VPP", Tag.VERB),
			entry("VPR", Tag.VERB),
			entry("NPP", Tag.NOUN),
			entry("NC", Tag.NOUN),
			entry("CS", Tag.CCONJ),
			entry("CC", Tag.CCONJ),
			entry("CLS", Tag.PRON),
			entry("CLO", Tag.PRON),
			entry("CLR", Tag.PRON),
			entry("P", Tag.PART),
			entry("P+D", Tag.PART),
			entry("P+PRO", Tag.PART),
			entry("I", Tag.INTJ),
			entry("PONCT", Tag.X),
			entry("ET", Tag.NOUN),    //	Borrowed
			entry("ADJWH", Tag.ADJ),
			entry("ADJ", Tag.ADJ),
			entry("ADVWH", Tag.ADV),
			entry("ADV", Tag.ADV),
			entry("PROWH", Tag.PRON),
			entry("PRORE", Tag.PRON),
			entry("PRO", Tag.PRON),
			entry("DETWH", Tag.DET),
			entry("DET", Tag.DET)
	);

	public static Map<String, Tag> GermanSTTS = Map.ofEntries(
			entry("ADJA", Tag.ADJ),    // attributive adjective
			entry("ADJD", Tag.ADJ),    // adverbial or predicative adjective
			entry("ADV", Tag.ADV),    // Adverb
			entry("APPR", Tag.PART),    // Preposition
			entry("APPRART", Tag.PART), // Preposition with article folded in
			entry("APPO", Tag.PART),    // Postposition
			entry("APZR", Tag.X),            // Right part of circumposition
			entry("ART", Tag.DET),    // definite or indefinite article
			entry("CARD", Tag.NOUN),        // cardinal number
			entry("FM", Tag.NOUN),        // foreign word
			entry("ITJ", Tag.INTJ),    // interjection
			entry("KOUI", Tag.CCONJ),    // subordinating conjunction with 'zu' and infinitive
			entry("KOUS", Tag.CCONJ),    // subordinating conjunction with sentence
			entry("KON", Tag.CCONJ),    // coordinating conjunction
			entry("KOKOM", Tag.CCONJ),    // comparative conjunction
			entry("NN", Tag.NOUN),        // common noun
			entry("NE", Tag.NOUN),        // proper noun
			entry("PDS", Tag.PRON),    // substituting demonstrative pronoun
			entry("PDAT", Tag.PRON),    // attributive demonstrative pronoun
			entry("PIS", Tag.PRON),    // substituting indefinite pronoun
			entry("PIAT", Tag.PRON),    // attributive indefinite pronoun
			entry("PIDAT", Tag.PRON),    // attributive indefinite pronoun with a determiner
			entry("PPER", Tag.PRON),    // non-reflexive personal pronoun
			entry("PPOSS", Tag.PRON),    // substituting possessive pronoun
			entry("PPOSAT", Tag.PRON),    // attribute adding posessive pronoun
			entry("PRELS", Tag.PRON),    // substituting relative pronoun
			entry("PRELAT", Tag.PRON),    // attribute adding relative pronoun
			entry("PRF", Tag.PRON),    // reflexive personal pronoun
			entry("PWS", Tag.PRON),    // substituting interrogative pronoun
			entry("PWAT", Tag.PRON),    // attribute adding interrogative pronoun
			entry("PWAV", Tag.PRON),    // adverbial interrogative or relative pronoun
			entry("PAV", Tag.ADV),    // pronominal adverb
			entry("PTKZU", Tag.X),            // 'zu' before infinitive
			entry("PTKNEG", Tag.X),        // Negation particle
			entry("PTKVZ", Tag.X),            // particle part of separable verb
			entry("PTKANT", Tag.X),        // answer particle
			entry("PTKA", Tag.X),            // particle associated with adverb or adjective
			entry("TRUNC", Tag.NOUN),    // first member of compound noun
			entry("VVFIN", Tag.VERB),    // full finite verb
			entry("VVIMP", Tag.VERB),    // full imperative
			entry("VVINF", Tag.VERB),    // full infinitive
			entry("VVIZU", Tag.VERB),    // full infinitive with "zu"
			entry("VVPP", Tag.VERB),        // full past participle
			entry("VAFIN", Tag.VERB),        // auxilliary finite verb
			entry("VAIMP", Tag.VERB),    // auxilliary imperative
			entry("VAINF", Tag.VERB),    // auxilliary infinitive
			entry("VAPP", Tag.VERB),        // auxilliary past participle
			entry("VMFIN", Tag.VERB),    // modal finite verb
			entry("VMINF", Tag.VERB),    // modal infinitive
			entry("VMPP", Tag.VERB),        // modal past participle
			entry("XY", Tag.X),            // Non word with special characters
			entry("$,", Tag.X),            // comma
			entry("$.", Tag.X),            // sentence ending punctuation
			entry("$(", Tag.X)            // other sentence internal punctuation
	);

	public static Map<String, Tag> BabelNet = Map.ofEntries(
			entry("r", Tag.ADV),
			entry("a", Tag.ADJ),
			entry("p", Tag.ADP),
			entry("c", Tag.CCONJ), // could also be Tag.SCONJ
			entry("d", Tag.DET),
			entry("i", Tag.INTJ),
			entry("u", Tag.NUM),
			entry("n", Tag.NOUN),
			entry("o", Tag.PRON),
			entry("l", Tag.PART),
			entry("t", Tag.PUNCT),
			entry("s", Tag.SYM),
			entry("v", Tag.VERB),
			entry("x", Tag.X));

	public static Map<String, Tag> Simple = Map.ofEntries(
			entry("N", Tag.NOUN),
			entry("J", Tag.ADJ),
			entry("V", Tag.VERB),
			entry("R", Tag.ADV),
			entry("X", Tag.X)
	);
}
