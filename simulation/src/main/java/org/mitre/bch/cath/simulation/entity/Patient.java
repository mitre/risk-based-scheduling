package org.mitre.bch.cath.simulation.entity;

import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.mitre.bch.cath.simulation.utils.CathDistribution;
import org.mitre.bch.cath.simulation.utils.LoggerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.io.Serial;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Patient class represents a patient/case that a Procedure is performed on in a Lab
 * This class captures the patient risks, procedure to be performed, lab assigned, resources used,
 * and various timing information
 * The class implements the MASON Steppable to move the patient from the various states and procedure steps.
 * @author H. Haven Liu, The MITRE Corporation
 */
public class Patient implements Steppable {
    //===== Static Attributes ======//
    /** Used for check-pointing by MASON. */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Static Logger object */
    private static final Logger SLOGGER = LoggerFactory.getLogger(Patient.class);
    private static final double NANOSECOND = 0.000000001;
    /** The states that patient can be in.  */
    public enum Status{BEFORE, DURING, AFTER, RESCUE}
    /** List of prior locations */
    public enum PriorLocation{ICU, FLOOR, OTHER}
    /** List of add-on urgencies **/
    public enum Urgency{URGENT, EMERGENCY, NORMAL}
    /** List of case risk level **/
    public enum RiskLevel{LOW, MED, HIGH}
    /** List of AE severity levels **/
    public enum AELevel{MED, HIGH}

    //===== Attributes ======//
    /** Patient identifier */
    public final int pid;

    /** Adverse Score */
    public final Integer adverseScore;

    /** Risk Score */
    public final Integer riskScore;

    /** Duration Score */
    public final Integer durationScore;

    /** Risk Level */
    public RiskLevel riskLevel;

    /** Procedure to perform */
    public final Procedure procedure;

    /** Whether elective or add-on */
    public final boolean addon;

    /** The day the add-on is to be observed, if applicable */
    public Integer addonDay;

    /** The time that the add-on is observed, if applicable */
    public Double addonObserved;

    /** Where patient came from */
    public final PriorLocation priorLocation;

    /** Urgency of the add-on case, if applicable */
    public Urgency urgency;

    /** The lab that the case is to be performed in */
    public Lab lab;

    /** Day of the procedure */
    public Integer day;

    /** Resources seized */
    public List<Resource.ResourceInstance> resources = new ArrayList<>();

    /** Adverse Event probability */
    public float pAE;

    /** Have Adverse Event (AE) (current) */
    public boolean ae;

    /** Had (at least one) Adverse Event during case */
    public boolean hadae;

    /** Admission to the ICU after case completion probability */
    public double pICU;

    /** Went to ICU during case */
    public boolean icu;

    /** The Adverse Event severity */
    public AELevel aeLevel;

    /** Adverse Event rescue time */
    public Double tRescue;

    /** Case start time */
    public Double tStart;

    /** Possible case original start time, if tStart was cleared for real or potential rescheduling */
    public Double originaltStart;

    /** Actual case duration */
    public Double tDuration = 0.0;

    /** Time between the start of this case and the end of last case,
     * if the gap was due to turnover and not advancing days */
    public Double tTurnover;

    /** Expected case duration */
    public Double tExpectedDuration;

    /** Delay before starting the case */
    public Double tDelay = 0.0;

    /** Add-on wait time, if applicable, from observation to start.
     * Includes wait from scheduling and any potential pushing, but not delay. */
    public Double tWait;

    /** Case/Patient state */
    public Status status;

    /** Case/Patient next state */
    public Status nextStatus;

    /** The step of the procedure being performed */
    public Procedure.Step pStep;

    /** Case end time */
    public Double tEnd;

    /** Whether this scheduled case got bumped due to an add-on, or this add-on got bumped at the end of the day */
    public boolean bumped = false;

    /** The number of times the case was bumped */
    public Integer bumpNum = 0;

    /** Whether this scheduled case was delayed from when it would have started
     * due to add-ons slotting in or cases from the previous day running long */
    public boolean delayed = false;

    /** The number of days scheduling contributes to an add-on starting after its observation on addonDay */
    public Integer schedDelay;

    /** The number of days pushing/bumping contributes to an add-on starting after its observation on addonDay,
     * including weekend days */
    public Integer bumpDelay;

    //===== Constructors ======//
    /** Constructor of a Patient.
     * @param cathLabSim    the CathLabSim instance of the simulation
     * @param procedure     the integer identifier of the procedure
     * @param adverseScore  adverse event category
     * @param riskScore     risk category
     * @param durationScore duration category
     * @param pICU          probability of admission to the ICU
     * @param addon         whether the patient/case is add-on
     * @param priorLocation where the patient came from
     * @param lab           the integer identifier of the lab where the patient/case is assigned
     * @param pid           the unique identifier of the patient/case
     * @param day           the day number that the case is scheduled for scheduled cases, or initially observed for add-on cases
     */
    public Patient (CathLabSim cathLabSim, Integer procedure, Integer adverseScore, Integer riskScore,
                    Integer durationScore, float pICU, boolean addon, String priorLocation, Integer lab, int pid, Integer day) {
        CathDistribution cathDistribution = cathLabSim.cathDistribution;
        this.day = addon ? null : day;
        this.addonDay = addon ? day : null;
        this.pid = pid;
        this.procedure = cathLabSim.entityManager.procedureMap.get(procedure);
        this.lab = addon ? null : cathLabSim.entityManager.labMap.get(lab);
        this.icu = false;
        this.hadae = false;
        this.addon = addon;
        this.adverseScore = adverseScore == null ? -1 : adverseScore;
        this.riskScore = riskScore;
        this.riskLevel = (riskScore == null || riskScore < 3) ? RiskLevel.LOW : riskScore == 3 ? RiskLevel.MED : RiskLevel.HIGH;
        this.durationScore = durationScore;
        this.pICU = pICU;
        this.priorLocation = priorLocation == null ? null : PriorLocation.valueOf(priorLocation);
        this.status = Status.BEFORE;
        this.nextStatus = Status.BEFORE;
        this.schedDelay = addon ? 0 : null;
        this.bumpDelay = addon ? 0 : null;
        this.tExpectedDuration = this.procedure.steps
                .stream()
                .map(step -> cathDistribution.tCase.get(this.durationScore, step.name).getMean())
                .reduce(0.0, Double::sum);
    }

    //===== Methods ======//
    @Override
    public String toString() {
        return "Patient " + pid;
    }

    /** Step function for the patient object. Only called by MASON.
     * The patient step function moves a patient through its status, steps, and the rescue (if AE).
     * It also handles the initial scheduling of add-on cases.
     * @param simState the MASON SimState object
     */
    @Override
    public void step(SimState simState) {
        this.status = this.nextStatus;
        CathLabSim model = (CathLabSim) simState;
        CathDistribution cathDistribution = model.cathDistribution;
        LoggerHelper LOGGER = model.LOGGER;
        double tnow = model.schedule.getTime();
        int day = (int) tnow / CathSchedule.MIN_PER_DAY;
        switch (this.status) {
            case BEFORE:
                if (this.day != null && this.day == day + 1 && this.tStart == null && this.addon) {
                    // add-on rescheduled to next day
                    LOGGER.info("This add-on {} no longer supposed to start now, likely was moved to the next day.", this.pid);
                    model.scheduleOnceIn(NANOSECOND, this.lab);
                    LOGGER.info("Scheduling {} @ {} to check for any remaining cases for this day, " +
                            "as though the moved case just finished.", this.lab.name, tnow + NANOSECOND);
                } else if (this.addonObserved != null && this.addonObserved == tnow) { // add-on now
                    float rng;
                    switch (this.priorLocation) {
                        case ICU -> {
                            rng = cathDistribution.UrgencyRandom.nextFloat();
                            this.urgency = rng <= 0.15 ? Urgency.URGENT : (rng >= 0.95 ? Urgency.EMERGENCY : Urgency.NORMAL);
                        }
                        case FLOOR -> {
                            rng = cathDistribution.UrgencyRandom.nextFloat();
                            this.urgency = rng <= 0.05 ? Urgency.URGENT : (rng >= 0.99 ? Urgency.EMERGENCY : Urgency.NORMAL);
                        }
                        case OTHER -> {
                            rng = cathDistribution.UrgencyRandom.nextFloat();
                            this.urgency = rng <= 0.05 ? Urgency.URGENT : (rng >= 0.98 ? Urgency.EMERGENCY : Urgency.NORMAL);
                        }
                    }
                    LOGGER.info("Patient {} add-on observed at {}, urgency set to {}", this.pid, tnow, this.urgency);
                    boolean isAfterEOD = tnow > day * CathSchedule.MIN_PER_DAY + model.endTime;
                    if (isAfterEOD) {
                        switch (this.urgency) {
                            case EMERGENCY -> scheduleNextSlot(model);
                            // if observed after the end of the day, urgent add-on doesn't check end of day logic,
                            // but rather just schedules to start of next day. This is also the case when a weekend.
                            case URGENT -> scheduleToDay(model, 1, false, true,
                                    false, false);
                            case NORMAL -> scheduleToDay(model, 3, true, true,
                                    false, true);
                        }
                    }
                    else {
                        switch (this.urgency) {
                            case EMERGENCY -> scheduleNextSlot(model);
                            case URGENT -> scheduleToDay(model); // this does futureDays = 0, endOfDay = true
                            case NORMAL -> scheduleToDay(model, 2, true, false,
                                    false, true);
                        }
                    }
                    model.metrics.addPatientLog(this,
                            String.format("add-on observed, urgency set to %s, assigned to %s, on day %d, with tStart %f",
                                    this.urgency, this.lab.name, this.day, this.tStart), tnow);
                } else if (this.bumped && this.tStart == null) {
                    LOGGER.info("patient {} was bumped (occurrence #{})",this.pid, this.bumpNum);
                } else if (this.tStart != null && tnow >= this.tStart) {
                    this.nextStatus = Status.DURING;
                    this.lab.setCurrentPatient(model,this);
                    model.scheduleOnceIn(NANOSECOND, this); // run the start step 0 logic a nanosecond after

                    model.metrics.addPatientLog(this, "BEFORE -> During", tnow);
                    model.metrics.addCaseTypeCount(this.day, this.procedure.name);
                    if (this.addon) {
                        model.metrics.addAddonTypeCount(this.day, this.urgency);
                    }
                    if (this.riskLevel == RiskLevel.HIGH) {
                        model.metrics.addHighRisk(this.day);
                        model.metrics.raiseCumulativeRisk(this.day, 2);
                    }
                    else if (this.riskLevel == RiskLevel.MED) {
                        model.metrics.raiseCumulativeRisk(this.day, 1);
                    }
                    if (tnow >= this.day * CathSchedule.MIN_PER_DAY + model.endTime) { // started case after end of day
                        model.metrics.addCaseAfterEOD(this.day, this.addon, this.urgency);
                    }
                } else if (this.bumped) {
                    LOGGER.info("Patient {} from yesterday bumped (occurrence #{}), was previously scheduled to start now",
                            this.pid, this.bumpNum);
                } else {
                    LOGGER.info("Patient {} had start time pushed back to {}", this.pid, this.tStart);
                }
                break;
            case DURING:
                model.metrics.addPatientLog(this, "during", tnow);
                LOGGER.info("patient {} in during", this.pid);
                if (this.pStep == null) {
                    // is there resources to start?
                    if (this.procedure.steps.get(0).areResourcesAvailable(model, this.lab)) {
                        this.pStep = this.procedure.steps.get(0);
                    } else {
                        // NOTE -- this may need additional work if resources are limited, this was never run/tested
                        //      may need to separate tScheduleStart and tActualStart,
                        //      and deal with multiple cases waiting for resources
                        tDelay = tnow - tStart;
                        model.metrics.addPatientLog(this, "delayed due to insufficient resources", tnow);
                        this.procedure.steps.get(0).resources.keySet().stream().forEach(r -> {
                            if (!r.isAvailable(model, this.lab, this.procedure.steps.get(0).resources.get(r))) {
                                model.metrics.addInsuffResource(this, r, tnow,
                                        this.procedure.steps.get(0).resources.get(r), false);
                            }
                        });
                        model.scheduleOnce(this);
                        break;
                    }
                } else if (this.ae) { // finished a step, having AE
                    LOGGER.info("patient {} should be in rescue; this should never get printed!!!", this.pid);
                    model.scheduleOnce(this);
                    break;
                } else if (this.procedure.hasStepAfter(this.pStep)) { // finished a step, no AE, continue to next step
                    this.pStep = this.procedure.getStepAfter(pStep);
                } else { // finished a step, no step after this, no AE, end case
                    model.metrics.addPatientLog(this, "DURING -> AFTER", tnow);
                    model.metrics.addLabTime(this.day, this.lab.name, this.tDuration);
                    model.metrics.addLabDelay(this.day, this.lab.name, this.tDelay);
                    if (this.tTurnover != null) {
                        model.metrics.addLabTurnover(this.day, this.lab.name, this.tTurnover);
                    }
                    this.pStep = null;
                    this.status = Status.AFTER;
                    this.nextStatus = Status.AFTER;
                    this.tEnd = this.tStart + this.tDelay + this.tDuration;
                    this.tWait = this.addon ? this.tStart - this.addonObserved : null;
                    double todayeod = this.day * CathSchedule.MIN_PER_DAY + model.endTime;
                    if (this.tEnd > todayeod) { // ends after the end of the day
                        model.metrics.addTimeAfterEOD(this.day, this.lab.name, this, todayeod,
                                model.startDate.plusDays(this.day).getDayOfWeek());
                    }
                    LOGGER.info("Patient {} ending, set to AFTER: tstart {} tdelay {} tduration {} tend {}",
                            this.pid, this.tStart, this.tDelay, this.tDuration, this.tEnd);
                    this.lab.setCurrentPatient(model,null);
                    this.lab.setPriorPatient(model, this);

                    //release resources
                    for (Resource r: this.resources.stream().map(r->r.type).collect(Collectors.toSet())) {
                        r.releaseAll(this.day, model,this);
                    }

                    model.scheduleOnceIn(NANOSECOND, this.lab);
                    LOGGER.info("Scheduling {}, patient {}, AFTER start @ {}",
                            this.lab.name, this.pid, tnow + NANOSECOND);

                    if (this.priorLocation == PriorLocation.ICU) {
                        this.pICU = 1.0;
                    }
                    float nextICUfloat = cathDistribution.ICURandom.nextFloat();
                    if (this.pICU > nextICUfloat) {
                        LOGGER.info("Patient {} sent to ICU at {}", this.pid, tnow);
                        model.metrics.addPatientLog(this, "Sent to ICU after case finished", tnow);
                        this.icu = true;
                        model.metrics.addSentICUCase(this.day);
                        if (this.pICU != 1) {
                            model.metrics.addNewSentICUCase(this.day);
                        }
                        this.status = Status.AFTER;
                    }
                    break;
                }
                model.metrics.addPatientLog(this, this.pStep, tnow);
                //seize resources
                Set<Resource> stepResources = this.pStep.resources.keySet();
                for (Resource r : stepResources) {
                    int stillNeeded = r.seize(model, this, this.pStep.resources.get(r));
                    if (stillNeeded > 0) {
                        model.metrics.addInsuffResource(this, r, tnow, stillNeeded, true);
                    }
                }

                // schedule next step
                double stepTime = cathDistribution.tCase.get(this.durationScore, this.pStep.name).nextDouble();
                // Truncating cases if random pull is over 8 hours,
                //      this may be handled differently, or with a different value, in other environments
                if (stepTime > 8*60) {
                    stepTime = 8*60;
                }
                LOGGER.info("patient {} step time: {}", this.pid, stepTime);
                this.tDuration += stepTime;

                // AE -- currently only able to trigger at the end of a step, and a case can only have 1
                if (this.aeLevel == null) {
                    this.pAE = cathDistribution.pAE.get(this.procedure.id, this.adverseScore, this.pStep.name);
                    model.metrics.addPatientLog(this, String.format("pAE at step is %.3f", this.pAE), tnow);
                    float nextFloat = cathDistribution.AERandom.nextFloat();
                    this.ae = this.pAE > nextFloat;
                    if (this.ae) {
                        this.aeLevel = cathDistribution.AERandom.nextFloat() > 0.75 ? AELevel.HIGH : AELevel.MED;
                        this.hadae = true;
                        model.metrics.addPatientLog(this, String.format("AE observed, AE Level set to %s",
                                this.aeLevel), tnow);
                        model.metrics.addAELevelCount(this.day, this.aeLevel);
                        model.metrics.addPatientLog(this, "DURING -> RESCUE", tnow + stepTime);
                        this.nextStatus = Status.RESCUE;
                        LOGGER.info("patient {} to enter rescue", this.pid);
                    }
                }

                model.scheduleOnceIn(stepTime, this);
                break;
            case AFTER:
                break;
            case RESCUE:
                LOGGER.info("patient {} in rescue", this.pid);
                model.metrics.addPatientLog(this, "RESCUE", tnow);
                //seize resources
                Set<Resource> stepRescueResources = this.pStep.rescueResources.keySet();
                for (Resource r : stepRescueResources) {
                    int stillNeeded = r.seize(model, this, this.pStep.rescueResources.get(r));
                    if (stillNeeded > 0) {
                        model.metrics.addInsuffResource(this, r, tnow, stillNeeded, true);
                    }
                }

                // schedule next event
                this.tRescue = cathDistribution.tRescue.nextDouble();
                this.tDuration += this.tRescue;

                this.nextStatus = Status.DURING;
                this.ae = false;
                LOGGER.info("patient {} rescue time: {}", this.pid, this.tRescue);
                model.scheduleOnceIn(this.tRescue, this);
                break;
        }
    }

    /** Assign day and lab to an add-on case.
     * @param model     the CathLabSim instance of the simulation
     * @param day       the day that the add-on is scheduled to.
     * @param lab       the lab that the add-on is assigned to
     * @param next      whether schedule the add-on after the completion of the current case. If true, the `day` parameter is ignored.
     * @param endOfDay  whether schedule the add-on at the end of day (if false, schedule to start of day)
     * @param bumping   whether the add-on being assigned is getting bumped from its scheduled spot or day
     */
    public void assignAddon(CathLabSim model, int day, Lab lab, boolean next, boolean endOfDay, boolean bumping) {
        LoggerHelper LOGGER = model.LOGGER;
        int currentDay = (int) model.schedule.getTime() / CathSchedule.MIN_PER_DAY;

        this.lab = lab;
        this.day = next ? currentDay : day;
        LOGGER.info("On day {} assigning patient {} to {} on day {}. next? {} eod? {}", currentDay,
                this.pid, this.lab.name, day, next, endOfDay);

        if (this.tStart != null || bumping) { // add-on case getting bumped from prior scheduled spot
            model.metrics.addPatientLog(this, "clear current tstart due to rescheduling",
                    model.schedule.getTime());
            LOGGER.info("Clearing tStart of {} from patient {} due to rescheduling", this.tStart, this.pid);
            this.originaltStart = this.tStart;
            this.tStart = null;
            this.bumped = true;
            this.bumpNum += 1;
            this.bumpDelay += this.day - currentDay;
        }

        if (next) { // ignore day, emergency add-on added to given lab after current case finishes
            if (this.lab.currentPatient == null) {
                LOGGER.info("adding emergency add-on patient {}. No current patient. The first case in {} is an emergency add-on!",
                        this.pid, this.lab.name);
            } else {
                LOGGER.info("adding emergency add-on patient {} after {}'s current patient {}, whose status is {}",
                        this.pid, this.lab.name, this.lab.currentPatient.pid, this.lab.currentPatient.status);
            }
            this.lab.addPatientToLabAfterCurrent(model, this);
            LOGGER.info("{}", this.lab.patients.stream().map(p->p.pid).collect(Collectors.toList()));
        } else if (currentDay == day) { // if it's the given day for the add-on, add to given lab
            this.lab.addPatientToLab(model, this);
        }

        if (endOfDay) { // urgent case to go at end of day (today), or normal case to go at end of whichever day
            Patient lastCaseOfDayLab = model.cathSchedule.allPatients.stream()
                    .filter(p -> p != this && p.day != null && p.lab != null && p.day == day && p.lab == lab)
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (lastCaseOfDayLab != null) { // this add-on is not the only case in that lab on that day
                int lastCaseIdx = model.cathSchedule.allPatients.indexOf(lastCaseOfDayLab);
                int thisCaseIdx = model.cathSchedule.allPatients.indexOf(this);
                model.cathSchedule.allPatients.remove(this);
                if (thisCaseIdx > lastCaseIdx) {
                    model.cathSchedule.allPatients.add(lastCaseIdx + 1, this);
                } else {
                    model.cathSchedule.allPatients.add(lastCaseIdx, this);
                }
            }
        }
        else {
            // urgent case observed post-end of day, or bumped at end of day, so doing first in passed day in whichever lab
            Patient firstCaseOfDayLab = model.cathSchedule.allPatients.stream()
                    .filter(p -> p != this && p.day != null && p.lab != null && p.day == day && p.lab == lab)
                    .reduce((first, second) -> first)
                    .orElse(null);
            if (firstCaseOfDayLab != null) { // other cases in the lab on next day to place this add-on before
                int firstCaseIdx = model.cathSchedule.allPatients.indexOf(firstCaseOfDayLab);
                model.cathSchedule.allPatients.remove(this);
                model.cathSchedule.allPatients.add(Math.max(firstCaseIdx - 1, 0), this);
            }
        }
    }

    /** Schedule the case after the completion of the current case.
     * @param model the CathLabSim instance of the simulation
     */
    public void scheduleNextSlot(CathLabSim model) {
        // put this case after the next expected completed case
        double tnow = model.schedule.getTime();
        int currentDay = (int) tnow / CathSchedule.MIN_PER_DAY;
        Optional<Lab> findLab = model.entityManager.labMap.values().stream()
                .filter(l -> l.type == this.procedure.labType &&
                        l.weekdays.contains(model.startDate.plusDays(currentDay).getDayOfWeek().getValue() - 1))
                .min(Comparator.comparingDouble(Lab::getExpectedCurrentCaseEnd));
        if (findLab.isPresent()) {
            Lab addonLab = findLab.get();
            assignAddon(model, currentDay, addonLab, true, false, false);
            if (addonLab.currentPatient == null) {
                model.scheduleOnce(addonLab);
            }
        } else {
            // No current handling for this, could potentially try scheduling to next day
            model.LOGGER.error("No lab found for scheduleNextSlot");
        }
    }

    /** Schedule the case to end of the current day.
     * @param model the CathLabSim instance of the simulation
     */
    public void scheduleToDay(CathLabSim model) {
        scheduleToDay(model, 0, true, false, false, false);
    }

    /** Schedule the case to end of day in the next few days.
     * @param model the CathLabSim instance of the simulation
     * @param futureDays the number of future days to consider when to place this case
     */
    public void scheduleToDay(CathLabSim model, int futureDays) {
        scheduleToDay(model, futureDays, true, false, false, false);
    }

    /** Schedule the case to next few days.
     * @param model the CathLabSim instance of the simulation
     * @param futureDays the number of future days to consider when to place this case
     * @param endOfDay whether schedule the case at the end of day (if false, schedule to start of day)
     */
    public void scheduleToDay(CathLabSim model, int futureDays, boolean endOfDay) {
        scheduleToDay(model, futureDays, endOfDay, false, false, false);
    }

    /** Schedule the case to sometime in the next few days.
     * Should never pass `skipToday` = true but `futureDays` = 0.
     * @param model the CathLabSim instance of the simulation
     * @param futureDays the number of future days to consider when to place this case
     * @param endOfDay whether schedule the case at the end of day (if false, schedule to start of day)
     * @param skipToday whether the current day should be skipped. Not currently used
     * @param bumping whether the case is being bumped, used in assignAddon
     * @param skipWeekend whether to skip weekend when considering scheduling possibilities
     */
    public void scheduleToDay(CathLabSim model, int futureDays, boolean endOfDay, boolean skipToday,
                              boolean bumping, boolean skipWeekend) {
        LoggerHelper LOGGER = model.LOGGER;
        double tnow = model.schedule.getTime();
        int currentDay = (int) tnow / CathSchedule.MIN_PER_DAY;

        Integer bestDay = null;
        Lab bestLab = null;
        Double bestTime = null;

        LOGGER.info("Checking availability for scheduling patient {}", this.pid);

        if (!bumping && this.urgency != Urgency.EMERGENCY) {
            // only check preferred labs for add-on assignments, not bumped cases
            // skipWeekend check is left in, in case there is a preferred weekend lab for some more urgent add-on cases,
            //      but don't want normal cases there
            for (int d : IntStream.range(0, futureDays + 1).boxed().toList()) {
                Lab bestDayLab = null;
                Double bestDayTime = null;
                Double newDayTime = null;
                // Not checking lab type for preferred add-on labs, only if the possibly scheduling day matches day lab is open
                for (Lab l : model.entityManager.labMap.values().stream()
                        .filter(l -> l.preferred.contains(model.startDate.plusDays(currentDay + d)
                                .getDayOfWeek().getValue() - 1)).toList()) {
                    if (skipWeekend && (model.startDate.plusDays(currentDay + d).getDayOfWeek() == DayOfWeek.SATURDAY ||
                            model.startDate.plusDays(currentDay + d).getDayOfWeek() == DayOfWeek.SUNDAY)) {
                        // day being checked is a weekend, and should be skipped
                        newDayTime = null;
                    } else if (d == 0) {
                        if (skipToday) {
                            newDayTime = null;
                        } else {
                            newDayTime = l.getExpectedTotalLabTime(currentDay);
                        }
                    } else {
                        // getExpectedTotalLabTime accounts for started & finished cases,
                        //      but only works for cases added to a lab's patients list, so only works for current day
                        // expected lab day end includes all expected times for cases in given lab on given day
                        newDayTime = model.cathSchedule.getExpectedLabDayEnd(currentDay + d, l);
                    }

                    if (newDayTime == null) {
                        LOGGER.info("{} not an option for scheduling on preferred day {}", l.name, currentDay + d);
                    } else {
                        LOGGER.info("Expected time for {} on preferred day {} is {}", l.name, currentDay + d, newDayTime);
                    }

                    if ((bestDayTime == null && newDayTime != null) ||
                            (bestDayTime != null && newDayTime != null && newDayTime < bestDayTime)) {
                        bestDayLab = l;
                        bestDayTime = newDayTime;
                    }
                }

                if (bestDayTime == null) {
                    LOGGER.info("Preferred day {} not an option for scheduling", currentDay + d);
                } else { // either an ineligible day, or no labs matching type
                    LOGGER.info("Best time for preferred day {} is {} in {}", currentDay + d, bestDayTime, bestDayLab.name);
                }

                if ((bestTime == null && bestDayTime != null) ||
                        (bestTime != null && bestDayTime != null && bestDayTime < bestTime)) {
                    bestDay = d;
                    bestLab = bestDayLab;
                    bestTime = bestDayTime;
                }
            }
        }

        if (bestLab == null) {
            LOGGER.info("No space in preferred labs found, checking all labs for correct days and type.");
            for (int d : IntStream.range(0, futureDays + 1).boxed().toList()) { // if futureDays is 0, only checks today
                Lab bestDayLab = null;
                Double bestDayTime = null;
                Double newDayTime = null;
                for (Lab l : model.entityManager.labMap.values().stream()
                        .filter(l -> l.type == this.procedure.labType && l.weekdays
                                .contains(model.startDate.plusDays(currentDay + d).getDayOfWeek().getValue() - 1)).toList()) {
                    if (skipWeekend && (model.startDate.plusDays(currentDay + d).getDayOfWeek() == DayOfWeek.SATURDAY ||
                            model.startDate.plusDays(currentDay + d).getDayOfWeek() == DayOfWeek.SUNDAY)) {
                        // day being checked is a weekend, and should be skipped
                        newDayTime = null;
                    } else if (d == 0) {
                        if (skipToday) {
                            newDayTime = null;
                        } else {
                            newDayTime = l.getExpectedTotalLabTime(currentDay);
                        }
                    } else {
                        // getExpectedTotalLabTime accounts for started & finished cases,
                        //      but only works for cases added to a lab's patients list, so only works for current day
                        // expected lab day end includes all expected times for cases in given lab on given day
                        newDayTime = model.cathSchedule.getExpectedLabDayEnd(currentDay + d, l);
                    }

                    if (newDayTime == null) {
                        LOGGER.info("{} not an option for scheduling on day {}", l.name, currentDay + d);
                    } else {
                        LOGGER.info("Expected time for {} on day {} is {}", l.name, currentDay + d, newDayTime);
                    }

                    if ((bestDayTime == null && newDayTime != null) ||
                            (bestDayTime != null && newDayTime != null && newDayTime < bestDayTime)) {
                        bestDayLab = l;
                        bestDayTime = newDayTime;
                    }
                }
                if (bestDayTime == null) {
                    LOGGER.info("Day {} not an option for scheduling", currentDay + d);
                } // either an ineligible day, or no labs matching type
                else {
                    LOGGER.info("Best time for day {} is {} in {}", currentDay + d, bestDayTime, bestDayLab.name);
                }

                if ((bestTime == null && bestDayTime != null) ||
                        (bestTime != null && bestDayTime != null && bestDayTime < bestTime)) {
                    bestDay = d;
                    bestLab = bestDayLab;
                    bestTime = bestDayTime;
                }
            }
        }

        if (bestLab == null) { // were no available labs of matching type on any day options
            // checks each next day, and if there is an eligible lab, places it in the best option
            LOGGER.info("No labs of correct type available over day range, so checking additional future days.");
            int d = futureDays;
            boolean found = false;
            while (!found) {
                d += 1;
                Lab bestDayLab = null;
                Double bestDayTime = null;
                Double newDayTime;
                int thisD = d;
                for (Lab l : model.entityManager.labMap.values().stream()
                        .filter(l -> l.type == this.procedure.labType && l.weekdays
                                .contains(model.startDate.plusDays(currentDay + thisD)
                                        .getDayOfWeek().getValue() - 1)).toList()) {
                    if (skipWeekend && (model.startDate.plusDays(currentDay + d).getDayOfWeek() == DayOfWeek.SATURDAY ||
                            model.startDate.plusDays(currentDay + d).getDayOfWeek() == DayOfWeek.SUNDAY)) {
                        // day being checked is a weekend, and should be skipped
                        newDayTime = null;
                    } else {
                        // expected lab day end includes all expected times for cases in given lab on given day
                        newDayTime = model.cathSchedule.getExpectedLabDayEnd(currentDay + d, l);
                    }

                    if (newDayTime == null) {
                        LOGGER.info("{} not an option for scheduling on day {}", l.name, currentDay + d);
                    } else {
                        LOGGER.info("Expected time for {} on day {} is {}", l.name, currentDay + d, newDayTime);
                    }

                    if ((bestDayTime == null && newDayTime != null) ||
                            (bestDayTime != null && newDayTime != null && newDayTime < bestDayTime)) {
                        bestDayLab = l;
                        bestDayTime = newDayTime;
                    }
                }
                if (bestDayTime == null) {
                    LOGGER.info("Day {} not an option for scheduling", currentDay + d);
                } // either an ineligible day, or no labs matching type
                else {
                    LOGGER.info("Best time for day {} is {} in {}", currentDay + d, bestDayTime, bestDayLab.name);
                }

                if (bestDayLab != null) { // found a valid lab on the checked day
                    bestDay = d;
                    bestLab = bestDayLab;
                    bestTime = bestDayTime;
                    found = true;
                } // if not, will loop back through while loop with found still false
            }
            LOGGER.info("Valid best option found, which is {} days forward, in {} on day {} with expected time of {}",
                    bestDay, bestLab.name, bestDay + currentDay, bestTime);
        }
        else {
            LOGGER.info("Best pair is {} on day {}, {} days forward", bestLab.name, bestDay + currentDay, bestDay);
        }

        Lab addonLab = bestLab;
        int addonDayAssigned = currentDay + bestDay;
        if (!bumping){
            this.schedDelay += bestDay;
        }
        model.metrics.addPatientLog(this, String.format("add-on patient %d going to %s on day %d",
                this.pid, addonLab.name, addonDayAssigned), tnow);
        LOGGER.info("Add-on patient {} going to {} on day {} ({}, {})", this.pid,
                addonLab.name, addonDayAssigned, bestDay, endOfDay);
        assignAddon(model, addonDayAssigned, addonLab, false, endOfDay, bumping);
        if (addonLab.currentPatient == null && addonLab.priorPatient != null
                && addonLab.getNextPatient() == this) {
            model.scheduleOnce(addonLab);
        }
    }

    /** Get CSV headers, corresponds to the csvRow() method.
     * @return List of Strings for CSV header row
     */
    public static String[] csvHeader() {
        return new String[]{"pid", "adverseScore", "riskScore", "durationScore", "riskLevel", "procedure", "addon",
                "addonDay", "addonObserved", "priorLocation", "urgency", "lab", "day", "pICU", "icu", "pAE", "hadae",
                "aeLevel", "tRescue", "tDuration", "tTurnover", "tExpectedDuration", "tDelay", "tWait", "tStart",
                "tEnd", "addonObserved","tStart", "tEnd", "bumped", "bumpNum", "schedDelay", "bumpDelay"
        };
    }

    /** Get the csv row value to populate a CSVPrinter.
     * @return CSV Row.
     */
    public String[] csvRow() {
        return new String[]{
                String.valueOf(this.pid),
                this.adverseScore.toString(),
                this.riskScore.toString(),
                this.durationScore.toString(),
                this.riskLevel.toString(),
                this.procedure.toString(),
                String.valueOf(this.addon),
                (this.addonDay == null ? "null" : this.addonDay).toString(),
                String.valueOf(this.addonObserved == null ? "null" : this.addonObserved),
                (this.priorLocation == null ? "null" : this.priorLocation).toString(),
                (this.urgency == null ? "null" : this.urgency).toString(),
                (this.lab == null ? "null" : this.lab).toString(),
                (this.day == null ? "null" : this.day).toString(),
                String.valueOf(this.pICU),
                String.valueOf(this.icu),
                String.valueOf(this.pAE),
                String.valueOf(this.hadae),
                (this.aeLevel == null ? "null" : this.aeLevel).toString(),
                (this.tRescue == null ? "null" : this.tRescue).toString(),
                this.tDuration.toString(),
                (this.tTurnover == null ? "null" : this.tTurnover).toString(),
                this.tExpectedDuration.toString(),
                this.tDelay.toString(),
                (this.tWait == null ? "null" : this.tWait).toString(),
                String.valueOf(this.tStart == null ? "null" : this.tStart),
                String.valueOf(this.tEnd == null ? "null" : this.tEnd),
                this.addonObserved == null ? "null" : LoggerHelper.toTime(this.addonObserved),
                this.tStart == null ? "null" : LoggerHelper.toTime(this.tStart),
                this.tEnd == null ? "null" : LoggerHelper.toTime(this.tEnd),
                String.valueOf(this.bumped),
                this.bumpNum.toString(),
                (this.schedDelay == null ? "null" : this.schedDelay).toString(),
                (this.bumpDelay == null ? "null" : this.bumpDelay).toString()
        };
    }
}
