package edu.upf.taln.textplanning.tools.evaluation.corpus;

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
	private final List<EvaluationCorpus.Sentence> sentences;
	private final int window_size;
	private final Map<Mention, List<String>> mentions2contexts;

	public CorpusContextFunction(List<EvaluationCorpus.Sentence> sentences, List<Candidate> candidates, ULocale language, int min_frequency, int window_size)
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
		final String contextId = mention.getSourceId();

		final List<String> sentence_tokens = sentences.stream()
				.filter(s -> contextId.equals(s.id))
				.findFirst()
				.map(s -> s.tokens.stream()
						.map(t -> t.wf)
						.collect(toList()))
				.orElse(List.of());


		final Pair<Integer, Integer> span = mention.getSpan();
		final Integer start = span.getLeft();
		final Integer end = span.getRight();
		final int size = sentence_tokens.size();

		final List<String> tokens_left = start == 0 ?  List.of() : sentence_tokens.subList(max(0, start - window_size), start);
		final List<String> tokens_right = end == size ? List.of() : sentence_tokens.subList(end, min(size, end + window_size));
		List<String> window = new ArrayList<>(tokens_left);
		window.addAll(tokens_right);

		return filterTokens(window);
	}
}
