package edu.upf.taln.textplanning.core.corpus;

import edu.upf.taln.textplanning.core.structures.Mention;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.alg.util.Pair;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

public class DependencyBasedAdjacencyFunction extends AdjacencyFunction
{
	private final Map<Pair<String, String>, String> dependencies;
	private final static Logger log = LogManager.getLogger();

	public DependencyBasedAdjacencyFunction(Corpora.Text text)
	{
		super(text);
		final Map<Pair<String, String>, List<Corpora.Dependency>> grouped_dependencies = text.sentences.stream()
				.flatMap(s -> s.dependencies.stream())
				.collect(groupingBy(d -> Pair.of(d.governor, d.dependent)));
		dependencies = grouped_dependencies.entrySet().stream()
				.peek(e -> { if(e.getValue().size() > 1) log.warn("Ignoring duplicate dependencies between " + e.getValue()); })
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().get(0).relation));
	}

	@Override
	public boolean test(Mention m1, Mention m2)
	{
		assert m1 != null && m2 != null && !m1.isMultiWord() && !m2.isMultiWord() : m1 + " " + m2;
		if (m1.equals(m2))
			return false;

		// returns true if m1 is the governor of m2
		return dependencies.containsKey(Pair.of(m1.getId(), m2.getId()));

	}

	@Override
	public String getLabel(Mention m1, Mention m2)
	{
		assert m1 != null && m2 != null && !m1.isMultiWord() && !m2.isMultiWord() : m1 + " " + m2;

		return dependencies.get(Pair.of(m1.getId(), m2.getId()));
	}

}
