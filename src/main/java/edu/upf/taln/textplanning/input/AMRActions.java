package edu.upf.taln.textplanning.input;

import edu.upf.taln.textplanning.input.amr.Actions;
import edu.upf.taln.textplanning.input.amr.Label;
import edu.upf.taln.textplanning.input.amr.TreeNode;
import edu.upf.taln.textplanning.structures.Role;
import edu.upf.taln.textplanning.structures.amr.SemanticGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


public class AMRActions implements Actions
{
    private class  LabelNode extends TreeNode
    {
        final String label;
        LabelNode(String label) { this.label = label; }
    }

    private class ConceptNode extends TreeNode
    {
        final String label;
        final int index;
        ConceptNode(String label, int index) { this.label = label; this.index = index; }
    }

    private class AlignmentNode extends TreeNode
    {
        final int index;
        AlignmentNode(int index) { this.index = index; }
    }

    private class DescendentNode extends TreeNode
    {
        final String label;
        final String range;
        final boolean is_inverse;
        DescendentNode(String label, String range, boolean is_inverse)
        {
            this.label = label;
            this.range = range;
            this.is_inverse = is_inverse;
        }
    }

	private final boolean keep_inverse_relations; // AMR inverse (':*-of') relations to their non-inverted counterparts?
	private final boolean keep_relation_alignments;
	private final Map<String, Integer> alignments = new HashMap<>();
	private SemanticGraph graph;

	AMRActions(String graph_id, boolean keep_inverse_relations, boolean keep_relation_alignments)
	{
		graph = new SemanticGraph(graph_id);
		this.keep_inverse_relations = keep_inverse_relations;
		this.keep_relation_alignments = keep_relation_alignments;
	}

    public SemanticGraph getGraph() { return graph; }
	Map<String, Integer> getAlignments() { return alignments; }

    @Override
    public LabelNode make_ancestor(String input, int start, int end, List<TreeNode> elements)
    {
        // Create node for var
        TreeNode var_node = elements.get(2);
        LabelNode var = new LabelNode(var_node.text);
        graph.addVertex(var.label);

        // Add "instance" edge from var to concept
        ConceptNode concept = (ConceptNode)elements.get(6);
        try { graph.addEdge(var.label, concept.label, Role.create(AMRConstants.instance)); }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        // Store alignment of var
        if (concept.index != GraphAlignments.unaligned)
            alignments.put(var.label, concept.index);

        // Add edge indicated by rel
        TreeNode relations = elements.get(7);
        for (TreeNode descendent_node : relations.elements)
        {
            DescendentNode descendent = (DescendentNode)descendent_node.get(Label.desc);
            try
            {
                if (descendent.is_inverse && !keep_inverse_relations)
                    graph.addEdge(descendent.range, var.label, Role.create(descendent.label));
                else
                    graph.addEdge(var.label, descendent.range, Role.create(descendent.label));
            }
            catch (Exception e)
            {
                throw new RuntimeException("Cannot create edge: " + e);
            }
        }

        return var;
    }

    @Override
    public DescendentNode make_descendent(String input, int start, int end, List<TreeNode> elements)
    {
    	try
	    {
		    TreeNode relation_node = elements.get(0);

		    // Replace inverse relations by their counterparts
		    boolean is_inverse = relation_node.text.endsWith(AMRConstants.inverse_suffix);
		    String relation = relation_node.text;
		    if (is_inverse && !keep_inverse_relations)
			    relation = relation.substring(0, relation.indexOf(AMRConstants.inverse_suffix));

		    if (relation.equals(AMRConstants.mod))
		    {
			    is_inverse = true;
			    if (!keep_inverse_relations)
			        relation = AMRConstants.domain;
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

		    return new DescendentNode(relation, var.label, is_inverse);
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

		    int index = GraphAlignments.unaligned;
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
            int index = getMatch(pattern, label).map(Integer::parseInt).orElse(GraphAlignments.unaligned);
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
        return make_node(elements);
    }

    @Override
    public TreeNode make_num(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements);
    }

    @Override
    public TreeNode make_str(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements);
    }

    @Override
    public TreeNode make_var(String input, int start, int end, List<TreeNode> elements)
    {
        return make_node(elements);
    }

    private LabelNode make_node(List<TreeNode> elements)
    {
    	try
	    {
		    TreeNode node = elements.get(0);
		    graph.addVertex(node.text);

		    TreeNode alignment = elements.get(1);
		    if (alignment instanceof AlignmentNode)
		    {
			    alignments.put(node.text, ((AlignmentNode) alignment).index);
		    }

		    return new LabelNode(node.text);
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
