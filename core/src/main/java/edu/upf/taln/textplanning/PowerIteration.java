package edu.upf.taln.textplanning;

import Jama.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

/**
 * Implementation of a power iteration algorithm for Markov chains.
 */
public class PowerIteration
{
	private final static Logger log = LoggerFactory.getLogger(PowerIteration.class);


	/**
	 * Power iteration method to obtain a final stationary distribution of a Markov chain
	 *
	 * @param inInitialDistribution initial state of the chain
	 * @param inTransitionMatrix stochastic matrix of a Markov chain
	 * @return a stationary distribution of the chain
	 */
	public static Matrix run(Matrix inInitialDistribution, Matrix inTransitionMatrix, double inStopThreshold)
	{
		log.info("Starting power iteration");
		int numIterations = 0;
		Matrix oldDistribution = inInitialDistribution;
		Matrix newDistribution;
		double delta;
		do
		{
			// Core operation: transform distribution according to stochastic matrix
			newDistribution = oldDistribution.times(inTransitionMatrix);

			// Find out magnitude of change in distribution vector (delta)
			Matrix difference = newDistribution.minus(oldDistribution);
			OptionalDouble optDelta = DoubleStream.of(difference.getColumnPackedCopy())
					.map(Math::abs)
					.max();
			delta = (optDelta.isPresent() ? optDelta.getAsDouble() : 0);
			oldDistribution = newDistribution;
			if (++numIterations % 100 == 0)
			{
				log.info("..." + numIterations + " iterations");
			}
		}
		while (delta >= inStopThreshold); // stopping criterion: delta falls below a certain threshold

		log.info("Power iteration completed after " + numIterations + " iterations");
		return newDistribution;
	}
}
