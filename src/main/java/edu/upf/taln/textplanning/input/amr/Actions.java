package edu.upf.taln.textplanning.input.amr;

import java.util.List;

public interface Actions {
    public TreeNode make_alignment(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_ancestor(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_concept(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_constant(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_descendent(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_num(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_str(String input, int start, int end, List<TreeNode> elements);
    public TreeNode make_var(String input, int start, int end, List<TreeNode> elements);
}
