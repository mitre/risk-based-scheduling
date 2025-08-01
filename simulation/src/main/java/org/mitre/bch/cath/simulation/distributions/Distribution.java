/**
 * Wrapper class that facilitates calls to double cdf(), double nextDouble(), and double getMean()
 *
 * @author James R. Thompson, H. Haven Liu, THe MITRE Corporation
 * @version 05/03/2021
 *
 */

package org.mitre.bch.cath.simulation.distributions;

public interface Distribution {
    public double cdf(double x);

    public double nextDouble();

    public double getMean();

}