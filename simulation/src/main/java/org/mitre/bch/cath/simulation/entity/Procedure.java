package org.mitre.bch.cath.simulation.entity;

import org.jetbrains.annotations.NotNull;
import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.utils.Config;
import org.mitre.bch.cath.simulation.utils.EntityManager;
import org.mitre.bch.cath.simulation.utils.LoggerHelper;
import org.mitre.bch.cath.simulation.utils.VerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/** Procedure class captures a procedure with its steps, which also contains resources needed.
 * @author H. Haven Liu, The MITRE Corporation
 */
public class Procedure implements Serializable {
    //===== Static Attributes ======//
    /** Make object Serializable */
    @Serial
    private static final long serialVersionUID = 1L;

    /** The static logger object */
    private static final Logger SLOGGER = LoggerFactory.getLogger(Procedure.class);

    //===== Attributes ======//
    /** A unique procedure identifier */
    public int id;

    /** Procedure name */
    public String name;

    /** Lab type required for this procedure */
    public Lab.LabType labType;

    /** The list of steps (and required resources for this procedure */
    public List<Procedure.Step> steps = new ArrayList<>();

    //===== Constructors ======//
    /** Constructor from a Config.Procedure instance that came from parsing of the config file.
     * @param cathLabSim the CathLabSim instance of the simulation
     * @param procedure  an instance of the Config.Procedure
     * @param steps      a list of steps, to determine step order
     */
    public Procedure (CathLabSim cathLabSim, Config.Procedure procedure, List<Config.Step> steps) {
        LoggerHelper LOGGER = cathLabSim.LOGGER;
        this.id = procedure.id;
        this.name = procedure.name;
        this.labType = Lab.LabType.valueOf(procedure.labType);
        for (String pStep: procedure.steps.keySet()) {
            Step step;
            List<Config.Step> stepCandidates = steps.stream().filter(s -> s.name.equals(pStep)).toList();
            if (stepCandidates.size() != 1) {
                // This should never happen, steps are checked on read in, in Config.java
                LOGGER.error("There is no step called {}", pStep);
            }
            step = new Step(stepCandidates.get(0).id, stepCandidates.get(0).type, pStep);
            for (String stepResource: procedure.steps.get(pStep).resources.keySet()) {
                step.addResource(cathLabSim.entityManager, stepResource,
                        procedure.steps.get(pStep).resources.get(stepResource));
            }
            for (String stepResource: procedure.steps.get(pStep).rescueResources.keySet()) {
                step.addRescueResource(cathLabSim.entityManager, stepResource,
                        procedure.steps.get(pStep).rescueResources.get(stepResource));
            }
            this.addStep(step);
        }
    }

    //===== Methods ======//
    /** Add a step to the current procedure.
      * @param step a Step instance, including resources needed
     */
    private void addStep(Step step){
        this.steps.add(step);
    }

    /** Check if a given step is the last one.
     * @param step if this is the last step
     * @return if this is the last step
     */
    public boolean hasStepAfter(Step step) {
        int currentStepIndex = steps.indexOf(step);
        return steps.size() > currentStepIndex + 1;
    }

    /** Retrieve the subsequent step.
     * @param step the step for which the subsequent step is retrieved
     * @return the subsequent step
     */
    public Step getStepAfter(Step step) {
        int currentStepIndex = steps.indexOf(step);
        if (steps.size() > currentStepIndex + 1) {
            return steps.get(currentStepIndex + 1);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Procedure " + name;
    }

    //===== SubClasses ======//
    /** Procedure Step class captures a step in a procedure. The Step class also contains resources needed during
     * both the normal performance of the step and the rescue if an AE occurs at this step.
     * @author H. Haven Liu, The MITRE Corporation
     */
    public static class Step implements Comparable<Step>, Serializable {
        //===== Attributes ======//
        /** Make Step serializable */
        @Serial
        private static final long serialVersionUID = 1L;

        /** Step unique identifier */
        public int id;

        /** The patient state where this step occurs */
        public Patient.Status type;

        /** Step name */
        public String name;

        /** Resources required in the normal performance of the step */
        public Map<Resource, Integer> resources = new HashMap<>();

        /** Resources required in the AE rescue phase of the step */
        public Map<Resource, Integer> rescueResources = new HashMap<>();

        //===== Constructors ======//
        /** Constructor of a Step.
         * @param id Step unique identifier
         * @param type Patient state where this step occurs
         * @param name Step name
         */
        public Step(int id, String type, String name) {
            this.id = id;
            this.type = Patient.Status.valueOf(type);
            this.name = name;
        }

        //===== Methods ======//
        /** Add a resource to the step.
         * @param entityManager Entity Manager instance
         * @param resourceName  the name of the resource. The name of the resource should be
         *                      a key in  Resource.resourceMap
         * @param needed        count of the resources needed
         */
        public void addResource(EntityManager entityManager, String resourceName, Integer needed) {
            this.resources.put(entityManager.resourceMap.get(resourceName), needed);
        }

        /** Add a resource to the step if an AE occurs.
         * @param entityManager Entity Manager instance
         * @param resourceName  the name of the resource. The name of the resource should be
         *                      a key in  Resource.resourceMap
         * @param needed        count of the resources needed
         */
        public void addRescueResource(EntityManager entityManager, String resourceName, Integer needed) {
            this.rescueResources.put(entityManager.resourceMap.get(resourceName), needed);
        }

        /** Check if all resources for the procedure are available for a lab.
         * @param model the CathLabSim instance of the simulation
         * @param lab the lab to check the resources
         * @return whether all resources are available.
         */
        public boolean areResourcesAvailable(CathLabSim model, Lab lab) {
            Set<Resource> stepResources = this.resources.keySet();
            boolean available = true;
            for (Resource r : stepResources) {
                available = available && r.isAvailable(model, lab, this.resources.get(r));
            }
            return available;
        }

        @Override
        public int compareTo(@NotNull Step o) {
            return Integer.compare(this.id, o.id);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Procedure key) {
                return this.id == key.id;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.id + " " + this.name).hashCode();
        }
    }
}
