package org.mitre.bch.cath.simulation.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.Nullable;
import org.mitre.bch.cath.simulation.model.CathLabSim;
import org.mitre.bch.cath.simulation.model.CathSchedule;
import org.mitre.bch.cath.simulation.entity.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mitre.bch.cath.simulation.utils.FileHandler.fileToString;

/**
 * Config class to read the config file
 */
public class Config implements Serializable {

    //===== Attributes ======//
    private static final Gson gson = new Gson();
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);
    private List<Lab> labs;
    private List<Procedure> procedures;
    private List<Step> steps; // used to order steps in the case
    private List<Resource> resources;
    private List<PushParam> pushing;
    private boolean pushCases;
    private String startDate;
    private double startTime;
    private double endTime;
    private double earlyEndTime;
    private Map<String, Double> pAEThresholds = new HashMap<>();
    private Map<String, Double> pICUThresholds = new HashMap<>();
    private List<PushCriterion> pushCriteria;

    //===== Constructor ======//
    private Config() {}

    //===== Methods ======//

    /** Read config from file
     *
     * @param fileName name of config file
     * @param cathLabSim CathLabSim instance of the simulation
     * @return the config object
     */
    @Nullable
    public static Config readConfig(String fileName, CathLabSim cathLabSim)  {

        Config config = null;
        try {
            config = gson.fromJson(fileToString(fileName), Config.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!verifier(config)){

            return null;
        }
        fromConfig(config, cathLabSim);
        return config;
    }

    public static Config uploadConfig(JsonObject configData, CathLabSim cathLabSim) {

        Config config = null;
        config = gson.fromJson(configData, Config.class);
        if (!verifier(config)){

            return null;
        }
        fromConfig(config, cathLabSim);
        return config;
    }

    /** Populate simulation objects from config file
     *
     * @param config the Config object to populate simulation objects with
     * @param cathLabSim the CathLabSim instance of the simulation
     */
    private static void fromConfig(Config config, CathLabSim cathLabSim) {
        // set start date, lab times, and other simulation attributes
        DateTimeFormatter dateFormat = DateTimeFormatter.ISO_LOCAL_DATE;
        cathLabSim.startDate = LocalDate.parse(config.startDate, dateFormat);
        LOGGER.info("Sim start date is {}", cathLabSim.startDate);
        cathLabSim.startTime = 60 * config.startTime;
        cathLabSim.endTime = 60 * config.endTime;
        cathLabSim.earlyEndTime = 60 * config.earlyEndTime;
        cathLabSim.pushCases = config.pushCases;
        cathLabSim.pAEThresholds.putAll(config.pAEThresholds);
        cathLabSim.pICUThresholds.putAll(config.pICUThresholds);

        // instantiate labs
        for (Lab lab: config.labs) {
            cathLabSim.entityManager.labMap.put(lab.id, new org.mitre.bch.cath.simulation.entity.Lab(lab));
        }

        // instantiate resources
        for (Resource resource: config.resources){
            org.mitre.bch.cath.simulation.entity.Resource r = new org.mitre.bch.cath.simulation.entity.Resource(resource.name, resource.type);
            cathLabSim.entityManager.resourceMap.put(resource.name, r);

            if (resource.instances != null){ // with schedule
                for (ResourceInstance ri: resource.instances) {
                    org.mitre.bch.cath.simulation.entity.Resource.ResourceInstance resourceInstance = r.new ResourceInstance(ri.name);
                    r.resourceInstances.add(resourceInstance);
                    for (ResourceInstanceSchedule ris: ri.schedule) {
                        if (ris.lab > -1) {
                            resourceInstance.addSchedule(ris.dayOfWeek, cathLabSim.entityManager.labMap.get(ris.lab), ris.startTime, ris.endTime);
                        } else {
                            resourceInstance.addSchedule(ris.dayOfWeek, ris.startTime, ris.endTime);
                        }
                    }

                    cathLabSim.scheduleOnce(resourceInstance.nextScheduledTime(cathLabSim, 0), resourceInstance);
                }
            } else if (resource.total > 0) { // with simple total
                for (int i = 0; i < resource.total; i++) {
                    String resourceName = resource.name + " " + i;
                    org.mitre.bch.cath.simulation.entity.Resource.ResourceInstance resourceInstance = r.new ResourceInstance(resourceName);
                    r.resourceInstances.add(resourceInstance);
                    for (int j = 0; j < 7; j++) {
                        resourceInstance.addSchedule(j, 0, CathSchedule.MIN_PER_DAY);
                    }
                }
            } else {
                //TODO fail
            }
        }

        // instantiate procedures
        for (Procedure procedure: config.procedures) {
            cathLabSim.entityManager.procedureMap.put(procedure.id, new org.mitre.bch.cath.simulation.entity.Procedure(cathLabSim, procedure, config.steps));
        }

        // set pushing parameters for urgency levels
        for (PushParam pushParam: config.pushing) {
            cathLabSim.pushBumpMap.put(Patient.Urgency.valueOf(pushParam.urgency), pushParam.bumpThresh);
            Map<String, Boolean> tempPushMap = new HashMap<>();
            for (String key: pushParam.pushBooleans.keySet()) {
                tempPushMap.put(key, pushParam.pushBooleans.get(key));
            }
            cathLabSim.pushBooleansMap.put(Patient.Urgency.valueOf(pushParam.urgency), tempPushMap);
        }

        // Read push criteria
        for (PushCriterion criterion: config.pushCriteria) {
            cathLabSim.pushCriteriaActive.put(criterion.name, criterion.active);
            cathLabSim.pushCriteriaUrgencies.put(criterion.name, criterion.caseLevels);
            Map<String, Double> tempThresholds = new HashMap<>();
            for (String key: criterion.thresholds.keySet()) {
                tempThresholds.put(key, criterion.thresholds.get(key));
            }
            cathLabSim.pushCriteriaValues.put(criterion.name, tempThresholds);
        }
    }

    /** Printing error in config reading
     *
     * @param message error message
     * @return false when error occurs
     */
    private static boolean error(String message) {
        StackTraceElement el = Thread.currentThread().getStackTrace()[1];
        LOGGER.error("{} {}({}) - {}", Config.class.getName(), el.getMethodName(), el.getLineNumber(), message);
        return false;
    }

    /** Verify all conditions needed are provided in the config
     *
     * @param config the Config object
     * @return whether the Config is verified
     */
    private static boolean verifier(Config config) {
        // labType for labs and procedures in Enum LabType
        for (Lab lab: config.labs) {
            if (!EnumUtils.isValidEnum(org.mitre.bch.cath.simulation.entity.Lab.LabType.class, lab.labType)) {
                return error("lab.labType not in Enum LabType: " + lab.labType);
            }
        }
        for (Procedure procedure: config.procedures) {
            if (!EnumUtils.isValidEnum(org.mitre.bch.cath.simulation.entity.Lab.LabType.class, procedure.labType)) {
                return error("procedure.labType not in Enum LabType: " + procedure.labType);
            }
        }

        // procedureStep in steps
        // procedure step resource in resources
        List<String> stepNames = config.steps.stream().map(s -> s.name).collect(Collectors.toList());
        List<String> resourceNames = config.resources.stream().map(s -> s.name).collect(Collectors.toList());
        for (Procedure p: config.procedures) {
            for (String s: p.steps.keySet()) {
                if (!stepNames.contains(s)) {
                    return error("procedure.step not in steps: " + s);
                }

                for (String r: p.steps.get(s).resources.keySet()) {
                    if (!resourceNames.contains(r)) {
                        return error("procedure.step.resource not in resources: " + r);
                    }
                }

                for (String r: p.steps.get(s).rescueResources.keySet()) {
                    if (!resourceNames.contains(r)) {
                        return error("procedure.step.resource not in rescueResources: " + r);
                    }
                }
            }
        }

        // resource type in enum Resource.Type
        for (Resource resource: config.resources) {
            if (!EnumUtils.isValidEnum(org.mitre.bch.cath.simulation.entity.Resource.ResourceType.class, resource.type)) {
                return error("resource.type not in Enum ResourceType: " + resource.type);
            }
        }

        return true;
    }

    //===== SubClass ======//
    /** Config Lab class */
    public static class Lab implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public String name;
        public String labType;
        public List<Integer> weekdays;
        public List<Integer> preferred;
    }

    /** Config Resource class */
    public static class Resource implements Serializable {
        private static final long serialVersionUID = 1L;
        public String type;
        public String name;
        public int total;
        public List<ResourceInstance> instances;
    }
    /** Config Resource Instance class */
    public static class ResourceInstance implements Serializable {
        private static final long serialVersionUID = 1L;
        public String name;
        public List<ResourceInstanceSchedule> schedule;
    }
    /** Config ResourceInstanceSchedule class */
    public static class ResourceInstanceSchedule implements Serializable {
        private static final long serialVersionUID = 1L;
        public int dayOfWeek;
        public int lab;
        public int startTime;
        public int endTime;
    }
    /** Config (Procedure) Step class */
    public static class Step implements Serializable{
        private static final long serialVersionUID = 1L;
        public int id;
        public String type;
        public String name;
    }
    /** Config Procedure class */
    public static class Procedure implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public String name;
        public String labType;
        public HashMap<String, ProcedureStep> steps;
    }

    /** Config ProcedureStep class */
    public static class ProcedureStep implements Serializable{
        private static final long serialVersionUID = 1L;
        public HashMap<String, Integer> resources;
        public HashMap<String, Integer> rescueResources;
    }
    /** Config (Patient) Schedule class */
    public static class Schedule implements Serializable{
        private static final long serialVersionUID = 1L;
        public Integer day;
        public Integer lab;
        public Integer procedure;
        public Integer adverseScore;
        public Integer riskScore;
        public float pICU;
        public boolean addon;
        public String priorLocation;
        public Integer durationScore;
    }
    /** Config PushParam class */
    public static class PushParam {
        public String urgency;
        public Integer bumpThresh;
        public HashMap<String, Boolean> pushBooleans;
    }
    /** Config PushCriterion class */
    public static class PushCriterion {
        public String name;
        public boolean active;
        public List<String> caseLevels;
        public Map<String, Double> thresholds;
    }
}
