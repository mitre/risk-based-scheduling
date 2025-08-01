package org.mitre.bch.cath.simulation.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jfree.data.xy.XYSeries;
import org.mitre.bch.cath.simulation.entity.Lab;
import org.mitre.bch.cath.simulation.entity.Patient;
import org.mitre.bch.cath.simulation.entity.Procedure;
import org.mitre.bch.cath.simulation.entity.Resource;
import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.mlflow.api.proto.Service;
import org.mlflow.tracking.MlflowClient;
import org.mlflow.api.proto.Service.*;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.*;

/** Metrics class
 * Records various metrics from the simulation
 */
public class Metrics implements Serializable {

    //===== Attributes ======//
    private static final long serialVersionUID = 1L;
    /** # of patients */
    private final XYSeries patients = new XYSeries("Patient Count", true, false);

    /** # of each resource */
    private final Map<String, XYSeries> resources = new HashMap<>();

    /** time + resource + patient + count when resource is not available */
    private final List<InsuffResource> insuffResources = new ArrayList<>();

    /** patient step log */
    private final List<PatientLog> patientLogs = new ArrayList<>();

    /** log of days with accompanying data */
    private final Map<Integer, DayRecord> dayLog = new LinkedHashMap<>();

    /** aggregate pAE */
    private final XYSeries pAE = new XYSeries("System Probability of Adverse Event", true, false);

    /** aggregate pICU */
    private final XYSeries pICU = new XYSeries("System Probability of ICU Admission", true, false);

    /** seed, for appending to log files */
    private final long seed;

    /** Date String */
    private String datestring;

    /** CathLabSim instance of the simulation */
    private CathLabSim model;

    //===== Constructor ======//

    /** Constructor of the Metrics class.
     *
     * @param resourceNames list of resources to create the XYSeries for each resource
     * @param seed random number generator seed
     * @param model CathLabSim instance of the simulation
     */
    public Metrics(List<String> resourceNames, long seed, CathLabSim model) {
        this.model = model;
        this.seed = seed;
        for (String r : resourceNames) {
            resources.put(r, new XYSeries(r, true, false));
        }

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-kkmmss");
        this.datestring = dateFormat.format(date);
    }
    //===== Methods ======//

    /** Get the list of current patients.
     *
     * @param entityManager Entity Manager instance
     * @return list of patients
     */
    private static List<Patient> getCurrentPatients(EntityManager entityManager) {
        return entityManager.labMap.values().stream()
                .filter(l -> l.currentPatient != null && (l.currentPatient.status == Patient.Status.DURING || l.currentPatient.status == Patient.Status.RESCUE))
                .map(l -> l.currentPatient)
                .toList();
    }

    /** Record a point in the various XYSeries.
     *
     * @param model CathLabSim instance of the simulation
     */
    public void recordPoint(CathLabSim model) {
        double minute = model.schedule.getTime();
        List<Patient> patientList = getCurrentPatients(model.entityManager);
        model.LOGGER.info("Patient list is {}", patientList);


        patients.addOrUpdate(minute, patientList.size());
        pAE.addOrUpdate(minute, computePAE(patientList));
        pICU.addOrUpdate(minute, computePICU(patientList));


        for (Resource r: model.entityManager.resourceMap.values()) {
            resources
                    .get(r.name)
                    .addOrUpdate(minute,
                            r.resourceInstances.stream()
                                    .filter(i-> i.inUse).count()
                    );
        }
    }

    /** Add a record for insufficient resources to its log.
     *
     * @param patient patient with insufficient resources
     * @param resource resource that's missing
     * @param time simulation time
     * @param count amount of resources that's missing
     * @param proceedWithout whether simulation proceeded without the resources
     */
    public void addInsuffResource(Patient patient, Resource resource, double time, int count, boolean proceedWithout) {
        this.insuffResources.add(new InsuffResource(patient, resource, time, count, proceedWithout));
    }

    /** Add a procedure step record to the patient log.
     *
     * @param patient patient to add to log
     * @param step procedure step patient is in
     * @param time simulation time
     */
    public void addPatientLog(Patient patient, Procedure.Step step, double time) {
        addPatientLog(patient, step.name, time);
    }

    /** Add a patient event record to the patient log.
     *
     * @param patient patient to add to log
     * @param event event to record
     * @param time simulation time
     */
    public void addPatientLog(Patient patient, String event, double time) {
        this.patientLogs.add(new PatientLog(patient, event, time));
    }

    /** Add a day record to the log of days for the simulation. Should be called at the start of the day.
     *
     * @param day Integer day of the simulation to create the record for
     * @param model CathLabSim instance of the simulation
     */
    public void addDayRecord(Integer day, CathLabSim model) {
        this.dayLog.put(day, new DayRecord(model));
    }

    /** Count an instance of a non-null AE level.
     *
     * @param day Integer day for accessing the DayRecord on which to record the AE occurrence
     * @param level AE occurrence severity level
     */
    public void addAELevelCount(Integer day, Patient.AELevel level) {
        DayRecord today = dayLog.get(day);
        today.AELevelCounts.put(level, today.AELevelCounts.get(level) + 1);
        today.AETotalCount += 1;
    }

    /** Count an instance of a high risk level case.
     *
     * @param day Integer day for accessing the DayRecord on which to record the high risk instance
     */
    public void addHighRisk(Integer day) {
        DayRecord today = dayLog.get(day);
        today.numHighRisk += 1;
    }

    /** Record if two or more labs are running at the end of the day.
     * A DayRecord's twoLabsRun attribute instantiates to false, so this only updates the metric if true.
     *
     * @param day Integer day for accessing the DayRecord on which to record if two labs are running
     * @param status boolean for whether two labs are running
     */
    public void setTwoLabsRunning(Integer day, boolean status) {
        DayRecord today = dayLog.get(day);
        if (status) {
            today.twoLabsRun = true;}
    }

    /** Record if the total AE occurrence level threshold for the day has been met by the end of the day.
     * A DayRecord's aeRiskThresh attribute instantiates to false, so this only updates the metric if true.
     *
     * @param day Integer day for accessing the DayRecord on which to record the total ae occurrence level
     * @param status boolean for whether the total ae occurrence level has been met
     */
    public void setAEThresh(Integer day, boolean status) {
        DayRecord today = dayLog.get(day);
        if (status) {
            today.aeRiskThresh = true;}
    }

    /** Record if the cumulative risk level threshold has been met by the end of the day.
     * A DayRecord's cumulativeRiskThresh attribute instantiates to false, so this only updates the metric if true.
     *
     * @param day Integer day for accessing the DayRecord on which to record the risk level
     * @param status boolean for whether the cumulative risk level threshold has been met
     */
    public void setCumulativeRiskThresh(Integer day, boolean status) {
        DayRecord today = dayLog.get(day);
        if (status) {
            today.cumulativeRiskThresh = true;}
    }

    /** Record if the high risk level threshold at the end of the day is met.
     * A DayRecord's EODRiskLevel attribute instantiates to false, so this only updates the metric if true.
     *
     * @param day Integer day for accessing the DayRecord on which to record the risk level
     * @param status boolean for whether the high risk level threshold at the end of the day has been met
     */
    public void setEODRiskThresh(Integer day, boolean status) {
        DayRecord today = dayLog.get(day);
        if (status) {
            today.EODRiskThresh = true;}
    }

    /** Count an instance of an unstarted addon with a long expected duration (over 2 hours) at the end of the day.
     *
     * @param day Integer day for accessing the DayRecord on which to record the instance
     */
    public void addLongCase(Integer day) {
        DayRecord today = dayLog.get(day);
        today.longCasesAtEOD += 1;
    }

    /** Count an instance of an addon being bumped at the end of the day.
     *
     * @param day Integer day for accessing the DayRecord on which to record the instance
     */
    public void addBumpedCase(Integer day) {
        DayRecord today = dayLog.get(day);
        today.bumpedCases += 1;
    }

    /** Count an instance of a patient being sent to the ICU after the case finished, if the case did not originate in the ICU. */
    public void addNewSentICUCase(Integer day) {
        DayRecord today = dayLog.get(day);
        today.newNumSentICU += 1;
    }

    /** Count an instance of a patient being sent to the ICU after the case finished. */
    public void addSentICUCase(Integer day) {
        DayRecord today = dayLog.get(day);
        today.allNumSentICU += 1;
    }

    // This could be used to track cumulative risk throughout the day.
    // Calls are in place when case starts to add to the metric, but not reported in any csv file.
    // The same metric, applied to the threshold, is recorded as boolean at the end of the day in another method.
    /** Increase the cumulative risk level for the day when a case with an eligible risk level occurs.
     * Raise it 2 points for a high risk case, and 1 point for a medium risk case.
     *
     * @param day Integer day for accessing the DayRecord on which to record the additional risk
     * @param inc integer for how much to increase the cumulative risk
     */
    public void raiseCumulativeRisk(Integer day, int inc) {
        DayRecord today = dayLog.get(day);
        today.cumulativeRiskLevel += inc;
    }

    /** Add a finished case's time to the lab's total time for the day.
     *
     * @param day Integer day for accessing the DayRecord on which to record the lab time
     * @param lab String name of lab the case was in
     * @param time double total case time once completed
     */
    public void addLabTime(Integer day, String lab, double time) {
        DayRecord today = dayLog.get(day);
        today.cumulativeLabTimes.put(lab, today.cumulativeLabTimes.get(lab) + time);
        today.cumulativeLabTimes.put("System", today.cumulativeLabTimes.get("System") + time);
    }

    /** Add the experienced delay time to a lab's total delay time for the day.
     *
     * @param day Integer day for accessing the DayRecord on which to record the delay time
     * @param lab String name of lab the case was in
     * @param time double time the case was delayed
     */
    public void addLabDelay(Integer day, String lab, double time) {
        DayRecord today = dayLog.get(day);
        today.labDelayTimes.put(lab, today.labDelayTimes.get(lab) + time);
    }

    /** Add the experienced turnover time from the prior case to this case to a lab's total turnover time for the day.
     *
     * @param day Integer day for accessing the DayRecord on which to record the turnover time
     * @param lab String name of lab the case was in
     * @param time double time between the last case and this recently finished case
     */
    public void addLabTurnover(Integer day, String lab, double time) {
        DayRecord today = dayLog.get(day);
        today.labTurnoverTimes.put(lab, today.labTurnoverTimes.get(lab) + time);
    }

    /** Add the time (in min) for which a particular instance of a given resource was used.
     *
     * @param day Integer day for accessing the DayRecord on which to record the use of the resource
     * @param ri ResourceInstance which was used
     * @param time Double representing how long the resource instance was used
     */
    public void addResourceUsage(Integer day, Resource.ResourceInstance ri, Double time) {
        DayRecord today = dayLog.get(day);
        today.resourceUsage.put(ri.name, today.resourceUsage.get(ri.name) + time);
    }

    /** Count an instance of a type of case (addon or scheduled).
     *
     * @param day Integer day for accessing the DayRecord on which to record the case type
     * @param type type of case/procedure occurring
     */
    public void addCaseTypeCount(Integer day, String type) {
        DayRecord today = dayLog.get(day);
        today.caseTypeCounts.put(type, today.caseTypeCounts.get(type) + 1);
        today.caseTotalCount += 1;
    }

    /** Count an instance of a type of addon.
     *
     * @param day Integer day for accessing the DayRecord on which to record the addon type
     * @param type type of addon occurring (urgency level)
     */
    public void addAddonTypeCount(Integer day, Patient.Urgency type) {
        DayRecord today = dayLog.get(day);
        today.addonTypeCounts.put(type, today.addonTypeCounts.get(type) + 1);
        today.addonTotalCount += 1;
    }

    /** Add a case's duration after the end of the day, if any, for its lab and the system.
     * Also records that the system had activity, if any, after the end of the day.
     *
     * @param day Integer day for accessing the DayRecord on which to record the time after the end of the day
     * @param lab name of lab the case was in
     * @param pat Patient of the case
     * @param eod time for the end of the day on this day of the simulation
     */
    public void addTimeAfterEOD(Integer day, String lab, Patient pat, double eod, DayOfWeek dayofweek) {
        DayRecord today = dayLog.get(day);
        String lab2 = lab.replace(" ", "_");
        if (dayofweek == DayOfWeek.MONDAY) {
            today.monAfter = true;
            today.labDayHadAfterEOD.put(lab2+"_Mon", true);
        } else if (dayofweek == DayOfWeek.TUESDAY) {
            today.tuesAfter = true;
            today.labDayHadAfterEOD.put(lab2+"_Tues", true);
        } else if (dayofweek == DayOfWeek.WEDNESDAY) {
            today.wedAfter = true;
            today.labDayHadAfterEOD.put(lab2+"_Wed", true);
        } else if (dayofweek == DayOfWeek.THURSDAY) {
            today.thursAfter = true;
            today.labDayHadAfterEOD.put(lab2+"_Thurs", true);
        } else if (dayofweek == DayOfWeek.FRIDAY) {
            today.friAfter = true;
            today.labDayHadAfterEOD.put(lab2+"_Fri", true);
        }
        boolean weekday = (dayofweek != DayOfWeek.SATURDAY && dayofweek != DayOfWeek.SUNDAY);
        if (weekday) {
            if (pat.addon) {
                today.addonTimeAfter += 1;
            } else {
                today.electiveTimeAfter += 1;
            }
        }
        if (pat.tEnd <= eod) {return;} // verify case ended after the end of the day
        if (pat.tStart <= eod && pat.tStart + pat.tDelay <= eod) { // only part of duration was after end of day
            today.labTimeAfterEOD.put(lab, today.labTimeAfterEOD.get(lab) + (pat.tEnd - eod));
            today.labTimeAfterEOD.put("System", today.labTimeAfterEOD.get("System") + (pat.tEnd - eod));
            if (weekday) {today.hadAfterEOD = true;}
        }
        else if (pat.tStart <= eod && pat.tStart + pat.tDelay > eod) { // can combine the else if and else,
            // but left separate in case want to add in tDelay as well
            today.labTimeAfterEOD.put(lab, today.labTimeAfterEOD.get(lab) + pat.tDuration);
            today.labTimeAfterEOD.put("System", today.labTimeAfterEOD.get("System") + pat.tDuration);
            if (weekday) {today.hadAfterEOD = true;}
        }
        else { // full case duration was post-end of day
            today.labTimeAfterEOD.put(lab, today.labTimeAfterEOD.get(lab) + pat.tDuration);
            today.labTimeAfterEOD.put("System", today.labTimeAfterEOD.get("System") + pat.tDuration);
            if (weekday) {today.hadAfterEOD = true;}
        }
    }

    /** Add an instance of a non-bumped case starting after the end of the day,
     * for either addon or scheduled, to the count.
     *
     * @param day Integer day for accessing the DayRecord on which to record the post-end-of-day start
     * @param addOn boolean for whether case starting after the end of the day was an addon (vs. scheduled)
     * @param urgency null if scheduled, otherwise the enum urgency level of the addon
     */
    public void addCaseAfterEOD(Integer day, boolean addOn, Patient.Urgency urgency) {
        DayRecord today = dayLog.get(day);
        if (addOn) {
            switch (urgency) {
                case EMERGENCY -> {today.EaddonsAfterEOD += 1;}
                case URGENT -> {today.UaddonsAfterEOD += 1;}
                case NORMAL -> {today.NaddonsAfterEOD += 1;}
            }
        }
        else {
            today.casesAfterEOD += 1;
        }
    }

    /** Compute the system level pAE (i.e., probability of at least one adverse event).
     *
     * @return probability of at least one adverse event
     */
    private double computePAE(List<Patient> patientList) {
        if (patientList.isEmpty()) {
            return 0.0;
        } else {
            double pNone = 1.0;
            for (Patient p: patientList) {
                pNone *= (1.0 - (p.ae ? 1.0 : p.pAE));
            }
            return(1.0 - pNone);
        }
    }

    /** Compute the system level pICU (i.e., the probability of at least one active case going to the ICU).
     *
     * @return probability of at least one active case going to the ICU
     */
    private double computePICU(List<Patient> patientList) {
        if (patientList.isEmpty()) {
            return 0.0;
        } else {
            double pNone = 1.0;
            for (Patient p: patientList) {
                pNone *= (1.0 - (p.pICU == 1.0 ? 0.0 : p.pICU));
            }
            return(1.0 - pNone);
        }
    }

    /** Find the variance of the given stream of values
     * @return var Double variance of the stream
     */
    public static Double findStdDev(List<Double> valueList) {
        Double mu = valueList.stream().mapToDouble(v -> v).average().orElse(0.0);
        double var = valueList.stream().mapToDouble(v -> Math.pow(v - mu, 2)).sum() / valueList.size();
        return Math.sqrt(var);
    }

    /**
     * Write desired summary run-level metrics to Mlflow.
     * Writes counts, averages, and medians for day metrics.
     * Writes standard deviation across every same day of the work week (e.g. across all Mondays) for a few key metrics.
     * Writes total lab minutes at each risk level for the run, while also tracking the lab minutes at each level from day to day.
     * Writes various daily metrics, which have a value for each simulation day, and various weekly metrics, which have a value for each full work week.
     * @param verbose If true, write full suite of metrics. If false, write just the metrics used in API.java.
     */
    public void writeToMlflow(boolean verbose) {
        double dayStart = model.startTime;
        double dayEnd = model.endTime;
        double dayEarly = model.earlyEndTime;
        double pAEMed = model.pAEThresholds.get("medium");
        double pAEHigh = model.pAEThresholds.get("high");
        double pICUMed = model.pICUThresholds.get("medium");
        double pICUHigh = model.pICUThresholds.get("high");
        List<Metric> metricList = new ArrayList<>();

        // Summary Day Metrics
        metricList.add(Metric.newBuilder().setKey("Total_AE_Count").setValue(dayLog.values().stream()
                .mapToDouble(dr -> dr.AETotalCount).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Bumped_Case_Count").setValue(dayLog.values().stream()
                .mapToDouble(dr -> dr.bumpedCases).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Total_Case_Count").setValue(dayLog.values().stream()
                .mapToDouble(dr -> dr.caseTotalCount).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Total_Addon_Count").setValue(dayLog.values().stream()
                .mapToDouble(dr -> dr.addonTotalCount).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("New_Cases_Sent_to_ICU_Count").setValue(dayLog.values().stream()
                .mapToDouble(dr -> dr.newNumSentICU).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Weekdays_With_After_EOD_Count").setValue(dayLog.values().stream()
                .mapToDouble(dr -> dr.hadAfterEOD ? 1 : 0).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        for (Map.Entry<String, Double> m : dayLog.get(0).labTimeAfterEOD.entrySet()) {
            if (m.getKey().equals("System")) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Avg_Daily_Time_After_EOD")
                        .setValue(dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                mapToDouble(dr -> dr.labTimeAfterEOD.get(m.getKey())).average().orElse(0.0))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            } else if (verbose) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Avg_Daily_Time_After_EOD")
                        .setValue(dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                mapToDouble(dr -> dr.labTimeAfterEOD.get(m.getKey())).average().orElse(0.0))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Avg_Daily_Time_After_EOD_No_Zeros")
                        .setValue(dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY
                                        && dr.labTimeAfterEOD.get(m.getKey()) != 0.0).
                                mapToDouble(dr -> dr.labTimeAfterEOD.get(m.getKey())).average().orElse(0.0))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }
        }

        if (verbose) {
            metricList.add(Metric.newBuilder().setKey("Days").setValue(dayLog.values().stream().mapToDouble(dr -> dr.simDay)
                    .max().orElse(0.0)).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            for (EnumMap.Entry<Patient.AELevel, Integer> m : dayLog.get(0).AELevelCounts.entrySet()) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().name().replace(" ", "_") + "_AE_Level_Count")
                        .setValue(dayLog.values().stream().mapToDouble(dr -> dr.AELevelCounts.get(m.getKey())).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }

            metricList.add(Metric.newBuilder().setKey("Days_With_Over_1_AEs_Count").setValue(dayLog.values().stream()
                    .filter(dr -> dr.AETotalCount > 1).count()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("Days_With_Over_1_New_ICU_Admissions_Count").setValue(dayLog.values().stream()
                    .filter(dr -> dr.newNumSentICU > 1).count()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("High_Risk_Count").setValue(dayLog.values().stream()
                    .mapToDouble(dr -> dr.numHighRisk).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("All_Cases_Sent_to_ICU_Count").setValue(dayLog.values().stream()
                    .mapToDouble(dr -> dr.allNumSentICU).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());

            metricList.add(Metric.newBuilder().setKey("Mult_Cases_Sent_to_ICU_Day_Count").setValue(dayLog.values().stream()
                            .filter(dr -> dr.newNumSentICU > 1).mapToDouble(dr -> dr.newNumSentICU).sum())
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            for (Map.Entry<String, Double> m : dayLog.get(0).cumulativeLabTimes.entrySet()) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Avg_Daily_Cumulative_Time")
                        .setValue(dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                mapToDouble(dr -> dr.cumulativeLabTimes.get(m.getKey())).average().orElse(0.0))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }

            for (Map.Entry<String, Boolean> m : dayLog.get(0).labDayHadAfterEOD.entrySet()) {
                metricList.add(Metric.newBuilder().setKey("After_Time_" + m.getKey() + "_Count").setValue(dayLog.values()
                        .stream().mapToDouble(dr -> dr.labDayHadAfterEOD.get(m.getKey()) ? 1 : 0)
                        .sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }
            metricList.add(Metric.newBuilder().setKey("After_Mondays_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.monAfter ? 1 : 0).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("After_Tuesdays_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.tuesAfter ? 1 : 0).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("After_Wednesdays_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.wedAfter ? 1 : 0).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("After_Thursdays_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.thursAfter ? 1 : 0).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("After_Fridays_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.friAfter ? 1 : 0).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("Addon_Time_After_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.addonTimeAfter).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("Elective_Time_After_Count").setValue(dayLog.values().stream().mapToDouble(dr -> dr.electiveTimeAfter).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("Scheduled_Cases_Started_After_EOD_Count").setValue(dayLog.values().stream()
                    .mapToDouble(dr -> dr.casesAfterEOD).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("EMERGENCY_Addon_Cases_Started_After_EOD_Count").setValue(dayLog.values().stream()
                    .mapToDouble(dr -> dr.EaddonsAfterEOD).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("URGENT_Addon_Cases_Started_After_EOD_Count").setValue(dayLog.values().stream()
                    .mapToDouble(dr -> dr.UaddonsAfterEOD).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey("NORMAL_Addon_Cases_Started_After_EOD_Count").setValue(dayLog.values().stream()
                    .mapToDouble(dr -> dr.NaddonsAfterEOD).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            for (Map.Entry<String, Double> m : dayLog.get(0).labDelayTimes.entrySet()) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Avg_Daily_Total_Delay_Time")
                        .setValue(dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                mapToDouble(dr -> dr.labDelayTimes.get(m.getKey())).average().orElse(0.0))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }
            for (Map.Entry<String, Double> m : dayLog.get(0).labTurnoverTimes.entrySet()) {
                if (dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).count() % 2 == 1) {
                    metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Median_Daily_Total_Turnover_Time")
                            .setValue(dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                    mapToDouble(dr -> dr.labTurnoverTimes.get(m.getKey())).sorted().toArray()[dayLog.values().size() / 2])
                            .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                } else {
                    metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Median_Daily_Total_Turnover_Time")
                            .setValue((dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                    mapToDouble(dr -> dr.labTurnoverTimes.get(m.getKey())).sorted().toArray()[dayLog.values().size() / 2] +
                                    dayLog.values().stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                            mapToDouble(dr -> dr.labTurnoverTimes.get(m.getKey())).sorted().toArray()[(dayLog.values()
                                            .size() / 2) - 1]) / 2).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                }
            }
            for (Map.Entry<String, Integer> m : dayLog.get(0).caseTypeCounts.entrySet()) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Count").setValue(dayLog.values().stream().
                        mapToDouble(dr -> dr.caseTypeCounts.get(m.getKey())).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }

            for (EnumMap.Entry<Patient.Urgency, Integer> m : dayLog.get(0).addonTypeCounts.entrySet()) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().name() + "_Addon_Count").setValue(dayLog.values().stream().
                        mapToDouble(dr -> dr.addonTypeCounts.get(m.getKey())).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }

            for (Map.Entry<String, Double> m : dayLog.get(0).resourceUsage.entrySet()) {
                metricList.add(Metric.newBuilder().setKey(m.getKey().replace(" ", "_") + "_Avg_Daily_Usage").setValue(dayLog.values()
                                .stream().filter(dr -> dr.weekDay != DayOfWeek.SATURDAY && dr.weekDay != DayOfWeek.SUNDAY).
                                mapToDouble(dr -> dr.resourceUsage.get(m.getKey())).average().orElse(0.0))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }
        }

        // Weekday Variances
        if (verbose) {
            List<DayOfWeek> weekdays = Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
            for (DayOfWeek dow : weekdays) {
                List<Double> valueList = dayLog.values().stream().filter(dr -> dr.weekDay == dow)
                        .mapToDouble(dr -> dr.labTimeAfterEOD.get("System")).boxed().toList();
                metricList.add(Metric.newBuilder().setKey("Std_Dev_System_Time_After_EOD_" + dow).setValue(findStdDev(valueList))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());

                valueList = dayLog.values().stream().filter(dr -> dr.weekDay == dow)
                        .mapToDouble(dr -> dr.cumulativeLabTimes.get("System")).boxed().toList();
                metricList.add(Metric.newBuilder().setKey("Std_Dev_System_Cumulative_Time_" + dow).setValue(findStdDev(valueList))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());

                valueList = dayLog.values().stream().filter(dr -> dr.weekDay == dow).mapToDouble(dr -> dr.AETotalCount).boxed().toList();
                metricList.add(Metric.newBuilder().setKey("Std_Dev_Total_AEs_" + dow).setValue(findStdDev(valueList))
                        .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }
        }

        // Daily Recorded Metrics
        Map<String, List<Double>> timeSeriesMap = new HashMap<>();
        timeSeriesMap.put("time", Arrays.stream(patients.toArray()[0]).boxed().toList());
        timeSeriesMap.put("patients", Arrays.stream(patients.toArray()[1]).boxed().toList());
        timeSeriesMap.put("pAE", Arrays.stream(pAE.toArray()[1]).boxed().toList());
        timeSeriesMap.put("pICU", Arrays.stream(pICU.toArray()[1]).boxed().toList());
        Map<Integer, Double> daysLowpAE = new HashMap<>();
        Map<Integer, Double> daysMediumpAE = new HashMap<>();
        Map<Integer, Double> daysHighpAE = new HashMap<>();
        Map<Integer, Double> daysLowpICU = new HashMap<>();
        Map<Integer, Double> daysMediumpICU = new HashMap<>();
        Map<Integer, Double> daysHighpICU = new HashMap<>();
        Map<Integer, Double> dayTotalLabMinutes = new HashMap<>();
        Map<Integer, Double> daypAELabMinutes = new HashMap<>();
        Map<Integer, Double> daypICULabMinutes = new HashMap<>();
        Map<Integer, Double> daysAvgRiskpAE = new HashMap<>();
        Map<Integer, Double> daysAvgRiskpICU = new HashMap<>();

        for (int i = 1; i < timeSeriesMap.get("time").size(); i++) {
            int day = (int) (timeSeriesMap.get("time").get(i) / CathSchedule.MIN_PER_DAY);
            double delta;
            if (i != timeSeriesMap.get("time").size() - 1) {
                delta = timeSeriesMap.get("time").get(i + 1) - timeSeriesMap.get("time").get(i);
            } else {
                delta = ((day + 1) * CathSchedule.MIN_PER_DAY) - timeSeriesMap.get("time").get(i);
            }
            double labMinutes = delta * timeSeriesMap.get("patients").get(i); // if all labs empty, won't count minutes
            double pAElabMinutes = labMinutes * timeSeriesMap.get("pAE").get(i);
            double pICUlabMinutes = labMinutes * timeSeriesMap.get("pICU").get(i);
            daypAELabMinutes.merge(day, pAElabMinutes, Double::sum);
            daypICULabMinutes.merge(day, pICUlabMinutes, Double::sum);
            dayTotalLabMinutes.merge(day, labMinutes, Double::sum);
            if (timeSeriesMap.get("pAE").get(i) < pAEMed) {
                daysLowpAE.merge(day, labMinutes, Double::sum);
            } else if (timeSeriesMap.get("pAE").get(i) >= pAEHigh) {
                daysHighpAE.merge(day, labMinutes, Double::sum);
            } else {
                daysMediumpAE.merge(day, labMinutes, Double::sum);
            }
            if (timeSeriesMap.get("pICU").get(i) < pICUMed) {
                daysLowpICU.merge(day, labMinutes, Double::sum);
            } else if (timeSeriesMap.get("pICU").get(i) >= pICUHigh) {
                daysHighpICU.merge(day, labMinutes, Double::sum);
            } else {
                daysMediumpICU.merge(day, labMinutes, Double::sum);
            }
        }
        int numDays = dayLog.values().stream().mapToInt(dr -> dr.simDay).max().orElse(daysLowpAE.size() - 1);

        for (int d = 0; d <= numDays; d++) {
            daysLowpAE.merge(d, 0.0, Double::sum);
            daysMediumpAE.merge(d, 0.0, Double::sum);
            daysHighpAE.merge(d, 0.0, Double::sum);
            daypAELabMinutes.merge(d, 0.0, Double::sum);
            dayTotalLabMinutes.merge(d, 0.0, Double::sum);
            double avgpAE = dayTotalLabMinutes.get(d) != 0 ? daypAELabMinutes.get(d) / dayTotalLabMinutes.get(d) : 0.0000001;

            daysAvgRiskpAE.put(d, avgpAE);
            if (verbose) {
                metricList.add(Metric.newBuilder().setKey("Daily_Low_pAE_Risk_Lab_Minutes").setValue(daysLowpAE.get(d)).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_Medium_pAE_Risk_Lab_Minutes").setValue(daysMediumpAE.get(d)).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_High_pAE_Risk_Lab_Minutes").setValue(daysHighpAE.get(d)).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_Weighted_Average_pAE").setValue(avgpAE).setTimestamp(d).setStep(d).build());
            }

            daysLowpICU.merge(d, 0.0, Double::sum);
            daysMediumpICU.merge(d, 0.0, Double::sum);
            daysHighpICU.merge(d, 0.0, Double::sum);
            daypICULabMinutes.merge(d, 0.0, Double::sum);
            double avgpICU = dayTotalLabMinutes.get(d) != 0 ? daypICULabMinutes.get(d) / dayTotalLabMinutes.get(d) : 0.0000001;

            daysAvgRiskpICU.put(d, avgpICU);
            if (verbose) {
                metricList.add(Metric.newBuilder().setKey("Daily_Low_pICU_Risk_Lab_Minutes").setValue(daysLowpICU.get(d)).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_Medium_pICU_Risk_Lab_Minutes").setValue(daysMediumpICU.get(d)).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_High_pICU_Risk_Lab_Minutes").setValue(daysHighpICU.get(d)).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_Weighted_Average_pICU").setValue(avgpICU).setTimestamp(d).setStep(d).build());
                metricList.add(Metric.newBuilder().setKey("Daily_Cases_Sent_ICU").setValue(dayLog.get(d).newNumSentICU).setTimestamp(d).setStep(d).build());
            }

            int currentDay = d;
            double timeRelativeToEOD;
            if (verbose) {
                // Lab-level
                for (Lab l : model.entityManager.labMap.values()) {
                    metricList.add(Metric.newBuilder().setKey(l.name.replace(" ", "_") + "_Daily_Utilization_Rate")
                            .setValue(dayLog.get(d).cumulativeLabTimes.get(l.name) / (dayEnd - dayStart)).setTimestamp(d).setStep(d).build());
                    timeRelativeToEOD = model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tEnd != null).
                            mapToDouble(p -> p.tEnd).max()
                            .orElse(currentDay * CathSchedule.MIN_PER_DAY + dayEnd) - (d * CathSchedule.MIN_PER_DAY + dayEnd);
                    metricList.add(Metric.newBuilder().setKey(l.name.replace(" ", "_") + "_Daily_End_Time_Relative_To_EOD")
                            .setValue(timeRelativeToEOD).setTimestamp(d).setStep(d).build());
                }
                // System-level
                metricList.add(Metric.newBuilder().setKey("System_Lab_Daily_Utilization_Rate")
                        .setValue(dayLog.get(d).cumulativeLabTimes.get("System") / (dayEnd - dayStart)).setTimestamp(d).setStep(d).build());
                timeRelativeToEOD = model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.tEnd != null).
                        mapToDouble(p -> p.tEnd).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayEnd) - (d * CathSchedule.MIN_PER_DAY + dayEnd);
                metricList.add(Metric.newBuilder().setKey("System_Daily_End_Time_Relative_To_EOD").setValue(timeRelativeToEOD)
                        .setTimestamp(d).setStep(d).build());
            }
        }

        metricList.add(Metric.newBuilder().setKey("Total_Low_pAE_Risk_Lab_Minutes").setValue(daysLowpAE.values().stream()
                .mapToDouble(d -> d).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Total_Medium_pAE_Risk_Lab_Minutes").setValue(daysMediumpAE.values().stream()
                .mapToDouble(d -> d).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Total_High_pAE_Risk_Lab_Minutes").setValue(daysHighpAE.values().stream()
                .mapToDouble(d -> d).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());

        metricList.add(Metric.newBuilder().setKey("Total_Low_pICU_Risk_Lab_Minutes").setValue(daysLowpICU.values().stream()
                .mapToDouble(d -> d).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Total_Medium_pICU_Risk_Lab_Minutes").setValue(daysMediumpICU.values().stream()
                .mapToDouble(d -> d).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        metricList.add(Metric.newBuilder().setKey("Total_High_pICU_Risk_Lab_Minutes").setValue(daysHighpICU.values().stream()
                .mapToDouble(d -> d).sum()).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());

        // Weekly Recorded Metrics
        if (verbose) {
            Map<Integer, Integer> weeklyDaysHadTimeAfterMap = new HashMap<>();
            Map<Integer, Integer> weeklyCasesStartedAfterMap = new HashMap<>();
            Map<Integer, Integer> weeklyDaysStartedAfterMap = new HashMap<>();
            Map<Integer, Integer> weeklyLabsEndedEarlyMap = new HashMap<>();
            Map<Integer, Integer> weeklyLabsStartedAfterEODMap = new HashMap<>();
            Map<Integer, Integer> weeklyLabsStartedHourBeforeRanAfterMap = new HashMap<>();
            Map<Integer, Integer> weeklyLabsRanHalfHourAfterMap = new HashMap<>();
            Map<Integer, Integer> weeklyLabsRanHourAfterMap = new HashMap<>();
            Map<Integer, Integer> weeklyMultipleSentToICUDailyMap = new HashMap<>();
            Map<Integer, Integer> weeklyDaysWithHighCumulativeProbAE = new HashMap<>();
            Map<Integer, Double> weeklyMinAtHigh = new HashMap<>();
            OptionalInt firstMon = dayLog.values().stream().filter(dr -> dr.weekDay == DayOfWeek.MONDAY).mapToInt(dr -> dr.simDay).min();
            OptionalInt lastFri = dayLog.values().stream().filter(dr -> dr.simDay >= 4 && dr.weekDay == DayOfWeek.FRIDAY).mapToInt(dr -> dr.simDay).max();

            if (firstMon.isPresent() && lastFri.isPresent()) { // sim long enough to calculate at least one week's weekly metrics
                for (int d = firstMon.getAsInt(); d <= lastFri.getAsInt(); d++) {
                    if (dayLog.get(d).weekDay != DayOfWeek.SATURDAY && dayLog.get(d).weekDay != DayOfWeek.SUNDAY) {
                        int weekNum = (d - firstMon.getAsInt()) / 7;
                        int casesStartedAfter = dayLog.get(d).casesAfterEOD + dayLog.get(d).NaddonsAfterEOD +
                                dayLog.get(d).UaddonsAfterEOD + dayLog.get(d).EaddonsAfterEOD;

                        int labsEndedEarly = 0;
                        int labsStartedAfterEOD = 0;
                        int labsStartedHourBeforeRanAfter = 0;
                        int labsRanHalfHourLate = 0;
                        int labsRanHourLate = 0;
                        int currentDay = d;
                        for (Lab l : model.entityManager.labMap.values()) {
                            if (model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tEnd != null).
                                    mapToDouble(p -> p.tEnd).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayEnd)
                                    <= currentDay * CathSchedule.MIN_PER_DAY + dayEarly) {
                                labsEndedEarly += 1;
                            }
                            if (model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tStart != null).
                                    mapToDouble(p -> p.tStart).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayStart)
                                    >= currentDay * CathSchedule.MIN_PER_DAY + dayEnd) {
                                labsStartedAfterEOD += 1;
                            }
                            if (model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tStart != null).
                                    mapToDouble(p -> p.tStart).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayStart - 60)
                                    >= currentDay * CathSchedule.MIN_PER_DAY + dayEnd - 60
                                    && model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tEnd != null).
                                    mapToDouble(p -> p.tEnd).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayStart)
                                    >= currentDay * CathSchedule.MIN_PER_DAY + dayEnd) {
                                labsStartedHourBeforeRanAfter += 1;
                            }
                            if (model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tEnd != null).
                                    mapToDouble(p -> p.tEnd).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayStart)
                                    >= currentDay * CathSchedule.MIN_PER_DAY + dayEnd + 30) {
                                labsRanHalfHourLate += 1;
                            }
                            if (model.cathSchedule.allPatients.stream().filter(p -> p.day == currentDay && p.lab == l && p.tEnd != null).
                                    mapToDouble(p -> p.tEnd).max().orElse(currentDay * CathSchedule.MIN_PER_DAY + dayStart)
                                    >= currentDay * CathSchedule.MIN_PER_DAY + dayEnd + 60) {
                                labsRanHourLate += 1;
                            }
                        }

                        weeklyDaysHadTimeAfterMap.merge(weekNum, dayLog.get(d).hadAfterEOD ? 1 : 0, Integer::sum);
                        weeklyCasesStartedAfterMap.merge(weekNum, casesStartedAfter, Integer::sum);
                        weeklyDaysStartedAfterMap.merge(weekNum, casesStartedAfter > 0 ? 1 : 0, Integer::sum);
                        weeklyLabsEndedEarlyMap.merge(weekNum, labsEndedEarly, Integer::sum);
                        weeklyLabsStartedAfterEODMap.merge(weekNum, labsStartedAfterEOD, Integer::sum);
                        weeklyLabsStartedHourBeforeRanAfterMap.merge(weekNum, labsStartedHourBeforeRanAfter, Integer::sum);
                        weeklyLabsRanHalfHourAfterMap.merge(weekNum, labsRanHalfHourLate, Integer::sum);
                        weeklyLabsRanHourAfterMap.merge(weekNum, labsRanHourLate, Integer::sum);
                        weeklyMultipleSentToICUDailyMap.merge(weekNum, dayLog.get(d).newNumSentICU > 1 ? 1 : 0, Integer::sum);
                        weeklyDaysWithHighCumulativeProbAE.merge(weekNum, daysAvgRiskpAE.get(d) >= 0.15 ? 1 : 0, Integer::sum);
                        weeklyMinAtHigh.merge(weekNum, daysHighpAE.get(d), Double::sum);
                    }
                }
                int numWeeks = ((lastFri.getAsInt() - firstMon.getAsInt() - 4) / 7) + 1;
                int badWeeksVersionOne = 0;
                int badWeeksVersionTwo = 0;
                int badWeeksVersionTwoPOne = 0;
                int badWeekOver3TimeAfterCounter = 0;
                int badWeekOver2TimeAfterCounter = 0;
                int badWeekOver600MinHighCounter = 0;
                int badWeekOver400MinHighCounter = 0;
                int badWeekOver800MinHighCounter = 0;
                int badWeekOver0MultICUCounter = 0;
                int badWeekOver1MultICUCounter = 0;
                int badWeekOver2MultICUCounter = 0;
                int badWeekOver1EarlyEndCounter = 0;
                int badWeekOver0EarlyEndCounter = 0;
                int badWeekOver2EarlyEndCounter = 0;
                int badWeekOver0StartAfterEODCounter = 0;
                int badWeekOver1StartAfterEODCounter = 0;
                int badWeekOver2StartAfterEODCounter = 0;
                int badWeekOver0StartAndRunHourCounter = 0;
                int badWeekOver1StartAndRunHourCounter = 0;
                int badWeekOver2StartAndRunHourCounter = 0;
                int badWeekOver1RanHalfHourAfterCounter = 0;
                int badWeekOver2RanHalfHourAfterCounter = 0;
                int badWeekOver1RanHourAfterCounter = 0;
                int badWeekOver2RanHourAfterCounter = 0;

                for (int w = 0; w < numWeeks; w++) {
                    if (weeklyDaysHadTimeAfterMap.get(w) > 3 || weeklyMinAtHigh.get(w) > 600
                            || weeklyMultipleSentToICUDailyMap.get(w) > 0) {
                        badWeeksVersionOne += 1;
                    }
                    if (weeklyLabsEndedEarlyMap.get(w) > 1 && weeklyLabsStartedAfterEODMap.get(w) > 1) {
                        badWeeksVersionTwo += 1;
                    }
                    if (weeklyLabsEndedEarlyMap.get(w) > 1 && weeklyLabsStartedHourBeforeRanAfterMap.get(w) > 1) {
                        badWeeksVersionTwoPOne += 1;
                    }
                    if (weeklyDaysHadTimeAfterMap.get(w) > 3) {
                        badWeekOver3TimeAfterCounter += 1;
                    }
                    if (weeklyDaysHadTimeAfterMap.get(w) > 2) {
                        badWeekOver2TimeAfterCounter += 1;
                    }
                    if (weeklyMinAtHigh.get(w) > 600) {
                        badWeekOver600MinHighCounter += 1;
                    }
                    if (weeklyMinAtHigh.get(w) > 400) {
                        badWeekOver400MinHighCounter += 1;
                    }
                    if (weeklyMinAtHigh.get(w) > 800) {
                        badWeekOver800MinHighCounter += 1;
                    }
                    if (weeklyMultipleSentToICUDailyMap.get(w) > 0) {
                        badWeekOver0MultICUCounter += 1;
                    }
                    if (weeklyMultipleSentToICUDailyMap.get(w) > 1) {
                        badWeekOver1MultICUCounter += 1;
                    }
                    if (weeklyMultipleSentToICUDailyMap.get(w) > 2) {
                        badWeekOver2MultICUCounter += 1;
                    }
                    if (weeklyLabsEndedEarlyMap.get(w) > 0) {
                        badWeekOver0EarlyEndCounter += 1;
                    }
                    if (weeklyLabsEndedEarlyMap.get(w) > 1) {
                        badWeekOver1EarlyEndCounter += 1;
                    }
                    if (weeklyLabsEndedEarlyMap.get(w) > 2) {
                        badWeekOver2EarlyEndCounter += 1;
                    }
                    if (weeklyLabsStartedAfterEODMap.get(w) > 2) {
                        badWeekOver2StartAfterEODCounter += 1;
                    }
                    if (weeklyLabsStartedAfterEODMap.get(w) > 1) {
                        badWeekOver1StartAfterEODCounter += 1;
                    }
                    if (weeklyLabsStartedAfterEODMap.get(w) > 0) {
                        badWeekOver0StartAfterEODCounter += 1;
                    }
                    if (weeklyLabsStartedHourBeforeRanAfterMap.get(w) > 0) {
                        badWeekOver0StartAndRunHourCounter += 1;
                    }
                    if (weeklyLabsStartedHourBeforeRanAfterMap.get(w) > 1) {
                        badWeekOver1StartAndRunHourCounter += 1;
                    }
                    if (weeklyLabsStartedHourBeforeRanAfterMap.get(w) > 2) {
                        badWeekOver2StartAndRunHourCounter += 1;
                    }
                    if (weeklyLabsRanHalfHourAfterMap.get(w) > 1) {
                        badWeekOver1RanHalfHourAfterCounter += 1;
                    }
                    if (weeklyLabsRanHalfHourAfterMap.get(w) > 2) {
                        badWeekOver2RanHalfHourAfterCounter += 1;
                    }
                    if (weeklyLabsRanHourAfterMap.get(w) > 1) {
                        badWeekOver1RanHourAfterCounter += 1;
                    }
                    if (weeklyLabsRanHourAfterMap.get(w) > 2) {
                        badWeekOver2RanHourAfterCounter += 1;
                    }

                    metricList.add(Metric.newBuilder().setKey("Weekly_Days_With_Time_After_EOD_Count")
                            .setValue(weeklyDaysHadTimeAfterMap.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Cases_Starting_After_EOD_Count")
                            .setValue(weeklyCasesStartedAfterMap.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Days_With_Starting_After_EOD_Count")
                            .setValue(weeklyDaysStartedAfterMap.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Labs_Ending_Early_Count")
                            .setValue(weeklyLabsEndedEarlyMap.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Labs_Starting_After_EOD_Count")
                            .setValue(weeklyLabsStartedAfterEODMap.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Days_With_Multiple_Cases_Sent_to_ICU_Count")
                            .setValue(weeklyMultipleSentToICUDailyMap.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Days_With_High_Avg_Cumulative_pAE")
                            .setValue(weeklyDaysWithHighCumulativeProbAE.get(w)).setTimestamp(w).setStep(w).build());
                    metricList.add(Metric.newBuilder().setKey("Weekly_Min_At_High_Risk")
                            .setValue(weeklyMinAtHigh.get(w)).setTimestamp(w).setStep(w).build());
                }
                metricList.add(Metric.newBuilder().setKey("Badly_Balanced_Weeks_Version_One_Count")
                        .setValue(badWeeksVersionOne).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Badly_Balanced_Weeks_Version_Two_Count")
                        .setValue(badWeeksVersionTwo).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Badly_Balanced_Weeks_Version_TwoPointOne")
                        .setValue(badWeeksVersionTwoPOne).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Time_After_Over2")
                        .setValue(badWeekOver2TimeAfterCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Time_After_Over3")
                        .setValue(badWeekOver3TimeAfterCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Min_High_Over400")
                        .setValue(badWeekOver400MinHighCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Min_High_Over600")
                        .setValue(badWeekOver600MinHighCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Min_High_Over800")
                        .setValue(badWeekOver800MinHighCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Mult_ICU_Over0")
                        .setValue(badWeekOver0MultICUCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Mult_ICU_Over1")
                        .setValue(badWeekOver1MultICUCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Mult_ICU_Over2")
                        .setValue(badWeekOver2MultICUCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Early_End_Over0")
                        .setValue(badWeekOver0EarlyEndCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Early_End_Over1")
                        .setValue(badWeekOver1EarlyEndCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Early_End_Over2")
                        .setValue(badWeekOver2EarlyEndCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Start_After_Over0")
                        .setValue(badWeekOver0StartAfterEODCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Start_After_Over1")
                        .setValue(badWeekOver1StartAfterEODCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Start_After_Over2")
                        .setValue(badWeekOver2StartAfterEODCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Start_And_Run_Hour_Over_0")
                        .setValue(badWeekOver0StartAndRunHourCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Start_And_Run_Hour_Over_1")
                        .setValue(badWeekOver1StartAndRunHourCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Start_And_Run_Hour_Over_2")
                        .setValue(badWeekOver2StartAndRunHourCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Ran_Half_Hour_Past_Over_1")
                        .setValue(badWeekOver1RanHalfHourAfterCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Ran_Half_Hour_Past_Over_2")
                        .setValue(badWeekOver2RanHalfHourAfterCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Ran_Hour_Past_Over_1")
                        .setValue(badWeekOver1RanHourAfterCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
                metricList.add(Metric.newBuilder().setKey("Bad_Week_Criteria_Counter_Ran_Hour_Past_Over_2")
                        .setValue(badWeekOver2RanHourAfterCounter).setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            }
        }

        List<List<Service.Metric>> metricLists = new ArrayList<>();
        int batchMax = 1000;
        int i = 0;
        int total = metricList.size();
        while (i < total) {
            if (total-i <= batchMax) {
                metricLists.add(metricList.subList(i, total));
                i += (total - i);
            } else {
                metricLists.add(metricList.subList(i, i + batchMax));
                i += batchMax;
            }
        }
        for (List<Service.Metric> ml : metricLists) {
            model.mlflowClient.logBatch(model.mlflowRunId, ml, null, null);
        }
    }

    /** Log parameters to a run in mlflow
     *
     * @param client MlflowClient where the run is
     * @param par whether the parameters are being logged on a parent run
     * @param addonBucketFile value from run parameters
     * @param earlyEndTime value originally from the config file
     * @param endTime value originally from the config file
     * @param extraDays value from run parameters
     * @param folderName value from run parameters
     * @param pushCases value originally from the config file
     * @param runId which run to log the parameters to
     * @param scheduleRunId identifies the run holding the schedule in scheduler mlflow experiment
     * @param scheduleName value from run parameters
     * @param seedOrRuns value from run parameters
     * @param startDate value originally from the config file
     * @param startTime value originally from the config file
     */
    public static void logAllParams(MlflowClient client, boolean par, String addonBucketFile, String earlyEndTime,
                                    String endTime, String extraDays, String folderName, String pushCases, String runId,
                                    String scheduleRunId, String scheduleName, String seedOrRuns, String startDate,
                                    String startTime) {
        List<Param> paramList = new ArrayList<>();
        if (!par) {
            paramList.add(Param.newBuilder().setKey("seed").setValue(seedOrRuns).build());
            paramList.add(Param.newBuilder().setKey("run_type").setValue("simulation_iteration").build());
        }
        paramList.add(Param.newBuilder().setKey("log_folder").setValue(folderName).build());
        paramList.add(Param.newBuilder().setKey("schedule").setValue(scheduleName).build());
        if (addonBucketFile != null) {
            paramList.add(Param.newBuilder().setKey("local_addon_bucket").setValue(addonBucketFile).build());
        }
        if (scheduleRunId != null) {
            paramList.add(Param.newBuilder().setKey("schedule_run_id").setValue(scheduleRunId).build());
        }
        paramList.add(Param.newBuilder().setKey("extra_days").setValue(extraDays).build());
        paramList.add(Param.newBuilder().setKey("start_date").setValue(startDate).build());
        paramList.add(Param.newBuilder().setKey("start_time").setValue(startTime).build());
        paramList.add(Param.newBuilder().setKey("end_time").setValue(endTime).build());
        paramList.add(Param.newBuilder().setKey("early_end_time").setValue(earlyEndTime).build());
        paramList.add(Param.newBuilder().setKey("push_cases").setValue(pushCases).build());
        client.logBatch(runId, null, paramList, null);
    }

    /**
     * Write all metrics to CSV files.
     *
     * @throws IOException if files cannot be open
     */
    public void writeToCSV() throws IOException {
        String newFolder = String.format("logs/metrics_%s", model.logFolder);
        new File(newFolder).mkdirs();

        Map<String, List<Double>> timeSeriesMap = new HashMap<>();

        timeSeriesMap.put("time", Arrays.stream(patients.toArray()[0]).boxed().toList());
        timeSeriesMap.put("patients", Arrays.stream(patients.toArray()[1]).boxed().toList());
        timeSeriesMap.put("pAE", Arrays.stream(pAE.toArray()[1]).boxed().toList());
        timeSeriesMap.put("pICU", Arrays.stream(pICU.toArray()[1]).boxed().toList());
        for (Map.Entry<String, XYSeries> entry: resources.entrySet()) {
            timeSeriesMap.put(entry.getKey(), Arrays.stream(entry.getValue().toArray()[1]).boxed().toList());
        }
        List<List<Double>> timeSeriesRecords = new ArrayList<>();
        ListIterator<Double> timeseriesIter = timeSeriesMap.get("time").listIterator();
        while (timeseriesIter.hasNext()) {
            timeSeriesRecords.add(timeSeriesMap.entrySet().stream().map(entry -> entry.getValue().get(timeseriesIter.nextIndex())).toList());
            timeseriesIter.next();
        }

        new File(newFolder+"/timeseries").mkdirs();
        FileWriter timeSeriesWriter = new FileWriter(String.format(newFolder+"/timeseries/timeseries_%d.csv", this.seed));
        CSVPrinter timeSeriesPrinter = new CSVPrinter(timeSeriesWriter, CSVFormat.DEFAULT);
        timeSeriesPrinter.printRecord(timeSeriesMap.keySet());
        timeSeriesPrinter.printRecords(timeSeriesRecords);
        timeSeriesWriter.flush();
        timeSeriesWriter.close();

        new File(newFolder+"/insuffResource").mkdirs();
        FileWriter insuffresourceWriter = new FileWriter(String.format(newFolder+"/insuffResource/insuffResource_%d.csv", this.seed));
        CSVPrinter insuffresourcePrinter = new CSVPrinter(insuffresourceWriter, CSVFormat.DEFAULT);
        insuffresourcePrinter.printRecord("patient", "resource", "time", "count", "proceedWithout");
        insuffresourcePrinter.printRecords(insuffResources.stream()
                .map(ir -> new String[]{ir.patient.toString(), ir.resource.toString(), String.valueOf(ir.time),
                        String.valueOf(ir.count), String.valueOf(ir.proceedWithout)}).toList());
        insuffresourceWriter.flush();
        insuffresourceWriter.close();

        new File(newFolder+"/patientLog").mkdirs();
        FileWriter patientLogWriter = new FileWriter(String.format(newFolder+"/patientLog/patientLog_%d.csv", this.seed));
        CSVPrinter patientLogPrinter = new CSVPrinter(patientLogWriter, CSVFormat.DEFAULT);
        patientLogPrinter.printRecord("patient", "event", "time");
        patientLogPrinter.printRecords(patientLogs.stream().map(patientLog -> new String[]{
                patientLog.patient.toString(),
                patientLog.event,
                String.valueOf(patientLog.time)
        }).toList());
        patientLogWriter.flush();
        patientLogWriter.close();

        new File(newFolder+"/patients").mkdirs();
        FileWriter patientsWriter = new FileWriter(String.format(newFolder+"/patients/patients_%d.csv", this.seed));
        CSVPrinter patientsPrinter = new CSVPrinter(patientsWriter, CSVFormat.DEFAULT);
        patientsPrinter.printRecord(List.of(Patient.csvHeader()));
        patientsPrinter.printRecords(model.cathSchedule.allPatients.stream().map(Patient::csvRow).toList());
        patientsWriter.flush();
        patientsWriter.close();

        new File(newFolder+"/days").mkdirs();
        FileWriter daysWriter = new FileWriter(String.format(newFolder+"/days/days_%d.csv", this.seed));
        CSVPrinter daysPrinter = new CSVPrinter(daysWriter, CSVFormat.DEFAULT);
        ArrayList<String> colNames = new ArrayList<>();
        colNames.add("Day");
        for (EnumMap.Entry<Patient.AELevel, Integer> m : dayLog.get(0).AELevelCounts.entrySet()) {
            colNames.add(m.getKey().name() + " AE Level Count"); // should read (MED/HIGH) AE Level Count
        }
        colNames.add("High Risk Count");
        colNames.add("Bumped Cases");
        colNames.add("All Cases Sent to ICU");
        colNames.add("New Cases Sent to ICU");
        for (Map.Entry<String, Double> m : dayLog.get(0).cumulativeLabTimes.entrySet()) {
            colNames.add(m.getKey() + " Cumulative Time"); // (Lab x) Cumulative Time
        }
        for (Map.Entry<String, Double> m : dayLog.get(0).labTimeAfterEOD.entrySet()) {
            colNames.add(m.getKey() + " Time After End of Day"); // (Lab x) Time After End of Day
        }
        colNames.add("Weekdays With After End of Day");
        colNames.add("Scheduled Cases Started After End of Day");
        colNames.add("EMERGENCY Addon Cases Started After End of Day");
        colNames.add("URGENT Addon Cases Started After End of Day");
        colNames.add("NORMAL Addon Cases Started After End of Day");
        for (Map.Entry<String, Double> m : dayLog.get(0).labDelayTimes.entrySet()) {
            colNames.add(m.getKey() + " Total Delay Time"); // (Lab x) Total Delay Time
        }
        for (Map.Entry<String, Double> m : dayLog.get(0).labTurnoverTimes.entrySet()) {
            colNames.add(m.getKey() + " Total Turnover Time"); // (Lab x) Total Turnover Time
        }
        for (Map.Entry<String, Integer> m : dayLog.get(0).caseTypeCounts.entrySet()) {
            colNames.add(m.getKey() + " Count"); // (Procedure Name) Count
        }
        colNames.add("Total Case Count");
        for (EnumMap.Entry<Patient.Urgency, Integer> m : dayLog.get(0).addonTypeCounts.entrySet()) {
            colNames.add(m.getKey() + " Addon Count"); // (Normal/Urgent/Emergency) Addon Count
        }
        colNames.add("Total Addon Count");
        for (Map.Entry<String, Double> m : dayLog.get(0).resourceUsage.entrySet()) {
            colNames.add(m.getKey() + " Usage"); // (Resource Instance Name) Usage
        }

        daysPrinter.printRecord(colNames);
        int fields = colNames.size();
        daysPrinter.printRecords(dayLog.values().stream().map(dr -> {
            String[] returnStr = new String[fields];
            ArrayList<String> tempArr = new ArrayList<>();
            tempArr.add(String.valueOf(dr.simDay));
            for (Integer v : dr.AELevelCounts.values()) {tempArr.add(v.toString());}
            tempArr.add(String.valueOf(dr.numHighRisk));
            tempArr.add(String.valueOf(dr.bumpedCases));
            tempArr.add(String.valueOf(dr.allNumSentICU));
            tempArr.add(String.valueOf(dr.newNumSentICU));
            for (Double v : dr.cumulativeLabTimes.values()) {tempArr.add(v.toString());}
            for (Double v : dr.labTimeAfterEOD.values()) {tempArr.add(v.toString());}
            tempArr.add(String.valueOf(dr.hadAfterEOD));
            tempArr.add(String.valueOf(dr.casesAfterEOD));
            tempArr.add(String.valueOf(dr.EaddonsAfterEOD));
            tempArr.add(String.valueOf(dr.UaddonsAfterEOD));
            tempArr.add(String.valueOf(dr.NaddonsAfterEOD));
            for (Double v : dr.labDelayTimes.values()) {tempArr.add(v.toString());}
            for (Double v : dr.labTurnoverTimes.values()) {tempArr.add(v.toString());}
            int totalCases = 0;
            for (Integer v : dr.caseTypeCounts.values()) {
                tempArr.add(v.toString());
                totalCases += v;
            }
            tempArr.add(String.valueOf(totalCases));
            int totalAddons = 0;
            for (Integer v : dr.addonTypeCounts.values()) {
                tempArr.add(v.toString());
                totalAddons += v;
            }
            tempArr.add(String.valueOf(totalAddons));
            for (Double v : dr.resourceUsage.values()) {tempArr.add(v.toString());}
            for (int i = 0; i < fields; i++) {
                returnStr[i] = tempArr.get(i);
            }
            return returnStr;
        }).toList());
        daysWriter.flush();
        daysWriter.close();

        new File(newFolder+"/events").mkdirs();
        FileWriter eventsWriter = new FileWriter(String.format(newFolder+"/events/events_%d.csv", this.seed));
        CSVPrinter eventsPrinter = new CSVPrinter(eventsWriter, CSVFormat.DEFAULT);
        eventsPrinter.printRecord(List.of(Event.csvHeader()));
        eventsPrinter.printRecords(model.eventQueue.stream().map(Event::csvRow).toList());
        eventsWriter.flush();
        eventsWriter.close();

    }

    /** Writes simulation output csv files as artifacts to Mlflow
     *
     * @param artifs artifacts from simulation to log
     */
    public void writeArifacts(List<String> artifs) {
        String metricFolder = String.format("logs/metrics_%s", model.logFolder);
        for (String f : artifs) {
            File metricFile = new File(metricFolder + "/" + f + "/" + f + String.format("_%d.csv", this.seed));
            model.mlflowClient.logArtifact(model.mlflowRunId, metricFile);
            }
    }

    //===== Getters/Setters ======//

    //===== SubClasses ======//
    public class InsuffResource {
        public Patient patient;
        public Resource resource;
        public double time;
        public int count;
        public boolean proceedWithout;

        public InsuffResource(Patient patient, Resource resource, double time, int count, boolean proceedWithout) {
            this.patient = patient;
            this.resource = resource;
            this.time = time;
            this.count = count;
            this.proceedWithout = proceedWithout;
        }
    }

    public class PatientLog {
        public Patient patient;
        public String event;
        public double time;

        public PatientLog(Patient patient, String event, double time) {
            this.patient = patient;
            this.event = event;
            this.time = time;
        }
    }

    public class DayRecord { // note all time values reported in minutes
        public CathLabSim model;
        public int simDay;
        public DayOfWeek weekDay;
        public EnumMap<Patient.AELevel, Integer> AELevelCounts;
        public int AETotalCount;
        public int numHighRisk;
        public boolean twoLabsRun;
        public boolean aeRiskThresh;
        public boolean cumulativeRiskThresh;
        public boolean EODRiskThresh;
        public int longCasesAtEOD;
        public int bumpedCases;
        public int cumulativeRiskLevel;
        public boolean hadAfterEOD;
        public boolean monAfter;
        public boolean tuesAfter;
        public boolean wedAfter;
        public boolean thursAfter;
        public boolean friAfter;
        public int addonTimeAfter;
        public int electiveTimeAfter;
        public Map<String, Double> cumulativeLabTimes;
        public Map<String, Double> labDelayTimes;
        public Map<String, Double> labTurnoverTimes;
        public Map<String, Double> resourceUsage;
        public Map<String, Integer> caseTypeCounts;
        public int caseTotalCount;
        public EnumMap<Patient.Urgency, Integer> addonTypeCounts;
        public int addonTotalCount;
        public Map<String, Double> labTimeAfterEOD;
        public Map<String, Boolean> labDayHadAfterEOD;
        public int casesAfterEOD;
        public int EaddonsAfterEOD;
        public int UaddonsAfterEOD;
        public int NaddonsAfterEOD;
        public int allNumSentICU;
        public int newNumSentICU;
        public DayRecord(CathLabSim model) { // constructed at 12 am (midnight) at start of every day
            this.model = model;
            this.simDay = (int) model.schedule.getTime() / CathSchedule.MIN_PER_DAY;
            this.weekDay = model.startDate.plusDays(this.simDay).getDayOfWeek();

            this.AELevelCounts = new EnumMap<Patient.AELevel, Integer>(Patient.AELevel.class);
            for (Patient.AELevel lvl : Patient.AELevel.values()) {
                this.AELevelCounts.put(lvl, 0);
            }
            this.AETotalCount = 0;

            this.numHighRisk = 0;
            this.twoLabsRun = false;
            this.aeRiskThresh = false;
            this.cumulativeRiskThresh = false;
            this.EODRiskThresh = false;
            this.longCasesAtEOD = 0;
            this.bumpedCases = 0;
            this.cumulativeRiskLevel = 0;
            this.hadAfterEOD = false;
            this.monAfter = false;
            this.tuesAfter = false;
            this.wedAfter = false;
            this.thursAfter = false;
            this.friAfter = false;
            this.addonTimeAfter = 0;
            this.electiveTimeAfter = 0;

            this.cumulativeLabTimes = new LinkedHashMap<>();
            for (Lab l : model.entityManager.labMap.values()) {
                this.cumulativeLabTimes.put(l.name, 0.0);
            }
            this.cumulativeLabTimes.put("System", 0.0);

            this.labDelayTimes = new LinkedHashMap<>();
            for (Lab l : model.entityManager.labMap.values()) {
                this.labDelayTimes.put(l.name, 0.0);
            }

            this.labTurnoverTimes = new LinkedHashMap<>();
            for (Lab l : model.entityManager.labMap.values()) {
                this.labTurnoverTimes.put(l.name, 0.0);
            }

            this.resourceUsage = new LinkedHashMap<>();
            for (Resource r : model.entityManager.resourceMap.values()) {
                if (r.resourceInstances.size() > 0) {
                    for (Resource.ResourceInstance ri : r.resourceInstances) {
                        this.resourceUsage.put(ri.name, 0.0);
                    }
                }
            }

            this.caseTypeCounts = new LinkedHashMap<>();
            for (Procedure c : model.entityManager.procedureMap.values()) {
                this.caseTypeCounts.put(c.name, 0);
            }
            this.caseTotalCount = 0;

            this.addonTypeCounts = new EnumMap<Patient.Urgency, Integer>(Patient.Urgency.class);
            for (Patient.Urgency lvl : Patient.Urgency.values()) {
                this.addonTypeCounts.put(lvl, 0);
            }
            this.addonTotalCount = 0;

            this.labTimeAfterEOD = new LinkedHashMap<>();
            for (Lab l : model.entityManager.labMap.values()) {
                this.labTimeAfterEOD.put(l.name, 0.0);
            }
            this.labTimeAfterEOD.put("System", 0.0);

            this.labDayHadAfterEOD = new LinkedHashMap<>();
            for (Lab l : model.entityManager.labMap.values()) {
                this.labDayHadAfterEOD.put(l.name.replace(" ", "_")+"_Mon", false);
                this.labDayHadAfterEOD.put(l.name.replace(" ", "_")+"_Tues", false);
                this.labDayHadAfterEOD.put(l.name.replace(" ", "_")+"_Wed", false);
                this.labDayHadAfterEOD.put(l.name.replace(" ", "_")+"_Thurs", false);
                this.labDayHadAfterEOD.put(l.name.replace(" ", "_")+"_Fri", false);
            }

            this.casesAfterEOD = 0;
            this.EaddonsAfterEOD = 0;
            this.UaddonsAfterEOD = 0;
            this.NaddonsAfterEOD = 0;
            this.allNumSentICU = 0;
            this.newNumSentICU = 0;
        }
    }
}
