package org.mitre.bch.cath.simulation.distributions;


import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.mitre.bch.cath.simulation.utils.RandomNumberGenerator;

/**
 * The Lognormal class uses a Normal distribution object to simulate
 * a lognormally distributed random variable. It therefore requires
 * parametes mu and sigma, which define the mean and standard deviation
 * of the underlying Normal distribution. That is X~N(mu, sigma) and 
 * Y ~ exp(X).
 *  Implements org.apache.commons.math3.distribution.LogNormalDistribution
 * 
 * @author H. Haven Liu, James R. Thompson, The MITRE Corporation
 * @version 05/03/2021*/
public class Lognormal extends LogNormalDistribution implements Distribution {
	
	//============================================= FIELDS ==========================================================//
	
	/**The mean of the corresponding Normal random variable.*/
	protected double mu;
	
	/**The standard deviation of corresponding Normal random variable.*/
	protected double sigma;

	//=========================================== CONSTRUCTORS ======================================================//
	
	/**The constructor for a lognormal in terms of the underling Normal parameters. The mean and
	 * standard deviation should describe the underlying Normal random variable.*/
	public Lognormal(double mu, double sigma, RandomNumberGenerator randomGenerator) {
		super(randomGenerator, mu, sigma);
		setState(mu, sigma);
	}//end constructor


	//============================================= METHODS =========================================================//

	/**
	 * Cumulative distribution function.
	 *
	 * @param x the point at which the CDF is evaluated
	 * @return the probability that a random variable with this distribution takes a value less than or equal to x
	 */
	public double cdf(double x) {
		return super.cumulativeProbability(x);
	}


	/**
	 * Returns a random number from the distribution.
	 * @return random value sampled from the LogNormal(mu, sigma) distribution
	 */
	public double nextDouble() {
		return super.sample();
	}

	/** Return mean from the distribution
	 *
	 * @return distribution mean
	 */
	public double getMean() {
		return super.getNumericalMean();
	}

	/**
	 * Set the mean and variance.
	 * @param mu mean of the distribution
	 * @param sigma standard deviation of the distribution
	 */
	private void setState(double mu, double sigma) {
		this.mu = mu;
		this.sigma = sigma;
	}

	/**
	 * Returns a String representation of the receiver.
	 */
	public String toString() {
		return this.getClass().getName()+"("+mu+","+sigma+")";
	}
}

