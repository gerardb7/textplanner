package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import edu.upf.taln.textplanning.input.amr.AMR;
import edu.upf.taln.textplanning.structures.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class AMRReader implements DocumentReader
{
	private final boolean keep_inverse_relations; // If false -> convert ':*-of' relations to their non-inverted counterparts
	private final boolean keep_relation_alignments; // If true -> move relation alignments to their target variables
    private final static Logger log = LoggerFactory.getLogger(AMRReader.class);

    public AMRReader() { keep_inverse_relations = true; keep_relation_alignments = false; }
	@SuppressWarnings("unused")
	public AMRReader(boolean keep_inverse_relations, boolean keep_relation_alignments)
	{
		this.keep_inverse_relations = keep_inverse_relations;
		this.keep_relation_alignments = keep_relation_alignments;
	}

    public List<SemanticGraph> read(String amrBank)
    {
	    Stopwatch timer = Stopwatch.createStarted();
    	List<SemanticGraph> graphs = new ArrayList<>();

        String[] graphs_text = amrBank.split("\n\n");
        for(int i=0; i < graphs_text.length; ++i)
        {
	        try
	        {
		        String[] lines = graphs_text[i].split("\n");
		        if (lines.length > 0 && !Arrays.stream(lines).allMatch(l -> l.startsWith("#")))
		        {
			        String id = readSentenceId(lines[0]).orElse(Integer.toString(i));
			        List<String> tokens = readTokens(lines[1]);
			        String amr_text = Arrays.stream(lines, 3, lines.length)
					        .collect(joining("\n"));

			        AMRActions actions = new AMRActions(id, this.keep_inverse_relations, this.keep_relation_alignments);
			        AMR.parse(amr_text, actions);
			        SemanticGraph graph = actions.getGraph();
			        GraphAlignments alignments = new GraphAlignments(graph, i,
					        actions.getAlignments(), tokens);
			        graph.setAlignments(alignments);
			        graphs.add(graph);
		        }
            }
            catch(Exception e)
            {
                log.error("Failed to read graph " + i + ": " + e);
            }
        }

	    log.info(graphs.size() + " graphs read in " + timer.stop());
        return graphs;
    }

    private Optional<String> readSentenceId(String line) throws Exception
    {
        try{ return getMatch("#\\s::id\\s(\\S+).*", line); }
        catch (Exception e) { throw new Exception("Cannot read sentence id: " + e); }
    }

    private List<String> readTokens(String line) throws Exception
    {
        try
        {
            Optional<String> toks = getMatch("#\\s::tok\\s(.*)", line);
            return toks.map(s -> s.split(" ")).map(Arrays::asList).orElse(Collections.emptyList());
        }
        catch (Exception e) { throw new Exception("Cannot read tokens: " + e); }
    }

    private List<Pair<Integer, List<Integer>>> readAlignments(String line)
    {
        try
        {
            Optional<String> align = getMatch("#\\s::alignments\\s(.*)", line);
            return align.map(s -> s.split(" "))
                    .map(Arrays::asList)
                    .map(l -> l.stream()
                            .map(a -> a.split("-"))
                            .map(p -> Pair.of(
                                    Integer.parseInt(p[0]),
                                    Arrays.stream(p[1].split("\\."))
                                            .map(i -> i.equalsIgnoreCase("r") ? "0" : i) // replace 'r' with '0'
                                            .map(Integer::parseInt)
                                            .collect(toList())))
                            .collect(toList()))
		            .orElse(Collections.emptyList());
        }
        catch (Exception e) { throw new RuntimeException("Cannot read alignments: " + e);  }
    }


    private static Optional<String> getMatch(String pattern, String line)
    {
        Pattern p = Pattern.compile(pattern);
        return Stream.of(line)
                .map(p::matcher)
                .filter(Matcher::matches)
                .map(o -> o.group(1))
                .findFirst();
    }
}
