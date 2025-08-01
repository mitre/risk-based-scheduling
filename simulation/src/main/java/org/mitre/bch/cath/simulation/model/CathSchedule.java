package org.mitre.bch.cath.simulation.model;

import org.mitre.bch.cath.simulation.entity.Lab;
import org.mitre.bch.cath.simulation.entity.Patient;
import org.mitre.bch.cath.simulation.utils.CathDistribution;
import org.mitre.bch.cath.simulation.utils.Config;
import org.mitre.bch.cath.simulation.utils.EntityManager;
import org.mitre.bch.cath.simulation.utils.LoggerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.time.*;

/** Cath Schedule class to store the entire simulated schedule. Also loop through daily schedules.
 * @author H. Haven Liu, The MITRE Corporation
 */
public class CathSchedule implements Steppable {
    //===== Static Attributes ======//
    /** Static logger */
    private static final Logger SLOGGER = LoggerFactory.getLogger(CathSchedule.class);

    /** minutes per day */
    public static final int MIN_PER_DAY = 60 * 24;

    //===== Attributes ======//
    /** List of all patients in the simulation */
    public final List<Patient> allPatients = new ArrayList<>();

    //===== Constructors ======//
    /** Constructor of the CathSchedule.
     * This method parses the schedule input file and creates a list of patients.
     * @param cathLabSim the CathLabSim instance of the simulation
     * @param configSchedule the schedule input file (list of Config.Schedule objects)
     */
    public CathSchedule(CathLabSim cathLabSim, List<Config.Schedule> configSchedule){
        for (Config.Schedule s: configSchedule){
            allPatients.add(new Patient(cathLabSim, s.procedure, s.adverseScore, s.riskScore, s.durationScore,
                    s.pICU, s.addon, s.priorLocation, s.lab, configSchedule.indexOf(s), s.day)); }
    }

    //===== Methods ======//
    /** Step function for the CathSchedule. Only called by MASON.
     * This step function is responsible for iterating schedules across days.
     * @param simState the CathLabSim instance of the simulation
     */
    @Override
    public void step(SimState simState) {
        CathLabSim model = (CathLabSim) simState;
        EntityManager entityManager = model.entityManager;
        CathDistribution cathDistribution = model.cathDistribution;
        double tnow = model.schedule.getTime();
        LoggerHelper LOGGER = model.LOGGER;

        LOGGER.info("CathSchedule Step called @ {}", tnow);
        int day = (int) tnow / MIN_PER_DAY;
        model.simLastDay = day;

        model.metrics.addDayRecord(day, model);

        DayOfWeek weekday = model.startDate.plusDays(day).getDayOfWeek();
        int addonCount = (int) cathDistribution.cAddon.get(weekday.getValue() - 1).nextDouble();
        LOGGER.info("day of week is: {} - {}", weekday.getValue() - 1, weekday.getValue());
        LOGGER.info("day {}: number of add-on patients is: {}", day, addonCount);

        for (int i = 0; i < addonCount; i++) {
            // random number generator in range of len of add-on bucket pool
            int addonIndex = (int) cathDistribution.nAddon.nextDouble();
            // grab add-on from bucket at given position
            Config.Schedule c = model.addonBucket.get(addonIndex);
            // change schedule day entry to current day and add to schedule
            int patientIndex = allPatients.size();
            allPatients.add(new Patient(model, c.procedure, c.adverseScore, c.riskScore, c.durationScore, c.pICU,
                    c.addon, c.priorLocation, c.lab, patientIndex, day));
        }

        // get patients of each day, put in labs, assign start time, put lab on schedule
        List<Patient> dayPatient = allPatients.stream().filter(p -> p.day != null && p.day == day).toList();
        List<Patient> dayAddonPatient = allPatients.stream().filter(p -> p.addon && p.addonDay == day).toList();

        LOGGER.info("day {} patients are: {}, in labs {}", day, dayPatient.stream().map(p->p.pid).toList(),
                dayPatient.stream().map(p->p.lab.name).toList());
        LOGGER.info("day {} add-on patients are: {}, labs to be determined", day,
                dayAddonPatient.stream().map(p -> p.pid).toList());

        for (Patient p: dayAddonPatient) {
            p.addonObserved = cathDistribution.tAddonObserved.nextDouble() + day * MIN_PER_DAY;
           model.scheduleOnce(p.addonObserved, p);
        }
        LOGGER.info("day {} add-on patients are: {}, to be observed at {}",
                day, dayAddonPatient.stream().map(p->p.pid).toList(),
                dayAddonPatient.stream().map(p->p.addonObserved).toList());
        for (Lab l: entityManager.labMap.values()) {
            List<Patient> labPatient = dayPatient.stream().filter(p -> p.lab == l).toList();
            if (!labPatient.isEmpty()){ // have patients in lab l on the new day
                // check if another case still to start from prior day or if current case still going.
                //      If so, set originaltStart as temp starting time
                if (l.hasNextPatient()) { // still another case(s) to start from prior day
                    double tstart = cathDistribution.tStart.get(l.id).nextDouble();
                    labPatient.get(0).originaltStart = day * MIN_PER_DAY + tstart;
                    labPatient.get(0).delayed = true;
                    LOGGER.info("Warning -- Day starts, but {} has at least one next patient ({}) " +
                                    "from prior day which has not started. Setting tentative tStart " +
                                    "for new day's first case ({}) as {}",
                            l.name, l.getNextPatient().pid, labPatient.get(0).pid, labPatient.get(0).originaltStart);
                    model.scheduleOnce(labPatient.get(0).originaltStart, labPatient.get(0));
                } else if (l.currentPatient != null) {
                    // no more cases, but current case ran past midnight and is not done yet
                    double tstart = cathDistribution.tStart.get(l.id).nextDouble();
                    labPatient.get(0).originaltStart = day * MIN_PER_DAY + tstart;
                    labPatient.get(0).delayed = true;
                    LOGGER.info("Warning -- Day stars, but current case ({}) in {} running past midnight and " +
                                    "not finished. Setting tentative tStart for new day's first case ({}) as {}",
                            l.currentPatient.pid, l.name, labPatient.get(0).pid, labPatient.get(0).originaltStart);
                    model.scheduleOnce(labPatient.get(0).originaltStart, labPatient.get(0));
                } else { // set tStart for first new day case in the lab
                    double tstart = cathDistribution.tStart.get(l.id).nextDouble();
                    labPatient.get(0).tStart = day * MIN_PER_DAY + tstart;
                    LOGGER.info("Setting patient ({}) in {} to start @ {} on day {} with tstart of {}",
                            labPatient.get(0).pid, labPatient.get(0).lab.name, labPatient.get(0).tStart, day, tstart);
                    model.scheduleOnce(labPatient.get(0).tStart, labPatient.get(0));
                }
            }
            l.addPatientsToLab(labPatient);
            model.scheduleOnce(l);
            // also add lab to schedule at end of d to check on add-ons
            model.scheduleOnce(day * MIN_PER_DAY + model.endTime, l);
            LOGGER.info("Scheduling {} start day @ {}", l.name, tnow + 1);
            LOGGER.info("{} on day {} has patients: {}", l.name, day, labPatient.stream().map(p -> p.pid).toList());
        }

        // schedule cathSchedule in a day if we haven't scheduled the last patient
        int lastDay = allPatients.stream().filter(p -> !p.addon)
                .mapToInt(patient -> patient.day == null ? patient.addonDay : patient.day).max()
                .orElseThrow(NoSuchElementException::new);
        if (day < lastDay + model.extraDays) {
            model.scheduleOnceIn(MIN_PER_DAY, this);
            LOGGER.info("Scheduling CathSchedule, @ {}", tnow + MIN_PER_DAY);
        }
    }

    /** Get the expected end time for a day in a certain lab.
     * @param day day
     * @param lab lab
     * @return expected end time
     */
    public double getExpectedLabDayEnd(int day, Lab lab) {
        return allPatients
                .stream()
                .filter(p -> p.day != null && p.lab != null && p.day == day && p.lab == lab)
                .map(p -> p.tExpectedDuration)
                .reduce(0.0, Double::sum);
    }
}
