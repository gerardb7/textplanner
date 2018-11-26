package edu.upf.taln.textplanning.amr.structures;

import com.google.common.collect.HashMultimap;
import edu.upf.taln.textplanning.core.structures.Mention;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public class CoreferenceChain implements Serializable
{
	private HashMultimap<String, Mention> coreferents = HashMultimap.create();

	public int getSize() { return coreferents.size(); }
	public void put(String v, Mention m) { coreferents.put(v, m); }
	public Collection<String> getVertices()
	{
		return coreferents.keySet();
	}
	public Collection<Mention> getMentionsForVertex(String v) { return coreferents.get(v); }

	void replaceVertex(String v_old, String v_new)
	{
		Set<Mention> mentions = coreferents.get(v_old);
		coreferents.removeAll(v_old);
		coreferents.putAll(v_new, mentions);
	}

	public String toString()
	{
		StringBuilder s = new StringBuilder();
		return "Chain of size " + getSize() + ": " + coreferents.values().stream()
				.sorted(Comparator.comparing(Mention::getSentenceId))
				.map(m ->  m + " in sentence " + m.getSentenceId())
				.collect(Collectors.joining(","));
	}
}
