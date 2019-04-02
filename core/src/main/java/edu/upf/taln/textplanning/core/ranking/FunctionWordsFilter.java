package edu.upf.taln.textplanning.core.ranking;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class FunctionWordsFilter
{
	// taken from CoreNLP list at https://github.com/stanfordnlp/CoreNLP/blob/master/data/edu/stanford/nlp/patterns/surface/stopwords.txt
	// added "'re", "n't", "'d"
	// polysemous words not included, e.g. "will"
	public static Set<String> en_list = new HashSet<>(Arrays.asList("!!", "?!", "??", "!?", "`", "``", "''", "-lrb-", "-rrb-", "-lsb-", "-rsb-", ",", ".", ":", ";", "\"", "\'", "?", "<", ">", "{", "}", "[", "]", "+", "-", "(", ")", "&", "%", "$", "@", "!", "^", "#", "*", "..", "...", "'ll", "'s", "'m", "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours ", "ourselves", "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves", "###", "return", "arent", "cant", "couldnt", "didnt", "doesnt", "dont", "hadnt", "hasnt", "havent", "hes", "heres", "hows", "im", "isnt", "lets", "mustnt", "shant", "shes", "shouldnt", "thats", "theres", "theyll", "theyre", "theyve", "wasnt", "werent", "whats", "whens", "wheres", "whos", "whys", "wont", "wouldnt", "youd", "youll", "youre", "youve", "'re", "n't", "'d"));
	{
		en_list =  Collections.unmodifiableSet(en_list); 
	}
	
	public static boolean test(Mention mention, ULocale language)
	{
		return test(mention.getSurface_form(), language);
	}

	public static boolean test(String form, ULocale language)
	{
		if (Pattern.matches("\\p{Punct}+", form))
			return false;

		if (ULocale.ENGLISH.equals(language))
		{
			return !en_list.contains(form.toLowerCase());
		}
		else
			throw new RuntimeException("Language " + language.toLanguageTag() + " not supported");
	}
}
