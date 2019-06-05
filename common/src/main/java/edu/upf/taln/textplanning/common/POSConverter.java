package edu.upf.taln.textplanning.common;

import com.babelscape.util.UniversalPOS;

import java.util.HashMap;
import java.util.Map;

/**
 * Taken from https://github.com/albarron/LumpSTS/blob/master/src/main/java/cat/lump/sts2017/babelNet/PoSMaps.java
 * Mapping PoS tags for Arabic, English and Spanish into the BabelNetWrapper tag set.
 */
public class POSConverter
{

	/**
	 * Mapping Arabic Mada 2.1 PoS tags into BabelNetWrapper.
	 * There is a 1-to-1 correspondence with English Penn Tree Bank
	 */
	static final Map<String, UniversalPOS> BN_POS_AR = new HashMap<>()
	{
		private static final long serialVersionUID = 1652098132934864030L;

		{
			put("noun", UniversalPOS.NOUN);
			put("noun_num", UniversalPOS.NOUN);
			put("noun_quant", UniversalPOS.NOUN);
			put("noun_prop", UniversalPOS.NOUN);
			put("adj", UniversalPOS.ADJ);
			put("adj_comp", UniversalPOS.ADJ);
			put("adj_num", UniversalPOS.ADJ);
			put("adv", UniversalPOS.ADV);
			put("adv_interrog", UniversalPOS.ADV);
			put("adv_rel", UniversalPOS.ADV);
			put("pron", UniversalPOS.PRON);
			put("pron_dem", UniversalPOS.PRON);
			put("pron_exclam", UniversalPOS.PRON);
			put("pron_interrog", UniversalPOS.PRON);
			put("pron_rel", UniversalPOS.PRON);
			put("verb", UniversalPOS.VERB);
			put("verb_pseudo", UniversalPOS.VERB);
			put("part", UniversalPOS.PART);
			put("part_dem", UniversalPOS.DET);
			put("part_det", UniversalPOS.DET);
			put("part_focus", UniversalPOS.PART);
			put("part_fut", UniversalPOS.PART);
			put("part_interrog", UniversalPOS.PART);
			put("part_neg", null); // think (RPs in PTB)
			put("part_restrict", UniversalPOS.PART);
			put("part_verb", UniversalPOS.PART);
			put("part_voc", UniversalPOS.PART);
			put("prep", UniversalPOS.PART);
			put("abbrev", UniversalPOS.NOUN);
			put("punc", null);
			put("conj", UniversalPOS.CCONJ);
			put("conj_sub", UniversalPOS.CCONJ);
			put("interj", UniversalPOS.INTJ);
			put("digit", UniversalPOS.NOUN); //think determiner?
			put("latin", UniversalPOS.NOUN);  //think
			put("joker", UniversalPOS.NOUN); //artificially added
		}
	};

	/**
	 * Mapping English Penn Tree Bank PoS into BabelNetWrapper
	 */
	static final Map<String, UniversalPOS> BN_POS_EN = new HashMap<String, UniversalPOS>()
	{
		private static final long serialVersionUID = 1652098132934864031L;

		{
			put("CC", UniversalPOS.CCONJ);
			put("CD", UniversalPOS.NOUN); //think determiner?
			put("DT", UniversalPOS.DET);
			put("EX", null); //think
			put("FW", UniversalPOS.NOUN); //think
			put("IN", UniversalPOS.PART);
			put("J", UniversalPOS.ADJ); // simplified tagset
			put("JJ", UniversalPOS.ADJ);
			put("JJR", UniversalPOS.ADJ);
			put("JJS", UniversalPOS.ADJ);
			put("LS", null); //think
			put("MD", UniversalPOS.VERB);
			put("N", UniversalPOS.NOUN); // simplified tagset
			put("NN", UniversalPOS.NOUN);
			put("NNS", UniversalPOS.NOUN);
			put("NNP", UniversalPOS.NOUN);
			put("NNPS", UniversalPOS.NOUN);
			put("PDT", UniversalPOS.DET);
			put("POS", null); //think
			put("PRP", UniversalPOS.PRON);
			put("PRP$", UniversalPOS.PRON);
			put("R", UniversalPOS.ADV); // simplified tagset
			put("RB", UniversalPOS.ADV);
			put("RBR", UniversalPOS.ADV);
			put("RBS", UniversalPOS.ADV);
			put("RP", null); //think
			put("SYM", null); //think
			put("TO", UniversalPOS.PART);
			put("UH", UniversalPOS.INTJ);
			put("V", UniversalPOS.VERB); // simplified tagset
			put("VB", UniversalPOS.VERB);
			put("VBD", UniversalPOS.VERB);
			put("VBG", UniversalPOS.VERB);
			put("VBN", UniversalPOS.VERB);
			put("VBP", UniversalPOS.VERB);
			put("VBZ", UniversalPOS.VERB);
			put("WDT", UniversalPOS.DET);
			put("WP", UniversalPOS.PRON);
			put("WP$", UniversalPOS.PRON);
			put("WRB", UniversalPOS.ADV);
			// Lowercased version
			put("cc", UniversalPOS.CCONJ);
			put("cd", UniversalPOS.NOUN); //think determiner?
			put("dt", UniversalPOS.DET);
			put("ex", null); //think
			put("fw", UniversalPOS.NOUN); //think
			put("in", UniversalPOS.PART);
			put("j", UniversalPOS.ADJ); // simplified tagset
			put("jj", UniversalPOS.ADJ);
			put("jjr", UniversalPOS.ADJ);
			put("jjs", UniversalPOS.ADJ);
			put("ls", null); //think
			put("md", UniversalPOS.VERB);
			put("n", UniversalPOS.NOUN); // simplified tagset
			put("nn", UniversalPOS.NOUN);
			put("nns", UniversalPOS.NOUN);
			put("nnp", UniversalPOS.NOUN);
			put("nnps", UniversalPOS.NOUN);
			put("pdt", UniversalPOS.DET);
			put("pos", null); //think
			put("prp", UniversalPOS.PRON);
			put("prp$", UniversalPOS.PRON);
			put("r", UniversalPOS.ADV); // simplified tagset
			put("rb", UniversalPOS.ADV);
			put("rbr", UniversalPOS.ADV);
			put("rbs", UniversalPOS.ADV);
			put("rp", null); //think
			put("sym", null); //think
			put("to", UniversalPOS.PART);
			put("uh", UniversalPOS.INTJ);
			put("v", UniversalPOS.VERB); // simplified tagset
			put("vb", UniversalPOS.VERB);
			put("vbd", UniversalPOS.VERB);
			put("vbg", UniversalPOS.VERB);
			put("vbn", UniversalPOS.VERB);
			put("vbp", UniversalPOS.VERB);
			put("vbz", UniversalPOS.VERB);
			put("wdt", UniversalPOS.DET);
			put("wp", UniversalPOS.PRON);
			put("wp$", UniversalPOS.PRON);
			put("wrb", UniversalPOS.ADV);
			put("joker", UniversalPOS.NOUN); //artificially added
		}
	};

	/**
	 * Mapping a subset of the Ancora tagset into BabelNetWrapper.
	 * Since the Ancora tagset is much more detailed than Babelnet we only
	 * consider the first two characters to do the mapping.
	 */
	static final Map<String, UniversalPOS> BN_POS_ES = new HashMap<String, UniversalPOS>()
	{
		private static final long serialVersionUID = 1652098132934864032L;

		{
			put("ao", UniversalPOS.ADJ);
			put("aq", UniversalPOS.ADJ);
			put("cc", UniversalPOS.CCONJ);
			put("cs", UniversalPOS.CCONJ);
			put("da", UniversalPOS.PART);
			put("dd", UniversalPOS.DET);
			put("de", UniversalPOS.DET);
			put("di", UniversalPOS.PART);
			put("dn", UniversalPOS.DET);
			put("dp", UniversalPOS.PART);
			put("dt", UniversalPOS.DET);
			put("f0", null); //punctuations
			put("fa", null);
			put("fc", null);
			put("fd", null);
			put("fe", null);
			put("fg", null);
			put("fh", null);
			put("fi", null);
			put("fp", null);
			put("fs", null);
			put("ft", null);
			put("fx", null);
			put("fz", null);
			put("index", UniversalPOS.INTJ);
			put("nc", UniversalPOS.NOUN);
			put("np", UniversalPOS.NOUN);
			put("p0", UniversalPOS.PRON);
			put("pd", UniversalPOS.PRON);
			put("pe", UniversalPOS.PRON);
			put("pi", UniversalPOS.PRON);
			put("pn", UniversalPOS.PRON);
			put("pp", UniversalPOS.PRON);
			put("pr", UniversalPOS.PRON);
			put("pt", UniversalPOS.PRON);
			put("px", UniversalPOS.PRON);
			put("rg", UniversalPOS.ADV);
			put("rn", UniversalPOS.ADV);
			put("sp", UniversalPOS.PART);
			put("va", UniversalPOS.VERB);
			put("vm", UniversalPOS.VERB);
			put("vs", UniversalPOS.VERB);
			put("w", null); //dates
			put("z0", UniversalPOS.NOUN); //numerals
			put("zm", UniversalPOS.NOUN);
			put("zu", UniversalPOS.NOUN);
			put("joker", UniversalPOS.NOUN); //artificially added
		}
	};


	/**
	 * Mapping TS Wikipedia Data Set for Turkish which is the one used by the TS tagget
	 */
	static final Map<String, UniversalPOS> BN_POS_TR = new HashMap<String, UniversalPOS>()
	{
		private static final long serialVersionUID = 1652098132934864033L;

		{
			put("Verb", UniversalPOS.VERB);
			put("Noun", UniversalPOS.NOUN);
			put("Adj", UniversalPOS.ADJ);
			put("Adv", UniversalPOS.ADV);
			put("Det", UniversalPOS.DET);
			put("Conj", UniversalPOS.CCONJ);
			put("Postp", UniversalPOS.PART);         // Postposition
			put("Interj", UniversalPOS.INTJ);        // Interjection
			put("Pron", UniversalPOS.PRON);
			put("Dup", null);           // Duplication
			put("Num", UniversalPOS.NOUN); //think determiner?       //	Number
			put("Punc", null);
			put("UnDef", UniversalPOS.PART);     // Undefinite
			put("Ques", null);     //Question
			put("YY", null);       //	Misspell
			put("Abbr", UniversalPOS.NOUN);     //	Abbreviation
			put("intEmphasis", null);   //	Internet Emphasis
			put("intAbbrEng", null);    //	Internet English Abbreviation
			put("tinglish", null);        //	Tinglish
			put("bor", UniversalPOS.NOUN);    //	Borrowed
			put("intSlang", null);
			put("joker", UniversalPOS.NOUN); //artificially added
		}
	};


	/**
	 * Mapping from the French Penn TreeBank. In the original FTB, words are split into 13 main
	 * categories, themselves divided into 34 subcategories. The version of the treebank we used was
	 * obtained by converting subcategories into a tagset consisting of 28 tags, with a granularity
	 * that is intermediate between categories and subcategories.
	 * https://alpage.inria.fr/statgram/frdep/Publications/crabbecandi-taln2008-final.pdf
	 */
	static final Map<String, UniversalPOS> BN_POS_FR = new HashMap<String, UniversalPOS>()
	{
		private static final long serialVersionUID = 1652098132934864034L;

		{
			put("V", UniversalPOS.VERB);
			put("VIMP", UniversalPOS.VERB);
			put("VINF", UniversalPOS.VERB);
			put("VS", UniversalPOS.VERB);
			put("VPP", UniversalPOS.VERB);
			put("VPR", UniversalPOS.VERB);
			put("NPP", UniversalPOS.NOUN);
			put("NC", UniversalPOS.NOUN);
			put("CS", UniversalPOS.CCONJ);
			put("CC", UniversalPOS.CCONJ);
			put("CLS", UniversalPOS.PRON);
			put("CLO", UniversalPOS.PRON);
			put("CLR", UniversalPOS.PRON);
			put("P", UniversalPOS.PART);
			put("P+D", UniversalPOS.PART);
			put("P+PRO", UniversalPOS.PART);
			put("I", UniversalPOS.INTJ);
			put("PONCT", null);
			put("ET", UniversalPOS.NOUN);    //	Borrowed
			put("ADJWH", UniversalPOS.ADJ);
			put("ADJ", UniversalPOS.ADJ);
			put("ADVWH", UniversalPOS.ADV);
			put("ADV", UniversalPOS.ADV);
			put("PROWH", UniversalPOS.PRON);
			put("PRORE", UniversalPOS.PRON);
			put("PRO", UniversalPOS.PRON);
			put("DETWH", UniversalPOS.DET);
			put("DET", UniversalPOS.DET);
		}
	};


	/**
	 * Mapping from the STTS Stuttgart Tübingen POS tagset for German
	 * http://paula.petcu.tm.ro/init/default/post/opennlp-part-of-speech-tags
	 */
	static final Map<String, UniversalPOS> BN_POS_DE = new HashMap<String, UniversalPOS>()
	{
		private static final long serialVersionUID = 1652098132934864035L;

		{
			put("ADJA", UniversalPOS.ADJ);    // attributive adjective
			put("ADJD", UniversalPOS.ADJ);    // adverbial or predicative adjective
			put("ADV", UniversalPOS.ADV);    // Adverb
			put("APPR", UniversalPOS.PART);    // Preposition
			put("APPRART", UniversalPOS.PART); // Preposition with article folded in
			put("APPO", UniversalPOS.PART);    // Postposition
			put("APZR", null);            // Right part of circumposition
			put("ART", UniversalPOS.DET);    // definite or indefinite article
			put("CARD", UniversalPOS.NOUN);        // cardinal number
			put("FM", UniversalPOS.NOUN);        // foreign word
			put("ITJ", UniversalPOS.INTJ);    // interjection
			put("KOUI", UniversalPOS.CCONJ);    // subordinating conjunction with 'zu' and infinitive
			put("KOUS", UniversalPOS.CCONJ);    // subordinating conjunction with sentence
			put("KON", UniversalPOS.CCONJ);    // coordinating conjunction
			put("KOKOM", UniversalPOS.CCONJ);    // comparative conjunction
			put("NN", UniversalPOS.NOUN);        // common noun
			put("NE", UniversalPOS.NOUN);        // proper noun
			put("PDS", UniversalPOS.PRON);    // substituting demonstrative pronoun
			put("PDAT", UniversalPOS.PRON);    // attributive demonstrative pronoun
			put("PIS", UniversalPOS.PRON);    // substituting indefinite pronoun
			put("PIAT", UniversalPOS.PRON);    // attributive indefinite pronoun
			put("PIDAT", UniversalPOS.PRON);    // attributive indefinite pronoun with a determiner
			put("PPER", UniversalPOS.PRON);    // non-reflexive personal pronoun
			put("PPOSS", UniversalPOS.PRON);    // substituting possessive pronoun
			put("PPOSAT", UniversalPOS.PRON);    // attribute adding posessive pronoun
			put("PRELS", UniversalPOS.PRON);    // substituting relative pronoun
			put("PRELAT", UniversalPOS.PRON);    // attribute adding relative pronoun
			put("PRF", UniversalPOS.PRON);    // reflexive personal pronoun
			put("PWS", UniversalPOS.PRON);    // substituting interrogative pronoun
			put("PWAT", UniversalPOS.PRON);    // attribute adding interrogative pronoun
			put("PWAV", UniversalPOS.PRON);    // adverbial interrogative or relative pronoun
			put("PAV", UniversalPOS.ADV);    // pronominal adverb
			put("PTKZU", null);            // 'zu' before infinitive
			put("PTKNEG", null);        // Negation particle
			put("PTKVZ", null);            // particle part of separable verb
			put("PTKANT", null);        // answer particle
			put("PTKA", null);            // particle associated with adverb or adjective
			put("TRUNC", UniversalPOS.NOUN);    // first member of compound noun
			put("VVFIN", UniversalPOS.VERB);    // full finite verb
			put("VVIMP", UniversalPOS.VERB);    // full imperative
			put("VVINF", UniversalPOS.VERB);    // full infinitive
			put("VVIZU", UniversalPOS.VERB);    // full infinitive with "zu"
			put("VVPP", UniversalPOS.VERB);        // full past participle
			put("VAFIN", UniversalPOS.VERB);        // auxilliary finite verb
			put("VAIMP", UniversalPOS.VERB);    // auxilliary imperative
			put("VAINF", UniversalPOS.VERB);    // auxilliary infinitive
			put("VAPP", UniversalPOS.VERB);        // auxilliary past participle
			put("VMFIN", UniversalPOS.VERB);    // modal finite verb
			put("VMINF", UniversalPOS.VERB);    // modal infinitive
			put("VMPP", UniversalPOS.VERB);        // modal past participle
			put("XY", null);            // Non word with special characters
			put("$,", null);            // comma
			put("$.", null);            // sentence ending punctuation
			put("$(", null);            // other sentence internal punctuation
		}
	};
}


	/*
	 *
	 * ENGLISH

	Alphabetical list of part-of-speech tags used in the Penn Treebank Project:
	https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
	Number Tag Description
	1. 		CC 		Coordinating conjunction
	2. 		CD 		Cardinal number
	3. 		DT 		Determiner
	4. 		EX 		Existential there
	5. 		FW 		Foreign word
	6. 		IN 		Preposition or subordinating conjunction
	7. 		JJ 		Adjective
	8. 		JJR 	Adjective, comparative
	9. 		JJS 	Adjective, superlative
	10. 	LS 		List item marker
	11. 	MD 		Modal
	12. 	NN 		Noun, singular or mass
	13. 	NNS 	Noun, plural
	14. 	NNP 	Proper noun, singular
	15. 	NNPS 	Proper noun, plural
	16. 	PDT 	Predeterminer
	17. 	POS 	Possessive ending
	18. 	PRP 	Personal pronoun
	19. 	PRP$ 	Possessive pronoun
	20. 	RB 		Adverb
	21. 	RBR 	Adverb, comparative
	22. 	RBS 	Adverb, superlative
	23. 	RP 		Particle
	24. 	SYM 	Symbol
	25. 	TO 		to
	26. 	UH 		Interjection
	27. 	VB 		Verb, base form
	28. 	VBD 	Verb, past tense
	29. 	VBG 	Verb, gerund or present participle
	30. 	VBN 	Verb, past participle
	31. 	VBP 	Verb, non-3rd person singular present
	32. 	VBZ 	Verb, 3rd person singular present
	33. 	WDT 	Wh-determiner
	34. 	WP 		Wh-pronoun
	35. 	WP$ 	Possessive wh-pronoun
	36. 	WRB 	Wh-adverb
	* SPANISH
	*
	* http://nlp.stanford.edu/software/spanish-faq.shtml#tagset
	Tag	Description	Example(s)
	Adjectives
	ao0000	Adjective (ordinal)	primera, segundo, últimos
	aq0000	Adjective (descriptive)	populares, elegido, emocionada, andaluz
	Conjunctions
	cc	Conjunction (coordinating)	y, o, pero
	cs	Conjunction (subordinating)	que, como, mientras
	Determiners
	da0000	Article (definite)	el, la, los, las
	dd0000	Demonstrative	este, esta, esos
	de0000	"Exclamative" 	qué (¡Qué pobre!)
	di0000	Article (indefinite)	un, muchos, todos, otros
	dn0000	Numeral	tres, doscientas
	dp0000	Possessive	sus, mi
	dt0000	Interrogative	cuántos, qué, cuál
	Punctuation
	f0	Binary	&, @
	faa	Inverted exclamation mark	¡
	fat	Exclamation mark	!
	fc	Comma	,
	fd	Colon	:
	fe	Double quote	"
	fg	Hyphen	-
	fh	Forward slash	/
	fia	Inverted question mark	¿
	fit	Question mark	?
	fp	Period / full-stop	.
	fpa	Left parenthesis	(
	fpt	Right parenthesis	)
	fs	Ellipsis	..., etcétera
	ft	Percent sign	%
	fx	Semicolon	;
	fz	Single quote	'
	Interjections
	index	Interjection	ay, ojalá, hola
	Nouns
	nc00000	Unknown common noun (neologism, loanword)	minidisc, hooligans, re-flotamiento
	nc0n000	Common noun (invariant number)	hipótesis, campus, golf
	nc0p000	Common noun (plural)	años, elecciones
	nc0s000	Common noun (singular)	lista, hotel, partido
	np00000	Proper noun	Málaga, Parlamento, UFINSA
	Pronouns
	p0000000	Impersonal se	se
	pd000000	Demonstrative pronoun	éste, eso, aquellas
	pe000000	"Exclamative" pronoun	qué
	pi000000	Indefinite pronoun	muchos, uno, tanto, nadie
	pn000000	Numeral pronoun	dos miles, ambos
	pp000000	Personal pronoun	ellos, lo, la, nos
	pr000000	Relative pronoun	que, quien, donde, cuales
	pt000000	Interrogative pronoun	cómo, cuánto, qué
	px000000	Possessive pronoun	tuyo, nuestra
	Adverbs
	rg	Adverb (general)	siempre, más, personalmente
	rn	Adverb (negating)	no
	Prepositions
	sp000	Preposition	en, de, entre
	Verbs
	vag0000	Verb (auxiliary, gerund)	habiendo
	vaic000	Verb (auxiliary, indicative, conditional)	habría, habríamos
	vaif000	Verb (auxiliary, indicative, future)	habrá, habremos
	vaii000	Verb (auxiliary, indicative, imperfect)	había, habíamos
	vaip000	Verb (auxiliary, indicative, present)	ha, hemos
	vais000	Verb (auxiliary, indicative, preterite)	hubo, hubimos
	vam0000	Verb (auxiliary, imperative)	haya
	van0000	Verb (auxiliary, infinitive)	haber
	vap0000	Verb (auxiliary, participle)	habido
	vasi000	Verb (auxiliary, subjunctive, imperfect)	hubiera, hubiéramos, hubiese
	vasp000	Verb (auxiliary, subjunctive, present)	haya, hayamos
	vmg0000	Verb (main, gerund)	dando, trabajando
	vmic000	Verb (main, indicative, conditional)	daría, trabajaríamos
	vmif000	Verb (main, indicative, future)	dará, trabajaremos
	vmii000	Verb (main, indicative, imperfect)	daba, trabajábamos
	vmip000	Verb (main, indicative, present)	da, trabajamos
	vmis000	Verb (main, indicative, preterite)	dio, trabajamos
	vmm0000	Verb (main, imperative)	da, dé, trabaja, trabajes, trabajemos
	vmn0000	Verb (main, infinitive)	dar, trabjar
	vmp0000	Verb (main, participle)	dado, trabajado
	vmsi000	Verb (main, subjunctive, imperfect)	diera, diese, trabajáramos, trabajésemos
	vmsp000	Verb (main, subjunctive, present)	dé, trabajemos
	vsg0000	Verb (semiauxiliary, gerund)	siendo
	vsic000	Verb (semiauxiliary, indicative, conditional)	sería, serían
	vsif000	Verb (semiauxiliary, indicative, future)	será, seremos
	vsii000	Verb (semiauxiliary, indicative, imperfect)	era, éramos
	vsip000	Verb (semiauxiliary, indicative, present)	es, son
	vsis000	Verb (semiauxiliary, indicative, preterite)	fue, fuiste
	vsm0000	Verb (semiauxiliary, imperative)	sea, sé
	vsn0000	Verb (semiauxiliary, infinitive)	ser
	vsp0000	Verb (semiauxiliary, participle)	sido
	vssf000	Verb (semiauxiliary, subjunctive, future)	fuere
	vssi000	Verb (semiauxiliary, subjunctive, imperfect)	fuera, fuese, fuéramos
	vssp000	Verb (semiauxiliary, subjunctive, present)	sea, seamos
	Dates
	w	Date	octubre, jueves, 2002
	Numerals
	z0	Numeral	547.000, 04, 52,52
	zm	Numeral qualifier (currency)	dólares, euros
	zu	Numeral qualifier (other units)	km, cc
	TURKISH
	TS Wikipedia Data Set PosTag List
	#
	POSTag
	TAG
	Tag Used in Data Set
	1
	Verb
	Verb
	_Verb
	2
	Noun
	Noun
	_Noun
	3
	Adj
	Adjective
	_Adj
	4
	Adv
	Adverb
	_Adverb
	5
	Det
	Determiner
	_Det
	6
	Conj
	Conjunction
	_Conj
	7
	Postp
	Postposition
	_Postp
	8
	Interj
	Interjection
	_Interj
	9
	Pron
	Pronoun
	_Pron
	10
	Dup
	Duplication
	_Dup
	11
	Num
	Number
	_Num
	12
	Punc
	Punctuation
	_Punc
	13
	UnDef
	Undefinite
	_UnDef
	14
	Ques
	Question
	_Ques
	15
	YY
	Misspell
	_YY
	16
	Abbr
	Abbreviation
	_Abbr
	17
	intEmphasis
	Internet Emphasis
	_intEmphasis
	18
	intAbbrEng
	Internet English Abbreviation
	_intAbbrEnglish
	19
	tinglish
	Tinglish
	_tinglish
	20
	bor
	Borrowed
	_bor
	21
	intSlang
	Internet Slang
	_intSlang
	The tags “YY, Abbr, intEmphasis, intAbbrEng, tinglish, bor and intSlang” are processed by
	* FRENCH
	* http://www.llf.cnrs.fr/Gens/Abeille/French-Treebank-fr.php
	*
	* The tagset with the 28 tags is on page 8 of this paper:
	 http://alpage.inria.fr/statgram/frdep/Publications/crabbecandi-taln2008-final.pdf
	*
	TAG	CAT	SOUS MODE
	V	V	-	indicatif
	VIMP	V	-	impératif
	VINF	V	-	infinitif
	VS	V	-	subjonctif
	VPP	V	-	participe passé
	VPR	V	-	participe présent
	NPP	N	P	-
	NC	N	C	-
	CS	C	S	-
	CC	C	C	-
	CLS	CL	suj	-
	CLO	CL	obj	-
	CLR	CL	refl	-
	P	P	-	-
	P+D	voir texte
	P+PRO	voir texte
	I	I	-	-
	PONCT	PONCT	-	-
	ET	ET	-	-
	ADJWH	A	int	-
	ADJ	A	¬int	-
	ADVWH	ADV	int	-
	ADV	ADV	¬int	-
	PROWH	PRO	int	-
	PROREL	PRO	rel	-
	PRO	PRO	¬(int | rel)	-
	DETWH	D	int	-
	DET	D	¬int	-
	*  GERMAN
	*  http://paula.petcu.tm.ro/init/default/post/opennlp-part-of-speech-tags
	*
	*  Table 3. Supposed POS tagset for German (the STTS Stuttgart Tübingen tag set)
	Number

	Tag

	Description
	1. 	ADJA 	attributive adjective
	2. 	ADJD 	adverbial or predicative adjective
	3. 	ADV 	Adverb
	4. 	APPR 	Preposition
	5. 	APPRART 	Preposition with article folded in
	6. 	APPO 	Postposition
	7. 	APZR 	Right part of circumposition
	8. 	ART 	definite or indefinite article
	9. 	CARD 	cardinal number
	10. 	FM 	foreign word
	11. 	ITJ 	interjection
	12. 	KOUI 	subordinating conjunction with 'zu' and infinitive
	13. 	KOUS 	subordinating conjunction with sentence
	14. 	KON 	coordinating conjunction
	15. 	KOKOM 	comparative conjunction
	16. 	NN 	common noun
	17. 	NE 	proper noun
	18. 	PDS 	substituting demonstrative pronoun
	19. 	PDAT 	attributive demonstrative pronoun
	20. 	PIS 	substituting indefinite pronoun
	21. 	PIAT 	attributive indefinite pronoun
	22. 	PIDAT 	attributive indefinite pronoun with a determiner
	23. 	PPER 	non-reflexive personal pronoun
	24. 	PPOSS 	substituting possessive pronoun
	25. 	PPOSAT 	attribute adding posessive pronoun
	26. 	PRELS 	substituting relative pronoun
	27. 	PRELAT 	attribute adding relative pronoun
	28. 	PRF 	reflexive personal pronoun
	29. 	PWS 	substituting interrogative pronoun
	30. 	PWAT 	attribute adding interrogative pronoun
	31. 	PWAV 	adverbial interrogative or relative pronoun
	32. 	PAV 	pronominal adverb
	33. 	PTKZU 	'zu' before infinitive
	34. 	PTKNEG 	Negation particle
	35. 	PTKVZ 	particle part of separable verb
	36. 	PTKANT 	answer particle
	37. 	PTKA 	particle associated with adverb or adjective
	38. 	TRUNC 	first member of compound noun
	39. 	VVFIN 	full finite verb
	40. 	VVIMP 	full imperative
	41. 	VVINF 	full infinitive
	42. 	VVIZU 	full infinitive with "zu"
	43. 	VVPP 	full past participle
	44. 	VAFIN 	auxilliary finite verb
	45. 	VAIMP 	auxilliary imperative
	46. 	VAINF 	auxilliary infinitive
	47. 	VAPP 	auxilliary past participle
	48. 	VMFIN 	modal finite verb
	49. 	VMINF 	modal infinitive
	50. 	VMPP 	modal past participle
	51. 	XY 	Non word with special characters
	52. 	$, 	comma
	53. 	$. 	sentence ending punctuation
	54. 	$( 	other sentence internal punctuation
	*/

