package edu.upf.taln.textplanning.amr.io;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import edu.upf.taln.textplanning.amr.structures.AMRSemantics;
import edu.upf.taln.textplanning.amr.structures.SemanticGraph;
import edu.upf.taln.textplanning.amr.io.parse.Actions;
import edu.upf.taln.textplanning.amr.io.parse.Label;
import edu.upf.taln.textplanning.amr.io.parse.TreeNode;
import edu.upf.taln.textplanning.core.structures.Role;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class AMRActions implements Actions
{
    public class  LabelNode extends TreeNode
    {
        final String label;
	    final boolean is_reentrant;
	    final List<String> vertex_order;
	    LabelNode(String label, boolean is_reentrant, List<String> order)
	    {
	    	this.label = label;
		    this.is_reentrant = is_reentrant;
		    this.vertex_order = order;
	    }
    }

    private class ConceptNode extends TreeNode
    {
        final String label;
        final int index;

        ConceptNode(String label, int index)
        {
        	this.label = label;
        	this.index = index;
        }
    }

    private class AlignmentNode extends TreeNode
    {
        final int index;
        AlignmentNode(int index) { this.index = index; }
    }

    private class DescendentNode extends TreeNode
    {
        final String label;
        final LabelNode range;
        final boolean is_inverse;
        DescendentNode(String label, LabelNode range, boolean is_inverse)
        {
            this.label = label;
            this.range = range;
            this.is_inverse = is_inverse;
        }
    }

	public static final int unaligned = -1;
	private final SemanticGraph graph;
	private final boolean keep_inverse_relations; // AMR inverse (':*-of') relations to their non-inverted counterparts?
	private final boolean keep_relation_alignments;
	private final Multimap<String, Integer> alignments = HashMultimap.create();
	private final Set<Role> reentrant_edges = new HashSet<>();

	AMRActions(SemanticGraph graph, boolean keep_inverse_relations, boolean keep_relation_alignments)
	{
		this.graph = graph;
		this.keep_inverse_relations = keep_inverse_relations;
		this.keep_relation_alignments = keep_relation_alignments;
	}

	Multimap<String, Integer> getAlignments() { return alignments; }
	Set<Role> getReentrantEdges() { return reentrant_edges; }

    @Override
    public LabelNode make_ancestor(String input, int start, int end, List<TreeNode> elements)
    {
        // Create node for var
        TreeNode var_node = elements.get(2);
        String label = var_node.text;
        boolean reentrant = graph.containsVertex(label);
        graph.addVertex(label);

        // Add "instance" edge from var to concept
        ConceptNode concept = (ConceptNode)elements.get(6);
        try { graph.addEdge(label, concept.label, Role.create(AMRSemantics.instance)); }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot create edge: " + e);
        }

        // Store alignment of var
        if (concept.index != unaligned)
            alignments.put(label, concept.index);

        // Prepare updated sequence of vertices
	    List<String> order = new ArrayList<>();
	    order.add(label);
	    order.add(concept.label);

        // Add edge indicated by rel
        TreeNode relations = elements.get(7);
        for (TreeNode descendent_node : relations.elements)
        {
            DescendentNode descendent = (DescendentNode)descendent_node.get(Label.desc);
            order.addAll(descendent.range.vertex_order);
            try
            {
	            Role role = Role.create(descendent.label);
	            if (descendent.is_inverse && !keep_inverse_relations)
                    graph.addEdge(descendent.range.label, label, role);
                else
                    graph.addEdge(label, descendent.range.label, role);
                if (descendent.range.is_reentrant)
                	reentrant_edges.add(role);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot create edge: " + e);
            }
        }

	    return new LabelNode(label, reentrant, order);
    }

    @Override
    public DescendentNode make_descendent(String input, int start, int end, List<TreeNode> elements)
    {
    	try
	    {
		    TreeNode relation_node = elements.get(0);

		    // Replace inverse relations by their counterparts
		    boolean is_inverse = relation_node.text.endsWith(AMRSemantics.inverse_suffix);
		    String relation = relation_node.text;
		    if (is_inverse && !keep_inverse_relations)
			    relation = relation.substring(0, relation.indexOf(AMRSemantics.inverse_suffix));

		    if (relation.equals(AMRSemantics.mod))
		    {
			    is_inverse = true;
			    if (!keep_inverse_relations)
			        relation = AMRSemantics.domain;
		    }

		    TreeNode alignment = elements.get(1);
		    LabelNode var = (LabelNode) elements.get(3);

		    if (alignment instanceof AlignmentNode)
		    {
			    AlignmentNode a = (AlignmentNode) alignment;
			    if (keep_relation_alignments)
			        alignments.put(relation, a.index);
		    	else if (!alignments.containsKey(var.label))
		    		alignments.put(var.label, a.index);
		    }

		    return new DescendentNode(relation, var, is_inverse);
	    }
	    catch (Exception e)
	    {
		    throw new RuntimeException("Cannot parse descendent " + elements + ": " + e);
	    }
    }

    @Override
    public TreeNode make_concept(String input, int start, int end, List<TreeNode> elements)
    {
	    try
	    {
		    TreeNode node = elements.get(0);
		    graph.addVertex(node.text);

		    int index = unaligned;
		    TreeNode alignment = elements.get(1);
		    if (alignment instanceof AlignmentNode)
		    {
			    index = ((AlignmentNode) alignment).index;
		    }

		    return new ConceptNode(node.text, index);
	    }
	    catch (Exception e)
	    {
		    throw new RuntimeException("Cannot parse concept " + elements + ": " + e);
	    }
    }

    @Override
    public TreeNode make_alignment(String input, int start, int end, List<TreeNode> elements)
    {
        String pattern = "~e\\.([0-9]+)";
        String label = input.substring(start, end);
        try
        {
            int index = getMatch(pattern, label).map(Integer::parseInt).orElse(unaligned);
            return new AlignmentNode(index);
        }
        catch (Exception e)
        {
           throw new RuntimeException("Cannot parse alignment " + label + ": " + e);
        }
    }

    @Override
    public TreeNode make_constant(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements, false);
    }

    @Override
    public TreeNode make_num(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements, false);
    }

    @Override
    public TreeNode make_str(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements, false);
    }

    @Override
    public TreeNode make_var(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements, true);
    }

    private LabelNode make_node(List<TreeNode> elements, boolean is_var)
    {
    	try
	    {
		    TreeNode node = elements.get(0);
		    boolean reentrant = is_var && graph.containsVertex(node.text);
		    graph.addVertex(node.text);

		    TreeNode alignment = elements.get(1);
		    if (alignment instanceof AlignmentNode)
		    {
			    alignments.put(node.text, ((AlignmentNode) alignment).index);
		    }

		    return new LabelNode(node.text, reentrant, Collections.singletonList(node.text));
	    }
	    catch (Exception e)
	    {
		    throw new RuntimeException("Cannot parse node " + elements + ": " + e);
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