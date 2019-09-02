package edu.upf.taln.textplanning.core.corpus;

import com.ibm.icu.util.ULocale;
import edu.upf.taln.textplanning.core.ranking.FunctionWordsFilter;
import edu.upf.taln.textplanning.core.structures.Mention;
import edu.upf.taln.textplanning.core.utils.POS;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static java.lang.Math.min;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class MentionsCollector
{
	public static List<Mention> collect(Corpora.Text text, POS.Tagset tagset, int max_span_size,
	                                    Set<POS.Tag> ignore_pos_tags, ULocale language)
	{
		if (text.sentences.isEmpty())
			return List.of();

		// Calculate sentence boundaries in token offsets
		final List<Integer> sentence_offsets = new ArrayList<>();
		sentence_offsets.add(0);

		for (int i=1; i < text.sentences.size(); ++i)
		{
			final Integer offset = sentence_offsets.get(i - 1);
			final int num_tokens = text.sentences.get(i - 1).tokens.size();
			sentence_offsets.add(offset + num_tokens);
		}

		// create mention objects
		return IntStream.range(0, text.sentences.size())
				.mapToObj(s_i ->
						IntStream.range(0, text.sentences.get(s_i).tokens.size())
								.mapToObj(start -> IntStream.range(start + 1, min(start + max_span_size + 1, text.sentences.get(s_i).tokens.size() + 1))
										.filter(end ->
										{
											final POS.Tag tag = POS.get(text.sentences.get(s_i).tokens.get(start).pos, tagset);
											// single words must have a pos tag other than 'X'
											if (end - start == 1)
												return !ignore_pos_tags.contains(tag);
												// multiwords must contain at least one nominal token
											else
												return text.sentences.get(s_i).tokens.subList(start, end).stream()
														.anyMatch(t -> POS.get(t.pos, tagset) == POS.Tag.NOUN);
										})
										.mapToObj(end ->
										{
											final List<Corpora.Token> tokens = text.sentences.get(s_i).tokens.subList(start, end);
											final List<String> token_forms = tokens.stream()
													.map(t -> t.wf)
													.collect(toList());
											final String lemma = tokens.stream()
													.map(t -> t.lemma != null ? t.lemma : t.wf)
													.collect(joining(" "));
											POS.Tag pos = tokens.size() == 1 ? POS.get(tokens.get(0).pos, tagset) : POS.Tag.NOUN;
											String contextId = tokens.size() == 1 ? tokens.get(0).id : tokens.get(0).id + "-" + tokens.get(tokens.size()-1).id;
											final Pair<Integer, Integer> span = Pair.of(sentence_offsets.get(s_i) + start, sentence_offsets.get(s_i) + end);

											return new Mention(contextId, text.sentences.get(s_i).id, span, token_forms, lemma, pos, false, "");
										})
										.filter(m -> FunctionWordsFilter.test(m.getSurfaceForm(), language)))
								.flatMap(stream -> stream))
				.flatMap(stream -> stream)
				.collect(toList());
	}
}
