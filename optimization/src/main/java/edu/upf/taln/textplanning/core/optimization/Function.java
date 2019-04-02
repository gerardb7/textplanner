package edu.upf.taln.textplanning.core.optimization;

/**
 * Created by gerard on 13/07/17.
 */
public interface Function
{
	double getValue(double[] dist);
	void getValueGradient(double[] params, double[] gradient);
}
