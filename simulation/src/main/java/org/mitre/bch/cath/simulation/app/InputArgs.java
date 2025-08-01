package org.mitre.bch.cath.simulation.app;
import com.google.gson.JsonObject;


public class InputArgs {

    public final int iterations;
    public final String sched;
    public final int seed;
    public final String folderName;
    public final int extraDays;
    public final String addonBucket;
    public final String expName;
    public final String description;
    public final JsonObject configData;
    public final boolean verbose;

    /** Constructor for InputArgs instance to run simulation from the frontend
     * @param iterations number of iterations to run the sim
     * @param sched the schedule to simulate
     * @param seed start seed
     * @param folderName name for log folder
     * @param extraDays number of days past scheduling window end date to run simulation
     * @param addonBucket file with add-on cases
     * @param expName MLFlow experiment name
     * @param iterations description of experiment
     * @param iterations JSON data with config.json contents
     * @param verbose whether or not to run with verbose logging
     */
    public InputArgs(int iterations, String sched, int seed, String folderName, int extraDays, String addonBucket, String expName, String description, JsonObject configData, boolean verbose) {
        this.iterations = iterations;
        this.sched = sched;
        this.seed = seed;
        this.folderName = folderName;
        this.extraDays = extraDays;
        this.addonBucket = addonBucket;
        this.expName = expName;
        this.description = description;
        this.configData = configData;
        this.verbose = verbose;
    }
}
