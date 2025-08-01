package org.mitre.bch.cath.simulation.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.mitre.bch.cath.simulation.distributions.*;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.stat.distribution.GammaDistribution;
import smile.stat.distribution.LogNormalDistribution;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mitre.bch.cath.simulation.utils.FileHandler.fileToString;

/** Cath Distribution class
 * Stores the various probability distributions used in the simulation
 *
 * @author H. Haven Liu, The MITRE Corporation
 */
public final class CathDistribution {
    /** Static Logger object */
    private static final Logger SLOGGER = LoggerFactory.getLogger(CathDistribution.class);
    //===== Attributes ======//
    /** Distribution for probability of AE */
    public ThreeWayMap<Integer, Integer, String, Float> pAE; // function of procedure, adverseScore, step
    /** Probability distribution for ICU admission */
    public Distribution pICU;
    /** Distribution of Turnover time */
    public Distribution tTurnover;
    /** Probability distributions for case time */
    public TwoWayMap<Integer, String, Distribution> tCase; // function of durationScore, step
    /** Distribution for Rescue time */
    public Distribution tRescue;
    /** Distribution for Addon count time */
    public Map<Integer, Distribution> cAddon; // function of Weekday
    /** Distribution for start time */
    public Map<Integer, Distribution> tStart; // function of Lab
    /** Distribution for addon observed time */
    public Distribution tAddonObserved;
    /** Distribution for random addon selection */
    public Distribution nAddon;
    /** Random number generator seed */
    public int seed;
    /** Random number generator for urgency logic*/
    public RandomNumberGenerator UrgencyRandom;
    /** Random number generator for AE logic*/
    public RandomNumberGenerator AERandom;

    public List<Config.Schedule> addonBucket;
    /** Random number generator for ICU logic */
    public RandomNumberGenerator ICURandom;

    private static final Gson gson = new Gson();

    //===== Constructors ======//

    /**
     * Constructor for CathDistribution using a random number generator seed
     * @param seed random number generator seed
     */
    public CathDistribution(int seed, List<Config.Schedule> addonBucket) {
        this.seed = seed;
        this.UrgencyRandom = new RandomNumberGenerator(seed);
        this.AERandom = new RandomNumberGenerator(seed);

        this.addonBucket = addonBucket;
        this.ICURandom = new RandomNumberGenerator(seed);
    }
    //===== Methods =====//

    /**
     * Read probability distribution config files to populate the Distribution objects
     */
    public void populateDistributions() {
        try {
            /* prob_ae */
            JsonArray pae = gson.fromJson(fileToString("prob_dist/p_ae.json"), JsonArray.class);

            pAE = new ThreeWayMap<>();
            for (JsonElement p: pae) {
                int proc = p.getAsJsonObject().get("procedure").getAsInt();
                int adverseScore = p.getAsJsonObject().get("adverseScore").getAsInt();
                String step = p.getAsJsonObject().get("step").getAsString();
                float prob = p.getAsJsonObject().get("prob").getAsFloat();
                pAE.set(proc, adverseScore, step, prob);
            }

            /* time_case */
            DistributionParameters[] tCaseJson = gson.fromJson(fileToString("prob_dist/time_case.json"), DistributionParameters[].class);
            tCase = new TwoWayMap<>();
            RandomNumberGenerator tCaseRandom = new RandomNumberGenerator(seed);
            for (DistributionParameters p: tCaseJson) {
                int durationScore = ((Double) p.segment.get("durationScore")).intValue();
                String step = (String) p.segment.get("step");
                Distribution dist = createDistribution(p.distribution, p.params, tCaseRandom);
                tCase.set(durationScore, step, dist);
            }

            /* time_start */
            DistributionParameters[] tStartJson = gson.fromJson(fileToString("prob_dist/time_start.json"), DistributionParameters[].class);
            tStart = new HashMap<>();
            RandomNumberGenerator tStartRandom = new RandomNumberGenerator(seed);
            for (DistributionParameters p: tStartJson) {
                int lab = ((Double) p.segment.get("lab")).intValue();
                Distribution dist = createDistribution(p.distribution, p.params, tStartRandom);
                tStart.put(lab, dist);
            }

            /* time_turnover */
            DistributionParameters tTurnoverJson = gson.fromJson(fileToString("prob_dist/time_turnover.json"), DistributionParameters[].class)[0];
            RandomNumberGenerator tTurnoverRandom = new RandomNumberGenerator(seed);
            tTurnover = createDistribution(tTurnoverJson.distribution, tTurnoverJson.params, tTurnoverRandom);

            /* prob_icu */
            RandomNumberGenerator pICURandom = new RandomNumberGenerator(seed);
            pICU = new Uniform(pICURandom);

            /* time_rescue */
            RandomNumberGenerator tRescueRandom = new RandomNumberGenerator(seed);
            tRescue = new Uniform(60,120,tRescueRandom);

            /* add on first observed time */
            RandomNumberGenerator tAddonObservedRandom = new RandomNumberGenerator(seed);
            tAddonObserved = new Uniform(0, CathSchedule.MIN_PER_DAY, tAddonObservedRandom);

            /* addon_count */
            DistributionParameters[] cAddonJson = gson.fromJson(fileToString("prob_dist/addon.json"), DistributionParameters[].class);
            cAddon = new HashMap<>();
            RandomNumberGenerator cAddonRandom = new RandomNumberGenerator(seed);
            for (DistributionParameters p: cAddonJson) {
                int weekday = ((Double) p.segment.get("weekday")).intValue();
                Distribution dist = createDistribution(p.distribution, p.params, cAddonRandom);
                cAddon.put(weekday, dist);
            }

            /* addon from bucket */
            int bucketSize = this.addonBucket.size();
            RandomNumberGenerator nAddonRandom = new RandomNumberGenerator(seed);
            nAddon = new Uniform(0,bucketSize,nAddonRandom);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * The createDistribution method takes a string array of the form [Distribution name, param1, param2, ...]
     * and creates the appropriate distribution and returns it.
     * @param name distribution name
     * @param params distribution parameters
     * @param random appropriate RandomNumberGenerator
     * @return Distribution dist
     */
    private Distribution createDistribution(String name, Map<String, Float> params, RandomNumberGenerator random) {
        Distribution dist;
        switch(name) {
            case "Gamma":
                if (params.containsKey("param3")) {
                    dist = new Gamma(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), Double.valueOf(params.get("param3")), random);
                } else {
                    dist = new Gamma(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), random);
                }
                break;
            case "Lognormal":
                dist = new Lognormal(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), random);
                break;
            case "Poisson":
                dist = new Poisson(Double.valueOf(params.get("param1")), random);
                break;
            case "Beta":
                if (params.containsKey("param3") && params.containsKey("param4")) {
                    dist = new Beta(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), Double.valueOf(params.get("param3")), Double.valueOf(params.get("param4")), random);
                } else {
                    dist = new Beta(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), random);
                }
                break;
            case "Normal":
                dist = new Normal(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), random);
                break;
            case "Uniform":
                if (params.containsKey("param1") && params.containsKey("param2")) {
                    dist = new Uniform(Double.valueOf(params.get("param1")), Double.valueOf(params.get("param2")), random);
                } else {
                    dist = new Uniform(random);
                }
                break;
            default:
                dist = new Uniform(random);
        }//end switch

        return dist;

    }//end createDistribution

    /**
     * Fit data to distribution
     * @param name the name of distribution to fit
     * @param data array of data used to fit distribution
     * @return distribution parameters
     */
    private Map<String, Double> fitDistribution(String name, double[] data) {
        Map<String, Double> params = new HashMap<>();
        switch (name) {
            case "Gamma" -> {
                GammaDistribution dist = GammaDistribution.fit(data);
                params.put("shape", dist.k);
                params.put("scale", dist.theta);
                params.put("lambda", 1/dist.theta);
            }
            case "Lognormal" -> {
                LogNormalDistribution dist = LogNormalDistribution.fit(data);
                params.put("mean", dist.mu);
                params.put("stdev", dist.sigma);
            }
        }
        return params;
    }

    //===== Getters/Setters ======//

    private static class DistributionParameters {
        public String distribution;
        public Map<String, Float> params;
        public Map<String, ?> segment;
    }
}
