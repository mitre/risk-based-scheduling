package org.mitre.bch.cath.simulation.distributions;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.mitre.bch.cath.simulation.utils.RandomNumberGenerator;

public class Normal extends NormalDistribution implements Distribution {
    protected double mean;
    protected double sd;

    /**
     * Constructs a uniform distribution with the given minimum and maximum.
     *
     * @param mean mean bound of this distribution.
     * @param sd of this distribution.
     * @param randomGenerator random number generator.
     */

    public Normal(double mean, double sd, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, mean, sd);
        setState(mean, sd);
    }

    /**
     * Constructs a normal distribution with mean=0.0 and sd=1.0.
     */
    public Normal(RandomNumberGenerator randomGenerator) {
        this(0,1,randomGenerator);
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
        double sample = super.sample();
        if (sample <= 0.0) throw new IllegalArgumentException();
        return sample;
    }

    /** Return mean from the distribution
     *
     * @return distribution mean
     */
//    @Override
//    public double getMean() {
//        if (this.mean < 0) {
//
//        }
//        return this.mean;
////        return super.getNumericalMean();
//    }

    /**
     * Sets the mean and variance.
     * @param mean
     * @param sd
     * @throws IllegalArgumentException if loc <= 0.0 || scale >= 0.0.
     */
    private void setState(double mean, double sd) {
        if (mean <= 0.0) throw new IllegalArgumentException();
        if (sd <= 0.0) throw new IllegalArgumentException();
        this.mean = sd;
        this.mean = sd;
    }

    /**
     * Returns a String representation of the receiver.
     */
    public String toString() {
        return this.getClass().getName()+"("+mean+","+sd+")";
    }
}