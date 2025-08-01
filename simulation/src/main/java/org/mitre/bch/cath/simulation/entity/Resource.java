package org.mitre.bch.cath.simulation.entity;

import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import static java.lang.Math.min;

/** Resource class provides a structure to store available resources, and methods to seize and release them.
 * @author H. Haven Liu, The MITRE Corporation
 */
public class Resource {
    //===== Static Attributes ======//
    /** Make object Serializable */
    private static final long serialVersionUID = 1L;

    /** Static Logger object */
    private static final Logger SLOGGER = LoggerFactory.getLogger(Resource.class);

    /** List of resource types */
    public enum ResourceType {STAFF, EQUIPMENT, PLACE}

    //===== Attributes ======//
    /** List of instances for this resource */
    public List<ResourceInstance> resourceInstances = new ArrayList<>();

    /** Name of the resource */
    public String name;

    /** Type of this resource */
    public ResourceType type;

    /** Resource assignment */
    public Map<ResourceInstance, Patient> inUseBy = new HashMap<>();

    //===== Constructors ======//
    /** Constructor of a Resource.
     * @param name resource name
     * @param type resource type
     */
    public Resource(String name, String type) {
        this.name = name;
        this.type = ResourceType.valueOf(type);
    }

    //===== Methods ======//
    /** Seize resource(s) for a patient.
     * @param model the CathLabSim instance that the resource instance exists in
     * @param patient the patient/case that seizes the resource
     * @param count the amount of resource
     * @param useOtherLab whether to request resource assigned to another lab
     * @return count of resources still needed
     */
    public int seize(CathLabSim model, Patient patient, int count, boolean useOtherLab) {
        int needed = count;
        List<ResourceInstance> resourcePool = new ArrayList<>();
        resourcePool.addAll(resourceInstances.stream().filter(r -> r.labAssigned == patient.lab && !r.inUse).toList());
        resourcePool.addAll(resourceInstances.stream().filter(r -> r.labAssigned == null && !r.inUse).toList());
        if (useOtherLab) {
            resourcePool.addAll(resourceInstances.stream().filter(r -> r.labAssigned != null &&
                    r.labAssigned != patient.lab && !r.inUse).toList());
        }

        for (int i = 0; i < min(count, resourcePool.size()); i++, needed --) {
            ResourceInstance r = resourcePool.get(i);
            r.inUse = true;
            r.seizedAt = model.schedule.getTime();
            r.inUseBy = patient;
            inUseBy.put(r, patient);
            patient.resources.add(r);
        }
        return needed;
    }

    /** Seize resource(s) for patient, not using resources from other labs.
     * @param model the CathLabSim instance that the resource instance exists in
     * @param patient the patient/case that seizes the resource
     * @param count the amount of resource
     * @return count of resources still needed
     */
    public int seize(CathLabSim model, Patient patient, int count) {
        return seize(model, patient, count, false);
    }

    /** Find how many resources are available for a patient.
     * @param lab the lab where the resource is to be used
     * @param useOtherLab whether to request resource assigned to another lab
     * @return the number of resources that are available
     */
    public int numAvailable(Lab lab, boolean useOtherLab) {
        List<ResourceInstance> resourcePool = new ArrayList<>();
        resourcePool.addAll(resourceInstances.stream().filter(r -> r.labAssigned == lab && !r.inUse).toList());
        resourcePool.addAll(resourceInstances.stream().filter(r -> r.labAssigned == null && !r.inUse).toList());
        if (useOtherLab) {
            resourcePool.addAll(resourceInstances.stream().filter(r -> r.labAssigned != null &&
                    r.labAssigned != lab && !r.inUse).toList());
        }
        return resourcePool.size();
    }

    /** Check the availability of resources for a patient.
     * @param model the CathLabSim instance of the simulation
     * @param lab the lab where the resource is to be used
     * @param count the amount of resource
     * @param useOtherLab whether to request resource assigned to another lab
     * @return boolean whether requested resources are available
     */
    public boolean isAvailable(CathLabSim model, Lab lab, int count, boolean useOtherLab) {
        int available = numAvailable(lab, useOtherLab);
        if (count > available) {
            model.LOGGER.info("Wanted {} of {}, only have {}", count, this.name, available);
        }
        return available >= count;
    }

    /** Check the availability of resources for a patient, not using resources from other labs.
     * @param model the CathLabSim instance of the simulation
     * @param lab the lab where the resource is to be used
     * @param count the amount of resource
     * @return boolean whether requested resources are available
     */
    public boolean isAvailable(CathLabSim model, Lab lab, int count) {
        return isAvailable(model, lab, count, false);
    }

    @Override
    public String toString() {
        return "Resource " + name;
    }

    /** Releases resources up to the given count from the patient.
     * @param day Integer day of the procedure which is releasing resources
     * @param model CathLabSim instance where the resources exist and are being released
     * @param patient the patient that releases the resource
     * @param count the amount of resource release by patient
     */
    public void release(Integer day, CathLabSim model, Patient patient, int count) {
        int toRelease = count;

        List<ResourceInstance> resourcesInUse = new ArrayList<>();
        resourcesInUse.addAll(resourceInstances.stream().filter(r -> r.labAssigned != null &&
                r.labAssigned != patient.lab && r.inUseBy == patient).toList());
        resourcesInUse.addAll(resourceInstances.stream().filter(r -> r.labAssigned == null &&
                r.inUseBy == patient).toList());
        resourcesInUse.addAll(resourceInstances.stream().filter(r -> r.labAssigned == patient.lab &&
                r.inUseBy == patient).toList());

        for (int i = 0; i < min(toRelease, resourcesInUse.size()); i++) {
            toRelease --;
            ResourceInstance r = resourcesInUse.get(i);
            r.inUse = false;
            model.metrics.addResourceUsage(day, r, model.schedule.getTime() - r.seizedAt);
            r.seizedAt = null;
            r.inUseBy = null;
            inUseBy.remove(r);
            patient.resources.remove(r);
        }
    }

    /** Release all resources in use for a patient.
     * @param day Integer day of the procedure which is releasing resources
     * @param model CathLabSim instance where the resources exist and are being released
     * @param patient the patient that releases the resource
     */
    public void releaseAll(Integer day, CathLabSim model, Patient patient) {
        Iterator<ResourceInstance> i = patient.resources.iterator();
        while (i.hasNext()) {
            ResourceInstance r = i.next();
            r.inUse = false;
            model.metrics.addResourceUsage(day, r, model.schedule.getTime() - r.seizedAt);
            r.seizedAt = null;
            r.inUseBy = null;
            inUseBy.remove(r);
            i.remove();
        }
    }

    //===== Nested Classes ======//
    /** The class for an instance of a Resource (type).
     */
    public class ResourceInstance implements Steppable {
        //===== Attributes ======//
        /** Name of the instance of resource */
        public String name;

        /** Resource type */
        public Resource type;

        /** The lab this resource is assigned to, with null indicating a floating resource */
        public Lab labAssigned;

        /** Whether this instance is currently in use */
        public boolean inUse = false;

        /** Time the resource was seized, null if not in use */
        public Double seizedAt = null;

        /** The patient currently using this resource */
        public Patient inUseBy;

        /** The availability schedule of the resource */
        public Map<Integer, ResourceSchedule> schedule = new HashMap<>();

        //===== Constructors ======//
        /** Constructor of a Resource Instance.
         * @param name name of resource
         */
        public ResourceInstance(String name) {
            this.name = name;
            this.type = Resource.this;
        }

        //===== Methods ======//
        @Override
        public String toString() {
            return "ResourceInstance{" +
                    "name='" + name + '\'' +
                    ", type=" + type +
                    '}';
        }

        /** Add a weekly schedule to the resource instance.
         * @param dayOfWeek day of week
         * @param lab       lab assigned
         * @param startTime start time in the day
         * @param endTime   end time in the day
         */
        public void addSchedule(int dayOfWeek, Lab lab, int startTime, int endTime){
            schedule.put(dayOfWeek, new ResourceSchedule(lab, startTime, endTime));
        }

        /** Add a floating weekly schedule to the resource instance.
         * @param dayOfWeek day of week
         * @param startTime start time in the day
         * @param endTime   end time in the day
         */
        public void addSchedule(int dayOfWeek, int startTime, int endTime){
            schedule.put(dayOfWeek, new ResourceSchedule(startTime, endTime));
        }

        /** Get the next time the resource is available.
         * @param time get next time resource is available after this time
         * @return next time the resource is available
         */
        public int nextScheduledTime(SimState simState, int time) {
            CathLabSim model = (CathLabSim) simState;
            int day = time / CathSchedule.MIN_PER_DAY;
            int dayOfWeek = model.startDate.plusDays(day).getDayOfWeek().getValue() - 1;
            int startOfWeek = (time / CathSchedule.MIN_PER_DAY / 7) * 7 * CathSchedule.MIN_PER_DAY;
            List<Integer> nextDays = new ArrayList<>();
            nextDays.addAll(schedule.keySet().stream().filter(d -> d > dayOfWeek).toList()); // later this week
            nextDays.addAll(schedule.keySet().stream().map(d -> d + 7).toList()); // next week
            return startOfWeek + nextDays.get(0) * CathSchedule.MIN_PER_DAY + schedule
                    .get(model.startDate.plusDays(nextDays.get(0)).getDayOfWeek().getValue() - 1).startTime;
        }

        /** Step function to manage/toggle the availability of a resource.
         * @param simState the CathLabSim instance of the simulation
         */
        @Override
        public void step(SimState simState) {
            CathLabSim model = (CathLabSim) simState;
            int tnow = (int)model.schedule.getTime();
            int day = tnow / CathSchedule.MIN_PER_DAY;
            int dayOfWeek = model.startDate.plusDays(day).getDayOfWeek().getValue() - 1;
            int timeOfDay = tnow % CathSchedule.MIN_PER_DAY;
            ResourceSchedule schedule = this.schedule.get(dayOfWeek);
            if (timeOfDay >= schedule.startTime && timeOfDay < schedule.endTime) {
                this.labAssigned = schedule.lab;
               model.scheduleOnce(day * CathSchedule.MIN_PER_DAY + schedule.endTime, this);
            } else if (timeOfDay >= schedule.endTime) {
                // If something better than 'null' for after-hours assignment, update that here.
                this.labAssigned = null;
               model.scheduleOnce((day + 1) * CathSchedule.MIN_PER_DAY + schedule.startTime, this);
            }
        }
    }

    /** Availability schedule and lab assignment of a resource. */
    private static class ResourceSchedule implements Serializable {
        //===== Attributes ======//
        /** Make object Serializable */
        @Serial
        private static final long serialVersionUID = 1L;

        /** The lab assigned */
        public Lab lab = null;

        /** The start time in the day */
        public Integer startTime;

        /** the end time in the day */
        public Integer endTime;

        //===== Constructor ======//
        /** Create a schedule with a start time and an end time with lab assignment.
         * @param lab the lab that the resource is assigned to
         * @param startTime starting time (in minute) for the resource
         * @param endTime ending time (in minute) for the resource
         */
        public ResourceSchedule(Lab lab, int startTime, int endTime) {
            this(startTime, endTime);
            this.lab = lab;
        }

        /** Create a floating schedule with a start time and an end time.
         * @param startTime starting time (in minute) for the resource
         * @param endTime ending time (in minute) for the resource
         */
        public ResourceSchedule(int startTime, int endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
