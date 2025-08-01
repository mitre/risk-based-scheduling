package org.mitre.bch.cath.simulation.utils;

/** Custom exception for issues found when verifying simulation results. */
public class VerificationException extends RuntimeException {
    public VerificationException(String message) {
        super(message);
    }
}
