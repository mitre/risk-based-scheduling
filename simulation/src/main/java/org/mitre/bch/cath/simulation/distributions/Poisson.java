package org.mitre.bch.cath.simulation.distributions;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.mitre.bch.cath.simulation.utils.RandomNumberGenerator;

/**
 * Poisson distribution
 * Implements org.apache.commons.math3.distribution.PoissonDistribution
 * @author H. Haven Liu, The MITRE Corporation
 */

public class Poisson extends PoissonDistribution implements Distribution {

    protected double p;


    /**
     * Constructs a Gamma distribution.
     * Example: p=1.0.
     * @throws IllegalArgumentException p &gt;= 0.0.
     */
    public Poisson(double p, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, p, PoissonDistribution.DEFAULT_EPSILON, PoissonDistribution.DEFAULT_MAX_ITERATIONS);
        setState(p);
    }
    /**
     * Cumulative distribution function.
     *
     * @param x the point at which the CDF is evaluated
     * @return the probability that a random variable with this distribution takes a value less than or equal to x
     */


    public double cdf(double x) {
        return super.cumulativeProbability((int) x);
    }

    /**
     * Returns a random number from the distribution.
     * @return random value sampled from the Poisson(p) distribution
     */
    public double nextDouble() {
        return super.sample();
    }

    /** Return mean from the distribution
     *
     * @return distribution mean
     */
    @Override
    public double getMean() {
        return super.getNumericalMean();
    }

    /**
     * Sets the mean.
     * @param p p parameter
     * @throws IllegalArgumentException if p <= 0.0.
     */
    private void setState(double p) {
        if (p <= 0.0) throw new IllegalArgumentException();
        this.p = p;
    }

    /**
     * Returns a String representation of the receiver.
     */
    public String toString() {
        return this.getClass().getName()+"("+p+")";
    }

}
