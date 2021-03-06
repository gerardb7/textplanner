package edu.upf.taln.textplanning.core;

import edu.upf.taln.textplanning.core.utils.DebugUtils;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Set;

public class Options
{
	public Set<String> excluded_POS_Tags = new HashSet<>(); // candidates meanings for words with these POS aren't ranked
	public int min_context_freq = 3; // Minimum frequency of document tokens used to calculate context vectors
	public double context_threshold = 0.8;
	public int num_first_meanings = 1;
	public double sim_threshold = 0.6; // Pairs of meanings with sim below this value have their score set to 0
	public double damping_meanings = 0.6; // controls bias towards weighting function when ranking meanings
	public double damping_variables = 0.2; // controls bias towards meanings rank when ranking variables
	public int num_subgraphs = 1000; // Number of subgraphs to extract
	public double extraction_lambda = 1.0; // Controls balance between weight of nodes and cost of edges during subgraph extraction
	public double tree_edit_lambda = 0.1; // Controls impact of roles when calculating similarity between semantic trees

	@Override
	public String toString()
	{
		NumberFormat f = NumberFormat.getInstance();
		f.setRoundingMode(RoundingMode.UP);
		//f.setMaximumFractionDigits(10);
		f.setMinimumFractionDigits(3);
		return  "Options:" +
				"\n\texcluded_POS_Tags = " + excluded_POS_Tags +
				"\n\tmin_context_freq = " + min_context_freq +
				"\n\tcontext_threshold = " + context_threshold +
				"\n\tnum_first_meanings = " + num_first_meanings +
				"\n\tsim_threshold = " + f.format(sim_threshold) +
				"\n\tdamping_meanings = " + f.format(damping_meanings) +
				"\n\tdamping_variables = " + f.format(damping_variables) +
				"\n\tnum_subgraphs = " + num_subgraphs +
				"\n\textraction_lambda = " + f.format(extraction_lambda) +
				"\n\tredundancy lambda = " + f.format(tree_edit_lambda);
	}

	public String toShortString()
	{
		return  "test.cf" + min_context_freq +
				".ct" + DebugUtils.printDouble(context_threshold) +
				".nf" + num_first_meanings +
				".st" + DebugUtils.printDouble(sim_threshold) +
				".dm" + DebugUtils.printDouble(damping_meanings) +
				".dv" + DebugUtils.printDouble(damping_variables) +
				".ns" + num_subgraphs +
				".el" + DebugUtils.printDouble(extraction_lambda) +
				".rl" + DebugUtils.printDouble(tree_edit_lambda);
	}
}
