package edu.upf.taln.textplanning.input.amr;

import java.util.List;

public interface Actions {
    TreeNode make_alignment(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_ancestor(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_concept(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_constant(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_descendent(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_num(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_str(String input, int start, int end, List<TreeNode> elements);
    TreeNode make_var(String input, int start, int end, List<TreeNode> elements);
}
