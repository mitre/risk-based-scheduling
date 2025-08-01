package org.mitre.bch.cath.simulation.distributions;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.mitre.bch.cath.simulation.utils.RandomNumberGenerator;

/**
 * Gamma distribution
 * Implements org.apache.commons.math3.distribution.GammaDistribution
 * @author H. Haven Liu, The MITRE Corporation
 */
public class Gamma extends GammaDistribution implements Distribution {

    protected double alpha;
    protected double lambda;
    protected double location;


/**
 * Constructs a Gamma distribution.
 * Example: alpha=1.0, lambda=1.0.
 * @throws IllegalArgumentException if alpha &lt;= 0.0 || lambda &gt;= 0.0.
 */
    public Gamma(double alpha, double lambda, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, alpha, 1/lambda);
        setState(alpha,lambda);
    }

    public Gamma(double alpha, double lambda, double location, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, alpha, 1/lambda);
        setState(alpha,lambda,location);
    }
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
     * @return random value sampled from the Gamma(shape, scale) distribution
     */
    public double nextDouble() {
        return location + super.sample();
        }

    /** Return mean from the distribution
     *
     * @return distribution mean
     */
    @Override
    public double getMean() {
        return location + super.getNumericalMean();
    }

    /**
     * Sets the mean and variance.
     * @param alpha alpha parameter
     * @param lambda lambda parameter
     * @throws IllegalArgumentException if alpha <= 0.0 || lambda >= 0.0.
     */
    private void setState(double alpha, double lambda) {
        if (alpha <= 0.0) throw new IllegalArgumentException();
        if (lambda <= 0.0) throw new IllegalArgumentException();
        this.alpha = alpha;
        this.lambda = lambda;
        this.location = 0;
        }
    /**
     * Sets the mean and variance.
     * @param alpha alpha parameter
     * @param lambda lambda parameter
     * @param location location (offset) parameter
     * @throws IllegalArgumentException if alpha <= 0.0 || lambda >= 0.0.
     */
    private void setState(double alpha, double lambda, double location) {
        if (alpha <= 0.0) throw new IllegalArgumentException();
        if (lambda <= 0.0) throw new IllegalArgumentException();
//        if (location < 0.0) throw new IllegalArgumentException();
        this.alpha = alpha;
        this.lambda = lambda;
        this.location = location;
    }
    /**
     * Returns a String representation of the receiver.
     */
    public String toString() {
        return this.getClass().getName()+"("+alpha+","+lambda+","+location+")";
        }
    }
