package edu.upf.taln.textplanning.input;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.input.amr.AMR;
import edu.upf.taln.textplanning.structures.Role;
import edu.upf.taln.textplanning.structures.amr.SemanticGraph;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class AMRReader implements DocumentReader
{
	private final boolean keep_inverse_relations; // If false -> convert ':*-of' relations to their non-inverted counterparts
	private final boolean keep_relation_alignments; // If true -> move relation alignments to their target variables
    private final static Logger log = LogManager.getLogger(AMRReader.class);

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
	    final String regex = "\\(([a-z]+[0-9]*)\\s";
	    Pattern pattern = Pattern.compile(regex);

        String[] graphs_text = amrBank.split("\n\n");
        for(int i=0; i < graphs_text.length; ++i)
        {
        	log.info("Reading graph " + i);
	        try
	        {
		        String[] lines = graphs_text[i].split("\n");
		        if (lines.length > 0 && !Arrays.stream(lines).allMatch(l -> l.startsWith("#")))
		        {
		        	// Parse lines containing graph id, sequence of tokens and alignments
			        String id_line = Arrays.stream(lines)
					        .filter(l -> l.startsWith("# ::id"))
					        .findFirst()
					        .orElseThrow(() -> new Exception("Cannot find id line"));
			        String tokens_line = Arrays.stream(lines)
					        .filter(l -> l.startsWith("# ::tok"))
					        .findFirst()
					        .orElseThrow(() -> new Exception("Cannot find tokens line"));
			        String alignemnts_line = Arrays.stream(lines)
					        .filter(l -> l.startsWith("# ::alignments"))
					        .findFirst()
					        .orElseThrow(() -> new Exception("Cannot find alignments line"));

			        String id = readSentenceId(id_line).orElse(Integer.toString(i));
			        List<String> tokens = readTokens(tokens_line);
			        int start_amr = IntStream.range(0, lines.length)
					        .filter(line_num -> !lines[line_num].startsWith("#"))
					        .findFirst().orElseThrow(() -> new Exception("All lines start with '#'!?"));
			        String amr_text = Arrays.stream(lines, start_amr, lines.length)
					        .collect(joining("\n"));


			        // Find root variable and create graph object
			        Matcher matcher = pattern.matcher(lines[start_amr]);
			        if (!matcher.find())
			        	throw new Exception("Cannot find root in line " + lines[start_amr]);
			        String root = matcher.group(1);
			        SemanticGraph graph = new SemanticGraph(id, root);

			        // Parse the graph and populate graph object
			        AMRActions actions = new AMRActions(graph, this.keep_inverse_relations, this.keep_relation_alignments);
			        AMR.parse(amr_text, actions);
			        final Map<String, Integer> alignments = actions.getAlignments();
			        // There's at least three ways in which alignments can be encoded in AMR files :-/
			        if (actions.getAlignments().isEmpty())
			        {
				        Multimap<Integer, List<Integer>> amr_alignments = readAlignmentsFormat1(alignemnts_line);
				        if (amr_alignments.isEmpty())
					        amr_alignments = readAlignmentsFormat2(alignemnts_line);
				        if (amr_alignments.isEmpty())
				        	throw new Exception("Cannot read alignments");
				        List<String> vertex_order = actions.getVertexOrder();
				        amr_alignments.forEach((t, a) ->
					        gornAdressToVertex(graph, vertex_order, actions.getReentrantEdges(), a).ifPresent(v -> alignments.put(v, t)));
			        }
			        GraphAlignments graph_alignments = new GraphAlignments(graph, alignments, tokens);
			        graph.setAlignments(graph_alignments);
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

    private Multimap<Integer, List<Integer>> readAlignmentsFormat1(String line)
    {
        try
        {
	        final Multimap<Integer, List<Integer>> map = HashMultimap.create();
            Optional<String> align = getMatch("#\\s::alignments\\s([^:\\n]+).*", line);

            align.map(s -> s.split(" "))
                    .map(Arrays::asList)
                    .ifPresent(l -> l.stream()
                            .map(a -> a.split("-"))
                            .forEach(p ->
		                            {
			                            int token_index = Integer.parseInt(p[0]);
			                            List<Integer> address = Arrays.stream(p[1].split("\\."))
					                            .map(i -> i.equalsIgnoreCase("r") ? "0" : i) // replace 'r' with '0'
					                            .map(Integer::parseInt)
					                            .collect(toList());
			                            map.put(token_index, address);
		                            }));
            return map;
        }
        catch (Exception ignored) { return HashMultimap.create(); }
    }

	private Multimap<Integer, List<Integer>> readAlignmentsFormat2(String line)
	{
		try
		{
			final Multimap<Integer, List<Integer>> map = HashMultimap.create();
			Optional<String> align = getMatch("#\\s::alignments\\s([^:\\n]+).*", line);

			if (align.isPresent())
			{
				String[] items = align.get().split(" ");
				for (String item : items)
				{
					String[] parts = item.split("\\|");
					String[] token_str = parts[0].split("-");
					String[] addresses = parts[1].split("\\+");
					Pair<Integer, Integer> offsets = Pair.of(Integer.parseInt(token_str[0]), Integer.parseInt(token_str[1]));
					int num_tokens = offsets.getRight() - offsets.getLeft();
					if (num_tokens > 1)
					{
						if (addresses.length < num_tokens)
							throw new Exception("Cannot parse alignment " +  item);

						IntStream.range(0, num_tokens).forEach(i ->
						{
							String address_str = addresses[addresses.length - num_tokens + i];
							List<Integer> address = Arrays.stream(address_str.split("\\."))
									.map(j -> j.equalsIgnoreCase("r") ? "0" : j) // replace 'r' with '0'
									.map(Integer::parseInt)
									.collect(toList());
							map.put(offsets.getLeft() + i, address);
						});
					}
					else
					{
						Integer token_index = offsets.getLeft();
						Arrays.stream(addresses).forEach(address_str ->
						{
							List<Integer> address = Arrays.stream(address_str.split("\\."))
									.map(j -> j.equalsIgnoreCase("r") ? "0" : j) // replace 'r' with '0'
									.map(Integer::parseInt)
									.collect(toList());
							map.put(token_index, address);
						});
					}
				}
			}

			return map;
		}
		catch (Exception ignored) { return HashMultimap.create(); }
	}

    private static Optional<String> gornAdressToVertex(SemanticGraph graph, List<String> vertex_order,
                                                       Set<Role> reentrant_edges, List<Integer> address)
    {
	    try
	    {
		    String currentNode = graph.getRoot();
		    if (address.size() > 1)
		    {
			    for (int index : address.subList(1, address.size())) // ignore first index pointing to root
			    {
				    List<String> children = graph.outgoingEdgesOf(currentNode).stream()
						    .filter(e -> !e.toString().equals(AMRConstants.instance))
						    .filter(e -> !reentrant_edges.contains(e))
						    .map(graph::getEdgeTarget)
						    .sorted(Comparator.comparingInt(vertex_order::indexOf))
						    .collect(Collectors.toList());
				    currentNode = children.get(index);
			    }
		    }

		    return Optional.of(currentNode);
	    }
	    catch (Exception e)
	    {
	    	log.warn("Invalid alignment " + address);
	    	return Optional.empty();
	    }
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
