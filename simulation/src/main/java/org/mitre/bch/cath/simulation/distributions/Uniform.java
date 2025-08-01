/*
  Copyright � 1999 CERN - European Organization for Nuclear Research.
  Permission to use, copy, modify, distribute and sell this software and its documentation for any purpose
  is hereby granted without fee, provided that the above copyright notice appear in all copies and
  that both that copyright notice and this permission notice appear in supporting documentation.
  CERN makes no representations about the suitability of this software for any purpose.
  It is provided "as is" without expressed or implied warranty.
*/
package org.mitre.bch.cath.simulation.distributions;
import ec.util.MersenneTwisterFast;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.mitre.bch.cath.simulation.utils.RandomNumberGenerator;
import sim.util.distribution.Probability;

/**
 * Uniform distribution
 * Implements org.apache.commons.math3.distribution.UniformRealDistribution
 * @author H. Haven Liu, THe MITRE COrporation
 */

public class Uniform extends UniformRealDistribution implements Distribution {
    private static final long serialVersionUID = 1;

    protected double min;
    protected double max;

    /**
     * Constructs a uniform distribution with the given minimum and maximum.
     *
     * @param min Lower bound of this distribution (inclusive).
     * @param max Upper bound of this distribution (exclusive).
     * @param randomGenerator random number geenrator
     */
    public Uniform(double min, double max, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, min, max);
        setState(min,max);
    }
    /**
     * Constructs a uniform distribution with min=0.0 and max=1.0.
     */
    public Uniform(RandomNumberGenerator randomGenerator) {
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
     * @return a random value
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
     * Sets the internal state.
     *
     * @param min Lower bound of this distribution (inclusive).
     * @param max Upper bound of this distribution (exclusive).
     */
    public void setState(double min, double max) {
        if (max<min) { setState(max,min); return; }
        this.min=min;
        this.max=max;
    }
    /**
     * Returns a String representation of the receiver.
     */
    public String toString() {
        return this.getClass().getName()+"("+min+","+max+")";
    }
}
