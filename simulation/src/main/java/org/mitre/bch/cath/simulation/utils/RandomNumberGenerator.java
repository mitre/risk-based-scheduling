/* Random number generator wrapper class that
 * Extends ec.utils.MersenneTwisterFast
 * Implements org.apache.commons.math3.random.RandomGenerator
 * @author: H. Haven Liu, THe MITRE Corporation
 */

package org.mitre.bch.cath.simulation.utils;

import ec.util.MersenneTwisterFast;
import org.apache.commons.math3.random.RandomGenerator;

public class RandomNumberGenerator extends MersenneTwisterFast implements RandomGenerator {

    /** random number generator seed */
    private int seed;
    /** Construct a random number generator wrapper
     *
     * @param seed RNG seed
     */
    public RandomNumberGenerator(int seed) {
        super(seed);
        this.seed = seed;
    }
    /** Construct a random number generator wrapper
     *
     * @param seed RNG seed
     */
    public RandomNumberGenerator(long seed) {
        super(seed);
        this.seed = (int) seed;
    }

    /** Set seed for the RNG
     *
     * @param seed the seed value
     */
    @Override
    public void setSeed(int seed) {
        super.setSeed(seed);
        this.seed = seed;
    }

    public int getSeed() {
        return this.seed;
    }
}
