package edu.upf.taln.textplanning.core.corpus;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.bias.ContextFunction;
import edu.upf.taln.textplanning.core.structures.Candidate;
import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

// Implements local context based on corpus class
public class CorpusContextFunction extends ContextFunction
{
	private final List<Corpora.Sentence> sentences;
	private final int window_size;
	private final Map<Mention, List<String>> mentions2contexts;

	public CorpusContextFunction(List<Corpora.Sentence> sentences, List<Candidate> candidates, ULocale language, int min_frequency, int window_size)
	{
		super(language, sentences.stream().flatMap(s -> s.tokens.stream().map(tok -> tok.wf))
				.collect(toList()), min_frequency, candidates);
		this.sentences = sentences;
		this.window_size = window_size;

		this.mentions2contexts = candidates.stream()
				.map(Candidate::getMention)
				.distinct()
				.collect(toMap(mention -> mention, this::calculateWindow));
	}

	@Override
	protected List<String> getWindow(Mention mention)
	{
		return mentions2contexts.get(mention);
	}

	private List<String> calculateWindow(Mention mention)
	{
		final List<Corpora.Token> tokens = sentences.stream()
				.flatMap(s -> s.tokens.stream())
				.collect(toList());

		final Pair<Integer, Integer> span = mention.getSpan();
		final Integer start = span.getLeft();
		final Integer end = span.getRight();
		final int size = tokens.size();

		final List<Corpora.Token> tokens_left = start == 0 ?  List.of() : tokens.subList(max(0, start - window_size), start);
		final List<Corpora.Token> tokens_right = end == size ? List.of() : tokens.subList(end, min(size, end + window_size));
		List<Corpora.Token> window = new ArrayList<>(tokens_left);
		window.addAll(tokens_right);
		final List<String> window_forms = window.stream().filter(t -> t.id.startsWith(mention.getContextId())).map(t -> t.wf).collect(toList());

		return filterTokens(window_forms);
	}
}
