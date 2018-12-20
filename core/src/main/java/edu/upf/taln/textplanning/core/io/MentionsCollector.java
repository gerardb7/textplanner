package edu.upf.taln.textplanning.core.io;

import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.core.structures.Mention;

public interface MentionsCollector<T>
{
	Multimap<String, Mention> collectMentions(T contents);
}
