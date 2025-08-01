package org.mitre.bch.cath.simulation.entity;

import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.mitre.bch.cath.simulation.utils.CathDistribution;
import org.mitre.bch.cath.simulation.utils.Config;
import org.mitre.bch.cath.simulation.utils.EntityManager;
import org.mitre.bch.cath.simulation.utils.LoggerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.io.Serial;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lab class represents the location where a Procedure is performed on a Patient.
 * This class captures all patients that are assigned to this lab up to the current day of the simulation.
 * The class implements the MASON Steppable to set case start time following the ending of a previous one.
 * @author H. Haven Liu, The MITRE Corporation
 */
public class Lab implements Steppable {
    //===== Static Attributes ======//
    /** Make this class serializable */
    @Serial
    private static final long serialVersionUID = 1L;

    /** The static logger object */
    private static final Logger SLOGGER = LoggerFactory.getLogger(Lab.class);

    /** Enum for lab types, used to assign procedure to lab */
    public enum LabType {CATH, SURG, PACE}

    //===== Attributes ======//
    /** Lab id */
    public int id;

    /** Lab name */
    public String name;

    /** Patients that are assigned to this lab up to the current day of simulation */
    public List<Patient> patients = new ArrayList<>();

    /** Lab type */
    public LabType type;

    /** Weekdays lab is open, with 0 as Monday to 6 as Sunday. */
    public List<Integer> weekdays;

    /** Weekdays lab is a preferred lab for cases being scheduled in the simulation, with 0 as Monday to 6 as Sunday. */
    public List<Integer> preferred;

    /** Patient currently in the lab. Once a patient's case is done, and it enters status AFTER, currentPatient is set to null. */
    public Patient currentPatient;

    /** Patient previously in the lab, immediately prior to either the current patient or the lab being empty. */
    public Patient priorPatient;

    /** Used for passing current time to getExpectedCurrentCaseEnd, which can't be a method and can't receive parameters. */
    public double timenow;

    //===== Constructors ======//
    /** Constructor based on id, name, and type.
     * @param id unique identifier of lab
     * @param name unique name of the lab
     * @param type lab type from LabType enum
     * @param weekdays days of the week the lab runs
     */
    public Lab(int id, String name, String type, List<Integer> weekdays) {
        this.id = id;
        this.name = name;
        this.type = LabType.valueOf(type);
        this.weekdays = weekdays;
    }

    /** Constructor from a Config.Lab instance that came from parsing of the config file.
     * @param lab an instance of the Config.Lab, as parsed from the config file
     */
    public Lab(Config.Lab lab) {
        this.id = lab.id;
        this.name = lab.name;
        this.type = LabType.valueOf(lab.labType);
        this.weekdays = lab.weekdays;
        this.preferred = lab.preferred;
    }

    //===== Methods ======//
    /** Step function for the lab object. Only called by MASON.
     * The lab step method is responsible for setting the start time for cases other than the first scheduled case of the day.
     * Start time of the case is set based on the end time of the previous case plus a turnover time sampled from a distribution.
     * This method also handles the pushing of add-on cases at the end of the day.
     * @param simState the MASON SimState object
     */
    @Override
    public void step(SimState simState) {
        CathLabSim model = (CathLabSim) simState;
        LoggerHelper LOGGER = model.LOGGER;
        CathDistribution cathDistribution = model.cathDistribution;
        double tnow = model.schedule.getTime();
        int day = (int) tnow / CathSchedule.MIN_PER_DAY;
        LOGGER.info("Lab Step for {} called @ {}", this.name, tnow);

        if (this.currentPatient == null && this.priorPatient != null && this.hasNextPatient() && this.getNextPatient().tStart == null) {
            //case ended, start next case
            Double turnover = cathDistribution.tTurnover.nextDouble();
            Patient nextPatient = this.getNextPatient();
            double potentialtStart = Double.max(turnover + this.priorPatient.tEnd, tnow);
            if (nextPatient.originaltStart != null && potentialtStart <= nextPatient.originaltStart) {
                // finished emergency case or case(s) carrying over from prior day, before lab would've started
                nextPatient.tStart = nextPatient.originaltStart;
                // don't set a turnover, interval forcefully lengthened by waiting until lab normally opens,
                //      rather than pivoting to next case asap
                nextPatient.delayed = false;
                LOGGER.info("Replacing patient {} in {}, back at original tStart time of {}",
                        nextPatient.pid, this.name, nextPatient.tStart);
                model.metrics.addPatientLog(nextPatient, String.format("Before lab opening, %s set tStart back to %f",
                        this.name, nextPatient.tStart), tnow);
                // no need to schedule, event is already on the schedule and tStart will match now when it hits that point
            } else {
                nextPatient.tStart = potentialtStart;
                nextPatient.tTurnover = nextPatient.tStart - this.priorPatient.tEnd;
                LOGGER.info("Scheduling patient {} in {}, at previous case end @ {}, with turnover of {}",
                        nextPatient.pid, this.name, nextPatient.tStart, turnover);
                if (!this.priorPatient.day.equals(nextPatient.day)) {
                    model.metrics.addPatientLog(nextPatient, String.format("Delayed lab start, %s set tStart to %f, with turnover of %f",
                            this.name, nextPatient.tStart, turnover), tnow);
                    LOGGER.info("Warning -- Patient being scheduled is the first new patient of the day. Day has a late start.");
                } else {
                    model.metrics.addPatientLog(nextPatient, String.format("Mid day, %s set tStart to %f, with turnover of %f",
                            this.name, nextPatient.tStart, turnover), tnow);
                }
                model.scheduleOnce(nextPatient.tStart, nextPatient);
            }

        } else if (this.currentPatient == null && this.priorPatient != null && this.hasNextPatient() &&
                this.getNextPatient().tStart != null && this.priorPatient.day.intValue() != this.getNextPatient().day.intValue()) {
            // all prev case ended, next case has start time, probably carried over from previous day
            Patient nextPatient = this.getNextPatient();
            double turnover = cathDistribution.tTurnover.nextDouble();

            model.metrics.addPatientLog(nextPatient, "Next case start already set to " + nextPatient.tStart, tnow);
            if (nextPatient.tStart <= tnow || nextPatient.tStart <= this.priorPatient.tEnd + turnover) {
                LOGGER.info("Prior patient {}'s status is {} with tEnd of {}, next patient is {} and tStart would have been {}",
                        this.priorPatient.pid, this.priorPatient.status, this.priorPatient.tEnd, nextPatient.pid, nextPatient.tStart);
                double newTStart = Double.max(nextPatient.tStart, Double.max(this.priorPatient.tEnd + turnover, tnow));
                LOGGER.info("Warning -- New case in a day starts, but behind schedule. Time in {} is {} and new case {} had " +
                        "start time of {}, rescheduling to {}", this.name, tnow, nextPatient.pid, nextPatient.tStart, newTStart);
                model.metrics.addPatientLog(nextPatient, "New day, Lab found tStart to " + nextPatient.tStart + " but set to " + newTStart, tnow);
                nextPatient.tStart = newTStart;
                nextPatient.tTurnover = nextPatient.tStart - this.priorPatient.tEnd;

                model.scheduleOnce(nextPatient.tStart, nextPatient);
                LOGGER.info("Scheduling patient {}, new day start @ {}", nextPatient.pid, nextPatient.tStart);
            }

        }
        if (tnow == day * CathSchedule.MIN_PER_DAY + 1) {
            // at the start of the day
            if (!this.hasNextPatient()) {
                // no new cases in this lab today
                if (this.currentPatient != null && this.currentPatient.status == Patient.Status.DURING) {
                    LOGGER.info("{} has no new cases today, current patient ({}) still going, @ {}",
                            this.name, this.currentPatient.pid, tnow);
                } else {
                    LOGGER.info("{} has no cases today, @ {}", this.name, tnow);
                }
            } else if (this.currentPatient == null && this.priorPatient == null && this.hasNextPatient()) {
                //sim starts, start of day, *no current case yet*. patient start should be set by cathSchedule
                LOGGER.info("{} sim start, @ {}", this.name, tnow);

                Patient nextPatient = this.getNextPatient();

                if (nextPatient.tStart == null) {
                    // This part is only called if the first case in the lab is an add-on, which doesn't have a tStart yet.
                    Optional<Patient> nextScheduled = patients.stream().filter(p -> p.tStart != null && p.tStart > tnow).findFirst();
                    if (nextScheduled.isPresent()){
                        // if there is scheduled non-add-on case after the add-on, push it after this add-on.
                        nextPatient.tStart = nextScheduled.get().tStart;
                        LOGGER.info("Clearing tStart of patient {} due to rescheduling", nextScheduled.get().pid);
                        nextScheduled.get().originaltStart = nextScheduled.get().tStart;
                        nextScheduled.get().tStart = null;
                        nextScheduled.get().delayed = true;
                    } else { // no current patient, next patient is an add-on, and no scheduled cases on schedule after
                        // even if an emergency add-on, need turnover time
                        //      (which isn't recorded as turnover time for the patient) to prep the lab or something
                        nextPatient.tStart = tnow + cathDistribution.tTurnover.nextDouble();
                    }
                    model.metrics.addPatientLog(nextPatient, "Start of sim, Lab set tStart to " + nextPatient.tStart, tnow);
                    model.scheduleOnce(nextPatient.tStart, nextPatient);
                    LOGGER.info("Scheduling {}, patient {}, sim start @ {}", this.name, nextPatient.pid, nextPatient.tStart);
                } else {
                    model.metrics.addPatientLog(nextPatient, "Start of sim, Lab found tStart to " + nextPatient.tStart, tnow);
                }
            } else if (this.currentPatient != null && this.currentPatient.status == Patient.Status.DURING &&
                    this.hasNextPatient() && this.getNextPatient().tStart != null) {
                LOGGER.info("Current patient ({}) is still going from prior day, next patient ({}) is on new day with a tentative start time ({})",
                        this.currentPatient.pid, this.getNextPatient().pid, this.getNextPatient().tStart);
            } else if (this.currentPatient != null && this.currentPatient.status == Patient.Status.DURING &&
                    this.hasNextPatient() && this.getNextPatient().tStart == null) {
                LOGGER.info("Current patient ({}) is still going from prior day, next patient ({}) is also from prior day, without start time yet",
                        this.currentPatient.pid, this.getNextPatient().pid);
            } else if (this.currentPatient == null && this.priorPatient != null && this.hasNextPatient() &&
                    this.getNextPatient().tStart != null) {
                LOGGER.info("Prior patient ({}) from prior day is in after, no current patient, next patient ({}) is on new day with a tentative start time ({})",
                        this.priorPatient.pid, this.getNextPatient().pid, this.getNextPatient().tStart);
            } else if (this.currentPatient == null && this.priorPatient != null && this.hasNextPatient() &&
                    this.getNextPatient().tStart == null) {
                // doesn't seem to ever happen, and don't think it should, but just in case
                LOGGER.error("Prior patient ({}) from prior day is in after, no current patient, next patient ({}) is also from prior day, without start time yet",
                        this.priorPatient.pid, this.getNextPatient().pid);
            } else {
                LOGGER.error("{} unclear what's happening @ {}.", this.name, tnow);
            }
        } else if (tnow == day * CathSchedule.MIN_PER_DAY + model.endTime) {
            // at lab end of day
            if (model.pushCases) {
                this.pushAddons(model);
            }
        }
    }

    //===== Getters/Setters ======//
    @Override
    public String toString() {
        return name;
    }

    /** Set a patient as the current patient, or set to null.
     * @param model the CathLabSim instance of the simulation
     * @param currentPatient the patient to set as current patient
     */
    public void setCurrentPatient(CathLabSim model, Patient currentPatient) {
        LoggerHelper LOGGER = model.LOGGER;
        this.currentPatient = currentPatient;
        if (currentPatient == null) {
            LOGGER.info("Set {} current patient to null", this.name);
        } else {
            LOGGER.info("Set {} current patient to {}", this.name, this.currentPatient.pid);
        }
    }

    /** Set a patient as the prior patient.
     * @param model the CathLabSim instance of the simulation
     * @param priorPatient the patient to set as the prior patient
     */
    public void setPriorPatient(CathLabSim model, Patient priorPatient) {
        LoggerHelper LOGGER = model.LOGGER;
        this.priorPatient = priorPatient;
        LOGGER.info("Set {} prior patient to {}", this.name, this.priorPatient.pid);
    }

    /** Add a patient to the lab.
     * @param model the CathLabSim instance of the simulation
     * @param patient patient to be added to the lab
     */
    public void addPatientToLab(CathLabSim model, Patient patient) {
        LoggerHelper LOGGER = model.LOGGER;
        double tnow = model.schedule.getTime();
        if (this.currentPatient == null && !hasNextPatient()) {
            patient.tStart = tnow + model.cathDistribution.tTurnover.nextDouble();
            model.schedule.scheduleOnce(patient.tStart, patient);
            model.metrics.addPatientLog(patient, String.format("%s set tStart of next add-on %d to %f",
                    this.name, patient.pid, patient.tStart), tnow);
        }
        this.patients.add(patient);
        LOGGER.info("Patient {} added to patients for {}", patient.pid, this.name);
    }

    /** Add a patient to the lab after the current patient.
     * @param model the CathLabSim instance of the simulation
     * @param patient patient to be added to the lab
     */
    public void addPatientToLabAfterCurrent(CathLabSim model, Patient patient) {
        LoggerHelper LOGGER = model.LOGGER;
        double tnow = model.schedule.getTime();
        // if prior patient finished so current is null, and the next patient's tstart is already set,
        // clear next patient's tstart, add add-on as next, and set add-on's start time
        if (this.currentPatient == null) {
            Optional<Patient> nextScheduled = patients.stream().filter(p -> p.tStart != null && p.tStart > tnow).findFirst();
            if (this.priorPatient != null && !this.priorPatient.day.equals((int) tnow / CathSchedule.MIN_PER_DAY)) {
                // early morning observation
                // whether pushing scheduled case or lab is empty for the day, schedule start now + turnover time
                patient.tStart = tnow + model.cathDistribution.tTurnover.nextDouble();
                if (nextScheduled.isPresent()) { // have a scheduled case in the future, may ultimately get tStart pushed back
                    LOGGER.info("Clearing tStart of patient {} due to possible rescheduling", nextScheduled.get().pid);
                    nextScheduled.get().originaltStart = nextScheduled.get().tStart;
                    nextScheduled.get().tStart = null;
                    nextScheduled.get().delayed = true;
                }
            }
            else if (nextScheduled.isPresent()) {
                // during day, lab has next patient(s), bump back and slot emergency add-on in vacated tStart
                // if there is case after the add-on, push it after this add-on.
                patient.tStart = nextScheduled.get().tStart;
                LOGGER.info("Clearing tStart of patient {} due to rescheduling", nextScheduled.get().pid);
                nextScheduled.get().originaltStart = nextScheduled.get().tStart;
                nextScheduled.get().tStart = null;
                nextScheduled.get().delayed = true;
            } else { // no more cases on schedule (all done or never any for that day), so schedule now plus some turnover
                patient.tStart = tnow + model.cathDistribution.tTurnover.nextDouble();
            }
            model.scheduleOnce(patient.tStart, patient);
            model.metrics.addPatientLog(patient, String.format("Lab set tStart of next add-on %d to %f",
                    patient.pid, patient.tStart), tnow);
            this.patients.add(Math.max(patients.indexOf(priorPatient) + 1, 0), patient);
        } else { // add as next patient in list after currentPatient finishes
            this.patients.add(Math.max(patients.indexOf(currentPatient) + 1, 0), patient);
        }
    }

    /** Get the expected time that the current case in the lab ends.
     * @return the expected end time of the current case.
     *          If between cases (prior case finished), return its end time.
     *          If the current case is ongoing, in rescue, or not yet started, return the start plus the expected duration.
     *          Otherwise, return 0.
     */
    public double getExpectedCurrentCaseEnd() {
        if (currentPatient == null && priorPatient != null) {
            return this.priorPatient.tEnd;
        } else if (currentPatient != null && currentPatient.status != Patient.Status.AFTER) {
            // If already past expected case end time, use the larger tnow
            return Double.max(this.currentPatient.tStart + this.currentPatient.tExpectedDuration, this.timenow);
        } else {
            return 0; // should only happen if called at sim start before any cases started/done
        }
    }

    /** Get the expected total lab time of the lab in a day.
     * @param day the day to get expected lab time
     * @return the expected total lab time
     */
    public double getExpectedTotalLabTime(int day) {
        return patients
                .stream()
                .filter(p -> p.tStart == null ? p.day == day : p.tStart.intValue() / CathSchedule.MIN_PER_DAY == day)
                .map(p -> p.status == Patient.Status.AFTER ? p.tDuration : p.tExpectedDuration)
                .reduce(0.0, Double::sum);
    }

    /** Add a bunch of patients to the lab
     * @param patients a List of patients to be added to the lab
     */
    public void addPatientsToLab(List<Patient> patients) {
        this.patients.addAll(patients);
    }

    /** Retrieve the patient subsequent to the currentPatient.
     * @return Patient subsequent to currentPatient by index.
     *          If currentPatient is null (no current patient), return patient subsequent to the prior finished patient.
     */
    public Patient getNextPatient() {
        if (hasNextPatient()) {
            if (this.currentPatient != null) {
                return patients.get(patients.indexOf(this.currentPatient) + 1);
            } else {
                // Could case a problem if called at sim start, but/so shouldn't be called there
                return patients.get(patients.indexOf(this.priorPatient) + 1);
            }
        } else {
            return null;
        }
    }

    /** Check if there is/are any more patient(s) after currentPatient, or priorPatient if no currentPatient.
     * If there is neither a currentPatient nor priorPatient, check if there are any patients for the lab.
     * @return boolean whether at least one additional patient is assigned to the lab.
     */
    public boolean hasNextPatient() {
        int patientIndex;
        if (this.currentPatient != null){
            patientIndex = patients.indexOf(this.currentPatient);
        } else if (this.priorPatient != null) {
            patientIndex = patients.indexOf(this.priorPatient);
        } else {
            return patients.size() > 0; // if no current or prior patients, just see if any patients on the list
        }
        return patients.size() > patientIndex + 1;
    }

    /** Run end-of-day pushing checks and actions for add-on cases which have not yet been started.
     * @param model CathLabSim instance to perform checks and actions on
     */
    public void pushAddons(CathLabSim model) {
        double tnow = model.schedule.getTime();
        int day = (int) tnow / CathSchedule.MIN_PER_DAY;
        LoggerHelper LOGGER = model.LOGGER;

        // Collect add-ons not yet started
        List<Patient> todayAddonNotStarted = this.patients.stream()
                .filter(p -> p.addon && p.status == Patient.Status.BEFORE && p.addonObserved != null && p.addonObserved <= tnow &&
                        p.bumpNum < model.pushBumpMap.get(p.urgency)).toList();
        LOGGER.info("List of add-ons not started in {} is {}", this.name, todayAddonNotStarted);

        // Check and possibly push each unstarted add-on
        todayAddonNotStarted
                .forEach(p -> {
                    switch (p.urgency) {
                        case EMERGENCY -> {
                            boolean emergencyMet = this.pushCriterionMet(model, Patient.Urgency.EMERGENCY, p);
                            model.metrics.addPatientLog(p, "emergency push criterion met: " + emergencyMet, tnow);
                            if (emergencyMet) {
                                p.scheduleToDay(model, 1,
                                        model.pushBooleansMap.get(Patient.Urgency.EMERGENCY).get("endOfDay"),
                                        model.pushBooleansMap.get(Patient.Urgency.EMERGENCY).get("skipToday"),
                                        true,
                                        model.pushBooleansMap.get(Patient.Urgency.EMERGENCY).get("skipWeekend"));
                                this.patients.remove(p);
                                model.metrics.addBumpedCase(day);
                                model.metrics.addPatientLog(p, String.format("Lab rescheduled emergency case to %s next day",
                                        model.pushBooleansMap.get(Patient.Urgency.EMERGENCY).get("endOfDay") ? "end of" : "start of"), tnow);
                                LOGGER.info("Emergency patient {} rescheduled to {} next day ({}) and removed from {} patients list",
                                        p.pid, model.pushBooleansMap.get(Patient.Urgency.EMERGENCY).get("endOfDay") ? "end of" : "start of",
                                        day + 1, this.name);
                            } else {
                                LOGGER.info("Conditions not met, emergency patient {} kept on today's schedule", p.pid);
                            }
                        }
                        case URGENT -> {
                            boolean urgentMet = this.pushCriterionMet(model, Patient.Urgency.URGENT, p);
                            model.metrics.addPatientLog(p, "urgent push criterion met: " + urgentMet, tnow);
                            if (urgentMet) {
                                p.scheduleToDay(model, 1,
                                        model.pushBooleansMap.get(Patient.Urgency.URGENT).get("endOfDay"),
                                        model.pushBooleansMap.get(Patient.Urgency.URGENT).get("skipToday"),
                                        true,
                                        model.pushBooleansMap.get(Patient.Urgency.URGENT).get("skipWeekend"));
                                this.patients.remove(p);
                                model.metrics.addBumpedCase(day);
                                model.metrics.addPatientLog(p, String.format("Lab rescheduled urgent case to %s next day",
                                        model.pushBooleansMap.get(Patient.Urgency.URGENT).get("endOfDay") ? "end of" : "start of"), tnow);
                                LOGGER.info("Urgent patient {} rescheduled to {} next day ({}) and removed from {} patients list",
                                        p.pid, model.pushBooleansMap.get(Patient.Urgency.URGENT).get("endOfDay") ? "end of" : "start of",
                                        day + 1, this.name);
                            } else {
                                LOGGER.info("Conditions not met, urgent patient {} kept on today's schedule", p.pid);
                            }
                        }
                        case NORMAL -> {
                            boolean normalMet = this.pushCriterionMet(model, Patient.Urgency.NORMAL, p);
                            model.metrics.addPatientLog(p, "normal push criterion met: " + normalMet, tnow);
                            if (normalMet) {
                                if (model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("skipWeekend")
                                        && model.startDate.plusDays(day).getDayOfWeek() == DayOfWeek.FRIDAY) {
                                    // if Friday & skip weekend, push to Monday
                                    p.scheduleToDay(model, 3,
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("endOfDay"),
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("skipToday"),
                                            true,
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("skipWeekend"));
                                    model.metrics.addPatientLog(p, String.format("Lab rescheduled normal case to %s following Monday",
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("endOfDay") ? "end of" : "start of"), tnow);
                                    LOGGER.info("Normal patient {} rescheduled from Friday to {} following Monday, day {}", p.pid,
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("endOfDay") ? "end of" : "start of", day + 3);
                                } else {
                                    // any other day (shouldn't be a normal pushing at end of day on weekend) push to next day
                                    p.scheduleToDay(model, 1,
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("endOfDay"),
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("skipToday"),
                                            true,
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("skipWeekend"));
                                    model.metrics.addPatientLog(p, String.format("Lab rescheduled normal case to %s next day",
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("endOfDay") ? "end of" : "start of"), tnow);
                                    LOGGER.info("Normal patient {} rescheduled to {} next day ({})", p.pid,
                                            model.pushBooleansMap.get(Patient.Urgency.NORMAL).get("endOfDay") ? "end of" : "start of", day + 1);
                                }
                                this.patients.remove(p);
                                model.metrics.addBumpedCase(day);
                                LOGGER.info("Patient {} removed from {} patients list", p.pid, this.name);
                            } else {
                                LOGGER.info("Conditions not met, normal patient {} kept on today's schedule", p.pid);
                            }
                        }
                    }
                });
    }

    /**
     * Check and return whether any push criteria are met for an add-on case.
     * @param model CathLabSim instance to get the patient list
     * @param urgency the Patient.Urgency level of the add-on case
     * @param pat the add-on Patient being considered to push
     * @return true if any criteria met, false otherwise
     */
    public boolean pushCriterionMet(CathLabSim model, Patient.Urgency urgency, Patient pat) {
        double tnow = model.schedule.getTime();
        int day = (int) tnow / CathSchedule.MIN_PER_DAY;
        EntityManager entityManager = model.entityManager;

        boolean criterionMet = false;

        for (String k: model.pushCriteriaActive.keySet()) {
            //noinspection StatementWithEmptyBody
            if (!model.pushCriteriaActive.get(k) || !model.pushCriteriaUrgencies.get(k).contains(urgency.toString())) {
            } else if (k.equals("currentLabsRunning")) {
                // labs with ongoing or still-to-start cases
                criterionMet = criterionMet | entityManager.labMap.values().stream().filter(
                                l -> l.patients.stream().anyMatch(p -> p.status != Patient.Status.AFTER &&
                                (!p.addon || p.addonObserved < tnow))).count() >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("aeMediumCount")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.aeLevel != null)
                        .mapToDouble(p2 -> p2.aeLevel == Patient.AELevel.MED ? 1 : 0).sum() >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("aeHighCount")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.aeLevel != null)
                        .mapToDouble(p2 -> p2.aeLevel == Patient.AELevel.HIGH ? 1 : 0).sum() >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("aeAnyCount")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.aeLevel != null)
                        .mapToDouble(p2 -> p2.hadae ? 1 : 0).sum() >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("aeWeightedThreshold")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.aeLevel != null)
                        .reduce(0.0, (acc, val) -> acc + (val.aeLevel == Patient.AELevel.HIGH
                                ? model.pushCriteriaValues.get(k).get("coefHIGH")
                                : model.pushCriteriaValues.get(k).get("coefMED")), Double::sum)
                                >= model.pushCriteriaValues.get(k).get("thresh");
            } else if (k.equals("MediumRiskCount")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.riskLevel == Patient.RiskLevel.MED).count()
                                >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("HighRiskCount")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.riskLevel == Patient.RiskLevel.HIGH).count()
                                >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("MixedRiskThreshold")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.day != null && p.day.equals(day) && p.riskLevel != null)
                        .reduce(0.0, (acc, val) -> acc + (val.riskLevel == Patient.RiskLevel.HIGH
                                ? model.pushCriteriaValues.get(k).get("coefHIGH")
                                : val.riskLevel == Patient.RiskLevel.MED
                                ? model.pushCriteriaValues.get(k).get("coefMED") : 0), Double::sum)
                                >= model.pushCriteriaValues.get(k).get("thresh");
            } else if (k.equals("currentHighRisk")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.tStart != null && p.tStart <= tnow
                                        && tnow - p.tStart <= model.pushCriteriaValues.get(k).get("hourRecency") * 60
                                        && p.riskLevel == Patient.RiskLevel.HIGH).count()
                                        >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("currentMediumRisk")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.tStart != null && p.tStart <= tnow
                                        && tnow - p.tStart <= model.pushCriteriaValues.get(k).get("hourRecency") * 60
                                        && p.riskLevel == Patient.RiskLevel.MED).count()
                                        >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("currentHighAERisk")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.status == Patient.Status.DURING && p.aeLevel == Patient.AELevel.HIGH).count()
                                    >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("currentMediumAERisk")) {
                criterionMet = criterionMet | model.cathSchedule.allPatients.stream()
                        .filter(p -> p.status == Patient.Status.DURING && p.aeLevel == Patient.AELevel.MED).count()
                                    >= model.pushCriteriaValues.get(k).get("num");
            } else if (k.equals("currentExpectedLongCase")) {
                criterionMet = criterionMet | pat.tExpectedDuration >= model.pushCriteriaValues.get(k).get("hourDuration") * 60;
            }
        }
        return criterionMet;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Lab lab) {
            String oKey = lab.id + " " + lab.type + " " + lab.name;
            String tKey = this.id + " " + this.type + " " + this.name;
            return oKey.equals(tKey);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (this.id + " " + this.type + " " + this.name).hashCode();
    }
}
