package org.mitre.bch.cath.simulation.distributions;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.mitre.bch.cath.simulation.utils.RandomNumberGenerator;

/** Beta distribution
 * Implements org.apache.commons.math3.distribution.BetaDistribution
 * @author Bennett Miller, The MITRE Corporation
 */
public class Beta extends BetaDistribution implements Distribution {

    /** Alpha parameter */
    protected double alpha;
    /** Beta parameter */
    protected double beta;
    /** Location parameter, min value */
    protected double loc;
    /** Shape parameter, equal to max value minus min value */
    protected double shape;

    /** Constructs a Beta distribution.
     *
     * Ex: alpha=1.5, beta=1.8, loc=450.0, shape=90.0
     * @throws IllegalArgumentException if alpha <= 0.0 || beta <= 0.0
     */
    public Beta(double alpha, double beta, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, alpha, beta);
        setState(alpha, beta, 0.0, 1.0);
    }
    public Beta(double alpha, double beta, double loc, double shape, RandomNumberGenerator randomGenerator) {
        super(randomGenerator, alpha, beta);
        setState(alpha, beta, loc, shape);
    }

    /** Cumulative distribution function.
     *
     * @param x the point at which the CDF is evaluated
     * @return the probability that a random variable with this distribution takes a value less than or equal to x
     */
    public double cdf(double x) {
        return super.cumulativeProbability(x);
    }

    /**
     * Returns a random number from the distribution.
     *
     * @return random value sampled from the Beta(alpha, beta, loc, shape) distribution
     */
    public double nextDouble() {
        return loc + shape * super.sample();
    }

    /** Return the mean from the distribution.
     *
     * @return distribution mean
     */
    public double getMean() {
        return loc + shape * super.getNumericalMean();
    }

    /** Set the alpha, beta, location, and shape parameters.
     *
     * @param alpha alpha parameter
     * @param beta beta parameter
     * @param loc location (min of range) parameter
     * @param shape shape (range of interval, max - min) parameter
     * @throws IllegalArgumentException if alpha <= 0.0 || beta <= 0.0.
     */
    private void setState(double alpha, double beta, double loc, double shape) {
        if (alpha <= 0.0) throw new IllegalArgumentException();
        if (beta <= 0.0) throw new IllegalArgumentException();
        this.alpha = alpha;
        this.beta = beta;
        this.loc = loc;
        this.shape = shape;
    }

    /** Return a String representation of the receiver.
     */
    public String toString() {
        return this.getClass().getName()+"("+alpha+","+beta+","+loc+","+shape+")";
    }
}