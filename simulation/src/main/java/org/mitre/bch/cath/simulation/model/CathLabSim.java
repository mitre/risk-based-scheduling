package org.mitre.bch.cath.simulation.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.cli.*;
import org.mitre.bch.cath.simulation.entity.Patient;
import org.mitre.bch.cath.simulation.utils.*;
import org.mlflow.api.proto.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sim.engine.SimState;
import sim.engine.Steppable;

import java.io.*;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.time.*;
import org.mlflow.tracking.*;
import org.mlflow.api.proto.Service.*;
import org.mlflow.tracking.MlflowClient;
import com.google.gson.JsonObject;

import static org.mitre.bch.cath.simulation.utils.FileHandler.fileToString;

public class CathLabSim extends SimState {
    /** CathSchedule instance */
    public CathSchedule cathSchedule;

    /** Metrics instance */
    public Metrics metrics;

    /** CathDistribution instance */
    public CathDistribution cathDistribution;

    /** EntityManager instance to manage various entities */
    public EntityManager entityManager;

    /** LoggerHelper instance for simulation logging */
    public LoggerHelper LOGGER;

    /** Gson reader instance */
    private static final Gson gson = new Gson();

    /** The static logger object */
    private static final Logger SLOGGER = LoggerFactory.getLogger(CathLabSim.class);

    /** The simulation event queue */
    public List<Event> eventQueue;

    /** How many days an add-on can be bumped/pushed, depending on urgency */
    public Map<Patient.Urgency, Integer> pushBumpMap = new HashMap<>();

    /** Whether push criteria apply to an add-on, depending on urgency */
    public Map<Patient.Urgency, Map<String, Boolean>> pushBooleansMap = new HashMap<>();

    /** Extra days the simulation will run past the last scheduled elective case */
    public int extraDays;

    /** The last day done in the simulation */
    public Integer simLastDay;

    /** Name of the folder where local log files are stored */
    public String logFolder;

    /** Whether add-on cases can be pushed */
    public boolean pushCases;

    /** The starting date of the simulation */
    public LocalDate startDate;

    /** Time of day labs can start cases, for the purpose of some metric calculations */
    public double startTime;

    /** Time of day labs would like to be finished by, and after which add-on cases can potentially be pushed */
    public double endTime;

    /** Time of day a lab could be considered finished early, for use in metrics */
    public double earlyEndTime;

    /** Threshold values for different pAE levels */
    public Map<String, Double> pAEThresholds = new HashMap<>();

    /** Threshold values for different pICU levels */
    public Map<String, Double> pICUThresholds = new HashMap<>();

    /** MlflowClient instance to manage Mlflow interactions */
    public MlflowClient mlflowClient;

    /** Mlflow run id for the simulation */
    public String mlflowRunId;

    /** Mlflow run id of the schedule being simulated */
    public String scheduleRunId;

    /** Name of the schedule being simulated */
    public String scheduleRunName;

    /** Add-on cases to pull from for arriving add-ons*/
    public List<Config.Schedule> addonBucket;

    /** Which add-on case push criteria are active */
    public Map<String, Boolean> pushCriteriaActive = new HashMap<>();

    /** Which criteria apply to which urgencies */
    public Map<String, List<String>> pushCriteriaUrgencies = new HashMap<>();

    /** Threshold values for various push criteria */
    public Map<String, Map<String, Double>> pushCriteriaValues = new HashMap<>();

    //===== Constructors ======//
    /** Constructor for a CathLabSim model.
     * @param seed random seed
     * @param scheduleRunId run id of an Mlflow schedule
     * @param folderName name of the folder to store local logs
     * @param extraDays how many days to run the simulation past the last elective case schedule day
     * @param configSchedule list of Config.Schedule objects, the elective cases
     * @param addonBucket list of Config.Schedule objects, the add-on cases
     * @param mlFlowManager mlFlowManager object for managing Mlflow interactions
     * @param configData possible uploaded config file as an object
     * @param configPath possible path to a config file to read from
     */
    public CathLabSim(long seed, String scheduleRunId, String folderName, int extraDays,
                      List<Config.Schedule> configSchedule, List<Config.Schedule> addonBucket,
                      MlFlowManager mlFlowManager, JsonObject configData, String configPath, String scheduleName) {
        super(seed);
        this.scheduleRunId = scheduleRunId;
        this.scheduleRunName = scheduleName == null ? getScheduleRunName(mlFlowManager, scheduleRunId) : scheduleName;
        this.logFolder = folderName;
        this.addonBucket = addonBucket;
        this.extraDays = extraDays;
        LOGGER = new LoggerHelper(this);
        eventQueue = new ArrayList<>();
        cathDistribution = new CathDistribution((int) seed, this.addonBucket);
        entityManager = new EntityManager();
        // populate distribution
        cathDistribution.populateDistributions();
        //===== Attributes ======//
        Config config;
        if (configData == null) {
            config = Config.readConfig(configPath, this);
        } else {
            config = Config.uploadConfig(configData, this);
        }
        if (config == null) {
            System.exit(1);
        }
        cathSchedule = new CathSchedule(this, configSchedule);
        LOGGER.info("allPatients size is {}", cathSchedule.allPatients.size());
        LOGGER.info("last patient is {}", cathSchedule.allPatients.get(cathSchedule.allPatients.size() - 1));
        LOGGER.info("simLastDay is {}", simLastDay);
        metrics = new Metrics(new ArrayList<>(entityManager.resourceMap.keySet()), seed, this);
        mlflowClient = mlFlowManager.client;
        mlflowRunId = mlFlowManager.startRun("iteration_" + seed);
    }

    //===== Methods ======//
    /** Main method of the simulation to run locally from the command line. */
    public static void main(String[] args) throws ParseException, IOException {
        Option option_n = Option.builder("n")
                .required(false)
                .desc("Number of iterations")
                .longOpt("num_iteration")
                .hasArg()
                .build();

        Option option_s = Option.builder("s")
                .required(true)
                .desc("Schedule name")
                .longOpt("schedule_name")
                .hasArg()
                .build();

        Option option_seed = Option.builder("d")
                .required(false)
                .desc("Starting seed")
                .longOpt("seed")
                .hasArg()
                .build();

        Option option_folder = Option.builder("f")
                .required(false)
                .desc("Folder suffix name")
                .longOpt("folder_name")
                .hasArg()
                .build();

        Option option_length = Option.builder("l")
                .required(false)
                .desc("Extra simulation days to run beyond day of last scheduled case")
                .longOpt("extra_days")
                .hasArg()
                .build();

        Option option_description = Option.builder("i")
                .required(false)
                .desc("Description of experiment.")
                .longOpt("description")
                .hasArg()
                .build();

        Option option_b = Option.builder("b")
                .required(false)
                .desc("File with add-on cases")
                .longOpt("addon_file")
                .hasArg()
                .build();

        Option option_exp = Option.builder("e")
                .required(false)
                .desc("Mlflow experiment name")
                .longOpt("experiment_name")
                .hasArg()
                .build();

        Option config_path = Option.builder("c")
                .required(false)
                .desc("Path to config json")
                .longOpt("config")
                .hasArg()
                .build();

        Option option_verbose = Option.builder("v")
                .required(false)
                .desc("Whether to save all logs and metrics")
                .longOpt("verbose")
                .hasArg()
                .build();

        final Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        options.addOption(option_n);
        options.addOption(option_s);
        options.addOption(option_seed);
        options.addOption(option_folder);
        options.addOption(option_length);
        options.addOption(option_description);
        options.addOption(option_b);
        options.addOption(option_exp);
        options.addOption(config_path);
        options.addOption(option_verbose);

        CommandLine cli = parser.parse(options, args);

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-kkmmss");
        String timeRun = dateFormat.format(date);

        int iterations = Integer.parseInt(cli.getOptionValue("n", "1"));
        String scheduleRunId = cli.getOptionValue("s");
        int startSeed = Integer.parseInt(cli.getOptionValue("d", "0"));
        int extraDays = Integer.parseInt(cli.getOptionValue("l", "0"));
        String description = cli.getOptionValue("i", "");
        String addonBucketFile = cli.getOptionValue("b");
        String expName = cli.getOptionValue("e", "Default");
        String configPath = cli.getOptionValue("c", "config.json");
        String folderName = cli.getOptionValue("f", expName + "_" + timeRun);
        boolean verbose = Boolean.parseBoolean(cli.getOptionValue("v", "false"));

        MlFlowManager mlFlowManager = new MlFlowManager("simulation");
        mlFlowManager.setParentRunName(expName);

        RunsPage scheduleRunResults;
        String scheduleRunName = null;
        Optional<Service.Experiment> exp = mlFlowManager.client.getExperimentByName("scheduler");
        String scheduleExpId;
        if (exp.isEmpty()) {
            scheduleExpId = mlFlowManager.client.createExperiment("scheduler");
        } else {
            scheduleExpId = exp.get().getExperimentId();
        }
        String filter = "run_id = '" + scheduleRunId + "'";
        scheduleRunResults =  mlFlowManager.client.searchRuns(new ArrayList<>(List.of(scheduleExpId)), filter,
                ViewType.ACTIVE_ONLY, 1);

        if (scheduleRunResults.getItems().isEmpty()) {
            String nameFilter = "attributes.run_name = '" + scheduleRunId + "'";
            scheduleRunResults = mlFlowManager.client.searchRuns(new ArrayList<>(List.of(scheduleExpId)), nameFilter,
                    ViewType.ACTIVE_ONLY, 1);
            scheduleRunName = scheduleRunId;
            scheduleRunId = scheduleRunResults.getItems().isEmpty() ? null : scheduleRunResults.getItems().get(0).getInfo().getRunId();
        }

        runSim(iterations, scheduleRunId, startSeed, folderName, expName, extraDays, description, addonBucketFile,
                mlFlowManager, null, configPath, verbose, scheduleRunName);
    }

    /** Runs the simulation for the specified number of iterations,
     * for each child schedule in the specified parent schedule.
     * Can be called from local or front-end, and saves results to Mlflow, possibly locally, and front-end if being used.
     * @param iterations how many simulation iterations to run on each schedule.
     * @param parentScheduleId Mlflow id of the parent schedule run
     * @param startSeed starting random seed
     * @param folderName local folder for log saving
     * @param expName name of the parent simulation experiment run in Mlflow
     * @param extraDays how many days to run each simulation past the last scheduled elective case
     * @param description description of the parent simulation run in Mlflow
     * @param addonBucketFile source of add-on cases
     * @param mlFlowManager mlFlowManager object for managing Mlflow interactions
     * @param configData possible uploaded config file as an object
     * @param configPath possible path to a config file to read from
     * @param verbose boolean whether to save full suite of metrics, artifacts, and logs, or only a smaller set
     * @param scheduleRunName name of the schedule being simulated
     */
    public static void runSim(int iterations, String parentScheduleId, int startSeed, String folderName,
                              String expName, int extraDays, String description, String addonBucketFile,
                              MlFlowManager mlFlowManager, JsonObject configData, String configPath, boolean verbose,
                              String scheduleRunName) throws IOException {
        mlFlowManager.startNestedRun();
        SLOGGER.info("args: n: {}, s: {}, d: {}, f: {}, e: {}, b: {}, l: {}, c: {}, v: {}", iterations,
                parentScheduleId, startSeed, folderName, expName, addonBucketFile, extraDays, configPath, verbose);
        SLOGGER.info("available processors: {}", Runtime.getRuntime().availableProcessors());

        mlFlowManager.client.logParam(mlFlowManager.parentRunId, "iterations", String.valueOf(iterations));
        mlFlowManager.client.logParam(mlFlowManager.parentRunId, "run_type", "simulation_aggregate");
        SLOGGER.info("LOGGED PARAMS");


        int startSeedNew = startSeed;

        List<String> childScheduleIDs = getChildScheduleIDs(mlFlowManager, parentScheduleId);
        for (String scheduleRunId : childScheduleIDs) {
            List<Config.Schedule> addonBucket = getAddonBucket(mlFlowManager, scheduleRunId, addonBucketFile);
            String scheduleFilepath = getScheduleFilepath(mlFlowManager, scheduleRunName, scheduleRunId);
            List<Config.Schedule> configSchedule = getConfigSchedule(scheduleFilepath, scheduleRunId);

            List<CompletableFuture<Void>> simIterations = IntStream.range(startSeedNew, iterations + startSeedNew).boxed().map(n ->
                    CompletableFuture.runAsync(() -> fakeMain(n, scheduleRunId, folderName, extraDays, configSchedule,
                                    addonBucket, addonBucketFile, mlFlowManager, configData, configPath, verbose, scheduleRunName))
                            .thenRun(() -> SLOGGER.info("CathLabSim - ({}) completed", n))
                            .exceptionally(
                                    e -> {
                                        SLOGGER.info("CathLabSim - ({}) Failed", n);
                                        e.printStackTrace();
                                        throw new RuntimeException(e);
                                    })
            ).toList();

            CompletableFuture
                    .allOf(simIterations.toArray(CompletableFuture[]::new))
                    .join();

            startSeedNew += iterations;
        }

        List<String> aggMetrics = new ArrayList<>();
        if (verbose) {
            aggMetrics.addAll(Arrays.asList("Days", "Total_High_pAE_Risk_Lab_Minutes",
                    "Total_Medium_pAE_Risk_Lab_Minutes", "Total_Low_pAE_Risk_Lab_Minutes",
                    "Total_High_pICU_Risk_Lab_Minutes", "Total_Medium_pICU_Risk_Lab_Minutes",
                    "Total_Low_pICU_Risk_Lab_Minutes", "MED_AE_Level_Count", "HIGH_AE_Level_Count",
                    "Total_AE_Count", "Days_With_Over_1_AEs_Count", "Days_With_Over_1_New_ICU_Admissions_Count",
                    "Bumped_Case_Count", "All_Cases_Sent_to_ICU_Count", "New_Cases_Sent_to_ICU_Count",
                    "Mult_Cases_Sent_to_ICU_Day_Count", "Weekdays_With_After_EOD_Count",
                    "After_Mondays_Count", "After_Tuesdays_Count", "After_Wednesdays_Count", "After_Thursdays_Count",
                    "After_Fridays_Count", "Addon_Time_After_Count", "Elective_Time_After_Count",
                    "Scheduled_Cases_Started_After_EOD_Count", "EMERGENCY_Addon_Cases_Started_After_EOD_Count",
                    "URGENT_Addon_Cases_Started_After_EOD_Count", "NORMAL_Addon_Cases_Started_After_EOD_Count",
                    "Total_Case_Count", "Total_Addon_Count", "Badly_Balanced_Weeks_Version_One_Count",
                    "Badly_Balanced_Weeks_Version_Two_Count", "Badly_Balanced_Weeks_Version_TwoPointOne",
                    "System_Avg_Daily_Cumulative_Time", "System_Avg_Daily_Time_After_EOD",
                    "System_Avg_Daily_Time_After_EOD_No_Zeros", "Bad_Week_Criteria_Counter_Time_After_Over2",
                    "Bad_Week_Criteria_Counter_Time_After_Over3",
                    "Bad_Week_Criteria_Counter_Min_High_Over400", "Bad_Week_Criteria_Counter_Min_High_Over600",
                    "Bad_Week_Criteria_Counter_Min_High_Over800", "Bad_Week_Criteria_Counter_Mult_ICU_Over0",
                    "Bad_Week_Criteria_Counter_Mult_ICU_Over1", "Bad_Week_Criteria_Counter_Mult_ICU_Over2",
                    "Bad_Week_Criteria_Counter_Early_End_Over0", "Bad_Week_Criteria_Counter_Early_End_Over1",
                    "Bad_Week_Criteria_Counter_Early_End_Over2", "Bad_Week_Criteria_Counter_Start_After_Over0",
                    "Bad_Week_Criteria_Counter_Start_After_Over1", "Bad_Week_Criteria_Counter_Start_After_Over2",
                    "Bad_Week_Criteria_Counter_Start_And_Run_Hour_Over_0",
                    "Bad_Week_Criteria_Counter_Start_And_Run_Hour_Over_1",
                    "Bad_Week_Criteria_Counter_Start_And_Run_Hour_Over_2",
                    "Bad_Week_Criteria_Counter_Ran_Half_Hour_Past_Over_1",
                    "Bad_Week_Criteria_Counter_Ran_Half_Hour_Past_Over_2",
                    "Bad_Week_Criteria_Counter_Ran_Hour_Past_Over_1",
                    "Bad_Week_Criteria_Counter_Ran_Hour_Past_Over_2"
            ));

            // can't get labs without a CathLabSim model, which isn't created at this point
            //      so this has to be hardcoded for now
            for (String l : new ArrayList<>(List.of("A", "B", "C", "D"))) {
                aggMetrics.add("Lab_" + l + "_Avg_Daily_Cumulative_Time");
                aggMetrics.add("Lab_" + l + "_Avg_Daily_Time_After_EOD");
                aggMetrics.add("After_Time_Lab_" + l + "_Mon_Count");
                aggMetrics.add("After_Time_Lab_" + l + "_Tues_Count");
                aggMetrics.add("After_Time_Lab_" + l + "_Wed_Count");
                aggMetrics.add("After_Time_Lab_" + l + "_Thurs_Count");
                aggMetrics.add("After_Time_Lab_" + l + "_Fri_Count");
            }
        } else {
            aggMetrics.addAll(Arrays.asList("Total_High_pAE_Risk_Lab_Minutes",
                    "Total_Medium_pAE_Risk_Lab_Minutes", "Total_Low_pAE_Risk_Lab_Minutes",
                    "Total_High_pICU_Risk_Lab_Minutes", "Total_Medium_pICU_Risk_Lab_Minutes",
                    "Total_Low_pICU_Risk_Lab_Minutes", "Total_AE_Count", "Bumped_Case_Count",
                    "Weekdays_With_After_EOD_Count", "Total_Case_Count", "Total_Addon_Count",
                    "System_Avg_Daily_Time_After_EOD", "New_Cases_Sent_to_ICU_Count"
            ));
        }

        logAggregate(mlFlowManager, aggMetrics, parentScheduleId, scheduleRunName);
        mlFlowManager.client.setTag(mlFlowManager.parentRunId, "description", description);
        mlFlowManager.client.logArtifacts(mlFlowManager.parentRunId, new File("src/main/resources/prob_dist"));
        if (configData != null) {
            // Overwrite config file with uploaded config data
            FileWriter file = new FileWriter("src/main/resources/config.json");
            file.write(String.valueOf(configData));
            file.close();
        }
        mlFlowManager.client.logArtifact(mlFlowManager.parentRunId, new File("src/main/resources/config.json"));
        mlFlowManager.client.setTerminated(mlFlowManager.parentRunId, Service.RunStatus.FINISHED);
    }

    /** Effectively the main method that runs the simulation.
     * @param seed the seed for this iteration
     * @param scheduleRunId Mlflow run id of the child schedule being simulated
     * @param folderName local folder where logs are saved
     * @param extraDays additional days to run the simulation past the last scheduled elective case
     * @param configSchedule list of Config.Schedule objects, the elective cases
     * @param addonBucket list of Config.Schedule objects, the add-on cases
     * @param addonBucketFile source of add-on cases
     * @param mlFlowManager mlFlowManager object for managing Mlflow interactions
     * @param configData possible uploaded config file as an object
     * @param configPath possible path to a config file to read from
     * @param verbose boolean whether to save full suite of metrics, artifacts, and logs, or only a smaller set
     */
    private static void fakeMain(int seed, String scheduleRunId, String folderName, Integer extraDays,
                                 List<Config.Schedule> configSchedule, List<Config.Schedule> addonBucket,
                                 String addonBucketFile, MlFlowManager mlFlowManager, JsonObject configData,
                                 String configPath, boolean verbose, String scheduleName) {
        CathLabSim model = new CathLabSim(seed, scheduleRunId, folderName, extraDays, configSchedule, addonBucket,
                mlFlowManager, configData, configPath, scheduleName);
        Metrics.logAllParams(model.mlflowClient, false, addonBucketFile, String.valueOf(model.earlyEndTime),
                String.valueOf(model.endTime), String.valueOf(extraDays), folderName, String.valueOf(model.pushCases),
                model.mlflowRunId, model.scheduleRunId, model.scheduleRunName, String.valueOf(seed),
                String.valueOf(model.startDate), String.valueOf(model.startTime));
        model.start();
        do {
            model.metrics.recordPoint(model);
            if (!model.schedule.step(model)) {
                break;
            }
        } while (!model.schedule.scheduleComplete());
        model.LOGGER.info("Day of last scheduled case was day {}", model.cathSchedule.allPatients.stream().filter(p ->
                !p.addon).mapToInt(patient -> patient.day == null ? patient.addonDay : patient.day).max()
                .orElseThrow(NoSuchElementException::new));
        model.LOGGER.info("Simulation ran to day {}", model.simLastDay);
        model.writeLogs(verbose);
        model.verify();
        model.finish();
    }

    /** Get the name of an Mlflow schedule run from the id.
     * @param mlFlowManager mlFlowManager instance that manages interactions with Mlflow
     * @param scheduleRunId run id of the schedule
     * @return name of the schedule run
     */
    public static String getScheduleRunName(MlFlowManager mlFlowManager, String scheduleRunId) {
        Optional<Service.Experiment> exp = mlFlowManager.client.getExperimentByName("scheduler");
        String scheduleExpId;
        if (exp.isEmpty()) {
            scheduleExpId = mlFlowManager.client.createExperiment("scheduler");
        } else {
            scheduleExpId = exp.get().getExperimentId();
        }
        String filter = "run_id = '" + scheduleRunId + "'";
        RunsPage scheduleRunResults = mlFlowManager.client.searchRuns(new ArrayList<>(List.of(scheduleExpId)), filter,
                ViewType.ACTIVE_ONLY, 1);
        // Nothing currently done if there are multiple schedules found
        return scheduleRunResults.getItems().isEmpty() ? null :
                scheduleRunResults.getItems().get(0).getInfo().getRunName();
    }

    /** Get the run ids of the child schedules for the specified parent schedule run in Mlflow.
     * @param mlFlowManager mlFlowManager instance that manages interactions with Mlflow
     * @param parentScheduleId run id of the parent schedule run
     * @return list of child schedule run ids
     */
    public static List<String> getChildScheduleIDs(MlFlowManager mlFlowManager, String parentScheduleId) {
        List<String> childScheduleIDs = new ArrayList<>();
        Optional<Service.Experiment> exp = mlFlowManager.client.getExperimentByName("scheduler");
        String scheduleExpId;
        if (exp.isEmpty()) {
            scheduleExpId = mlFlowManager.client.createExperiment("scheduler");
        } else {
            scheduleExpId = exp.get().getExperimentId();
        }
        String filter = "tags.run_type = 'scheduler_child' and tags.mlflow.parentRunId = '" + parentScheduleId + "'";
        RunsPage scheduleRunResults = mlFlowManager.client.searchRuns(new ArrayList<>(List.of(scheduleExpId)), filter,
                ViewType.ACTIVE_ONLY, 1000);
        if (scheduleRunResults.getItems().isEmpty()) {
            childScheduleIDs.add(parentScheduleId);
        } else {
            for (Run r : scheduleRunResults.getItems()) {
                String childScheduleID = r.getInfo().getRunId();
                childScheduleIDs.add(childScheduleID);
            }
        }
        return childScheduleIDs;
    }

    /** Get the filepath to a schedule, either locally or in Mlflow.
     * Tries to pull from Mlflow first before pulling from local.
     * @param mlFlowManager mlFlowManager instance that manages interactions with Mlflow
     * @param scheduleName name of the local schedule file, if applicable
     * @param scheduleRunId run id of the schedule to pull, if applicable
     * @return filepath to a schedule
     */
    public static String getScheduleFilepath(MlFlowManager mlFlowManager, String scheduleName, String scheduleRunId) {
        String scheduleFilepath;
        try {
            scheduleFilepath = mlFlowManager.client.downloadArtifacts(scheduleRunId)+"/schedule.json";
        } catch(Exception e) {
            scheduleFilepath = String.format("schedules/schedule%s.json",("_" + scheduleName));
        }
        return scheduleFilepath;
    }

    /** Get the schedule from Mlflow if possible, otherwise from local.
     * @param scheduleFilepath path to the schedule locally
     * @param scheduleRunId run id of the schedule in Mlflow
     * @return configSchedule object
     */
    public static List<Config.Schedule> getConfigSchedule(String scheduleFilepath, String scheduleRunId) {
        final Gson gson = new Gson();
        Type listType = new TypeToken<List<Config.Schedule>>(){}.getType();
        List<Config.Schedule> configSchedule = null;
        try {
            if (scheduleRunId != null) {
                BufferedReader br = new BufferedReader(new FileReader(scheduleFilepath));
                configSchedule = gson.fromJson(br, listType);
            } else {
                configSchedule = gson.fromJson(fileToString(scheduleFilepath), listType);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return configSchedule;
    }

    /** Get the filepath to an add-on case bucket, either locally or in Mlflow.
     * Tries to pull from Mlflow first, otherwise returns name of local file passed.
     * @param mlFlowManager mlFlowManager instance that manages interactions with Mlflow
     * @param scheduleRunId run id of the schedule to pull, if applicable
     * @param addonBucketFile name of local add-on bucket file
     * @return filepath to the add-on bucket source
     */
    public static String getAddonBucketFilepath(MlFlowManager mlFlowManager,
                                                String scheduleRunId, String addonBucketFile) {
        if (scheduleRunId != null) {
            List<Param> paramsList = mlFlowManager.client.getRun(scheduleRunId).getData().getParamsList();
            String caseListRunId = paramsList.stream().filter(p ->
                    "case_file".equals(p.getKey())).toList().get(0).getValue();
            return mlFlowManager.client.downloadArtifacts(caseListRunId)+"/addon_cases.json";
        }
        return addonBucketFile;
    }

    /** Get the add-on case bucket from Mlflow if possible, otherwise from local.
     * @param mlFlowManager mlFlowManager instance that manages interactions with Mlflow
     * @param scheduleRunId run id of the schedule to pull, if applicable
     * @param addonBucketFile name of local add-on bucket file
     * @return add-on case bucket
     */
    public static List<Config.Schedule> getAddonBucket(MlFlowManager mlFlowManager,
                                                       String scheduleRunId, String addonBucketFile) {
        String addonBucketFilepath = getAddonBucketFilepath(mlFlowManager, scheduleRunId, addonBucketFile);
        Type listType = new TypeToken<List<Config.Schedule>>(){}.getType();
        List<Config.Schedule> addonCaseBucket = null;
        try {
            if (scheduleRunId != null) {
                BufferedReader br = new BufferedReader(new FileReader(addonBucketFilepath));
                addonCaseBucket = gson.fromJson(br, listType);
            } else {
                addonCaseBucket = gson.fromJson(fileToString("prob_dist/" + addonBucketFile + ".json"),
                        listType);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return addonCaseBucket;
    }

    /** Pull metrics from all runs performed under a parentRunID and aggregate metrics into averages.
     * Also logs identifying params for the parent run.
     * @param mlFlowManager MlFlowManager that holds information like client and runIds
     * @param aggMetrics List of strings for which metrics to aggregate
     * @param parentScheduleId identifies the schedule in scheduler experiment
     */
    public static void logAggregate(MlFlowManager mlFlowManager, List<String> aggMetrics,
                                    String parentScheduleId, String scheduleRunName) {
        List<Metric> metricList = new ArrayList<>();
        String filter = "tags.mlflow.parentRunId = '" + mlFlowManager.parentRunId + "'";
        List<Service.Run>  mlflowRunResults = mlFlowManager.client.searchRuns(
                new ArrayList<>(List.of(mlFlowManager.expId)), filter, ViewType.ACTIVE_ONLY, 1000).getItems();
        for (String met: aggMetrics) {
            List<Double> metList = mlflowRunResults.stream().map(
                            r -> r.getData().getMetricsList().stream().filter(
                                            m -> m.getKey().equals(met))
                                    .toList().get(0).getValue())
                    .toList();
            double meanMet = metList.stream().mapToDouble(v -> v).average().orElse(0.0);
            double sdMet = Metrics.findStdDev(metList);
            double minMet = metList.stream().mapToDouble(v -> v).min().orElse(0.0);
            double maxMet = metList.stream().mapToDouble(v -> v).max().orElse(0.0);
            double medMet = findMedian(metList);
            List<Double> sorted = metList.stream().mapToDouble(v -> v).sorted().boxed().toList();
            int len = sorted.size();
            double q1Met = len == 1 ? medMet : findMedian(sorted.subList(0, len/2));
            double q3Met = len == 1 ? medMet : len % 2 == 1 ? findMedian(sorted.subList((len/2)+1, len)) :
                    findMedian(sorted.subList(len/2, len));

            metricList.add(Metric.newBuilder().setKey(met+"_Mean").setValue(meanMet)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey(met+"_SD").setValue(sdMet)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey(met + "_Min").setValue(minMet)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey(met + "_Max").setValue(maxMet)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey(met+"_Median").setValue(medMet)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey(met+"_Q1").setValue(q1Met)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
            metricList.add(Metric.newBuilder().setKey(met+"_Q3").setValue(q3Met)
                    .setTimestamp(Instant.now().toEpochMilli()).setStep(0).build());
        }

        int total = metricList.size();
        int batchMax = 1000;
        if (total <= batchMax) {
            mlFlowManager.client.logBatch(mlFlowManager.parentRunId, metricList, null, null);
        } else {
            List<List<Service.Metric>> metricLists = new ArrayList<>();
            int i = 0;
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
                mlFlowManager.client.logBatch(mlFlowManager.parentRunId, ml, null, null);
            }
        }

        List<String> paramList = new ArrayList<>(Arrays.asList("addon_bucket", "early_end_time", "end_time",
                "extra_days", "log_folder", "push_cases", "start_date", "start_time"));
        Map<String, String> params = new HashMap<>();
        for (String pa : paramList) {
            List<Param> paramVal = mlflowRunResults.get(0).getData().getParamsList().stream().filter(p ->
                    p.getKey().equals(pa)).toList();
            if (paramVal.size() > 0) {
                params.put(pa, paramVal.get(0).getValue());
            } else {
                params.put(pa, null);
            }
        }
        params.put("schedule_run_id", parentScheduleId);
        params.put("schedule", scheduleRunName == null ? getScheduleRunName(mlFlowManager,
                parentScheduleId) : scheduleRunName);

        Metrics.logAllParams(mlFlowManager.client, true, params.get("addon_bucket"), params.get("early_end_time"),
                params.get("end_time"), params.get("extra_days"), params.get("log_folder"), params.get("push_cases"),
                mlFlowManager.parentRunId, params.get("schedule_run_id"), params.get("schedule"),
                String.valueOf(mlflowRunResults.size()), params.get("start_date"), params.get("start_time"));
    }

    /** Find the median for a list of values.
     * @param valueList list of values for which to compute the median.
     * @return double for the median of the values
     */
    public static Double findMedian(List<Double> valueList) {
        double median;
        if (valueList.size() % 2 == 1) {
            median = valueList.stream().mapToDouble(v -> v).sorted().toArray()[valueList.size() / 2];
        } else {
            median = (valueList.stream().mapToDouble(v -> v).sorted().toArray()[valueList.size() / 2] +
                    valueList.stream().mapToDouble(v -> v).sorted().toArray()[(valueList.size() / 2) - 1]) / 2;
        }
        return median;
    }

    @Override
    public String toString() {
        return String.format("CathLabSim (seed=%d)", cathDistribution.seed);
    }

    @Override
    public void start() {
        super.start();
        this.schedule.scheduleOnce(cathSchedule);
    }

    @Override
    public void finish() {
        super.finish();
        mlflowClient.setTerminated(mlflowRunId, Service.RunStatus.FINISHED);
    }

    /** Write local csv log files, save more useful ones as artifacts in the MLFlow run, and save run-level MLFlow metrics.
     * @param verbose If false, only save the most useful metrics to MLFlow, nothing else. If true, save everything.
     */
    public void writeLogs(boolean verbose) {
        try {
            if (verbose) {
                metrics.writeToCSV();
                List<String> artifs = Arrays.asList("days", "patientLog", "patients");
                metrics.writeArifacts(artifs);
            }
            metrics.writeToMlflow(verbose);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Provide some verification of the results.
     * 1. Check and report any cases that don't start as a result of being scheduled for after the simulation end,
     * 2. otherwise, check all cases started and completed appropriately
     */
    private void verify() {
        // Ensure every patient has a non-null start and stop time
        cathSchedule.allPatients.forEach(p->{
            if (p.day > this.simLastDay && p.addon) {
                LOGGER.info("Patient {} is scheduled to day {}, after simulation ends, and is an add-on case",
                        p.pid, p.day);
            }
            else if (p.day > this.simLastDay) {
                LOGGER.error("Patient {} is scheduled to day {}, after simulation ends, but is a scheduled case",
                        p.pid, p.day);
                mlflowClient.setTerminated(mlflowRunId, RunStatus.FAILED);
                throw new VerificationException(String.format(
                        "Patient %d is scheduled to day %d, after simulation ends, but is a scheduled case",
                        p.pid, p.day));
            }
            else {
                if (p.tStart != null && p.tEnd == null) {
                    LOGGER.error("Patient {} starts, but has a null end time", p.pid);
                    mlflowClient.setTerminated(mlflowRunId, RunStatus.FAILED);
                    throw new VerificationException(String.format("Patient %d starts, but has a null end time", p.pid));
                }
                else if (p.tStart == null && p.tEnd != null) {
                    LOGGER.error("Patient {} never starts with a null start time, but has an end time", p.pid);
                    mlflowClient.setTerminated(mlflowRunId, RunStatus.FAILED);
                    throw new VerificationException(String.format(
                            "Patient %d never starts with a null start time, but has an end time", p.pid));
                }
                else if (p.tStart == null && p.addonDay + p.schedDelay + p.bumpDelay < this.simLastDay){
                    LOGGER.error("Patient {} has null start and end times", p.pid);
                    mlflowClient.setTerminated(mlflowRunId, RunStatus.FAILED);
                    throw new VerificationException(String.format("Patient %d has null start and end times", p.pid));
                }
            }
        });

        // Ensure all cases start on the day they should.
        //      If one starts sometime on the first day after it was supposed to, is considered ok,
        //      as it may have been at the end of a list of cases that ran on from the prior day.
            cathSchedule.allPatients.forEach(p -> {
                if (p.tStart != null) {
                    int tStartDay = (int) (p.tStart / CathSchedule.MIN_PER_DAY);
                    double minStart = 12*60;
                    boolean nextDayEarlyStart = p.tStart < (p.day + 1) * CathSchedule.MIN_PER_DAY + minStart;
                    boolean doneNextDay = p.tStart < (p.day + 2) * CathSchedule.MIN_PER_DAY;
                    if (!p.addon) {
                        if (p.day != tStartDay && !nextDayEarlyStart) {
                            LOGGER.error("Scheduled patient {} did not start on assigned day", p.pid);
                            throw new VerificationException(String.format(
                                    "Scheduled patient %d did not start on assigned day", p.pid));
                        }
                    } else {
                        if (p.bumpNum > this.pushBumpMap.get(p.urgency)) {
                            LOGGER.error("Add-on patient {} pushed/bumped too many times", p.pid);
                            throw new VerificationException(String.format(
                                    "Add-on patient %d pushed/bumped too many times", p.pid));
                        }
                        if (tStartDay - (p.schedDelay + p.bumpDelay) != p.addonDay && !doneNextDay) {
                            LOGGER.error("Add-on patient {} incorrectly started later than it was supposed to. " +
                                            "Was observed on day {}, scheduled {} days later, bumped a total of {} " +
                                            "days (potentially including weekends)," +
                                            "and thus should have started on day {}, but began on day {} at {}",
                                            p.pid, p.addonDay, p.schedDelay,
                                            p.bumpDelay, p.addonDay + p.schedDelay + p.bumpDelay, tStartDay,
                                            p.tStart % CathSchedule.MIN_PER_DAY);
                            throw new VerificationException(String.format("Add-on patient %d incorrectly started later " +
                                            "than it was supposed to. Was observed on day %d, scheduled %d days later, " +
                                            "bumped a total of %d days (potentially including weekends)," +
                                            "and thus should have started on day %d, but began on day %d at %f",
                                            p.pid, p.addonDay, p.schedDelay,
                                            p.bumpDelay, p.addonDay + p.schedDelay + p.bumpDelay, tStartDay,
                                            p.tStart % CathSchedule.MIN_PER_DAY));
                        } else if (tStartDay - (p.schedDelay + p.bumpDelay) != p.addonDay) {
                            LOGGER.info("Add-on patient {} incorrectly started on day after it was supposed to. " +
                                            "Was observed on day {}, scheduled {} days later, " +
                                            "bumped a total of {} days (potentially including weekends)," +
                                            "and thus should have started on day {}, but began on day {} at {}",
                                            p.pid, p.addonDay, p.schedDelay,
                                            p.bumpDelay, p.addonDay + p.schedDelay + p.bumpDelay, tStartDay,
                                            p.tStart % CathSchedule.MIN_PER_DAY);
                        }
                    }
                }
            });
    }

    /** Overriding sim.engine.Schedule.scheduleOnce with additional logging
     * @see sim.engine.Schedule#scheduleOnce(double, Steppable)
     */
    public void scheduleOnce(double time, Steppable event) {
        LOGGER.info(5, "Scheduling {} @ {} ({})", event.toString(), time, LoggerHelper.toTime(time));
        String scheduledBy = String.format("%s.%s (%d)", LOGGER.getClassMethodStack(3, false));
        Event evt = new Event(event, time, scheduledBy);
        if (eventQueue.contains(evt)) {
            LOGGER.info("*** Event Queue already contains this event {}! ***", evt);
        }
        else {
            eventQueue.add(evt);
            this.schedule.scheduleOnce(time, event);
        }
    }

    /** Overriding sim.engine.Schedule.scheduleOnce with additional logging
     * @see sim.engine.Schedule#scheduleOnce(Steppable)
     */
    public void scheduleOnce(double time, Steppable event, boolean printStack) {
        LOGGER.info(5, "Scheduling {} @ {} ({})", event.toString(), time, LoggerHelper.toTime(time));
        String scheduledBy = String.format("%s.%s (%d)", LOGGER.getClassMethodStack(3, false));
        Event evt = new Event(event, time, scheduledBy);
        if (eventQueue.contains(evt)) {
            LOGGER.info("*** Event Queue already contains this event {}! ***", evt);
        }
        else {
            eventQueue.add(evt);
            this.schedule.scheduleOnce(time, event);
        }
        if (printStack) LOGGER.stack();
    }

    /** Overriding sim.engine.Schedule.scheduleOnce with additional logging
     * @see sim.engine.Schedule#scheduleOnce(Steppable)
     */
    public void scheduleOnce(Steppable event) {
        LOGGER.info(5, "Scheduling {} in +1.0 @ {} ({})", event.toString(),
                this.schedule.getTime() + 1.0, LoggerHelper.toTime(this.schedule.getTime() + 1.0));
        String scheduledBy = String.format("%s.%s (%d)", LOGGER.getClassMethodStack(3, false));
        Event evt = new Event(event, this.schedule.getTime() + 1.0, scheduledBy);
        if (eventQueue.contains(evt)) {
            LOGGER.info("*** Event Queue already contains this event {}! ***", evt);
        }
        else {
            eventQueue.add(evt);
            this.schedule.scheduleOnce(event);
        }
    }

    /** Overriding sim.engine.Schedule.scheduleOnce with additional logging
     * @see sim.engine.Schedule#scheduleOnceIn(double, Steppable)
     */
    public void scheduleOnceIn(double delta, Steppable event) {
        LOGGER.info(5, "Scheduling {} in {} @ {} ({})", event.toString(), delta,
                this.schedule.getTime() + delta, LoggerHelper.toTime(this.schedule.getTime() + delta));
        String scheduledBy = String.format("%s.%s (%d)", LOGGER.getClassMethodStack(3, false));
        Event evt = new Event(event, this.schedule.getTime() + delta, scheduledBy);
        if (eventQueue.contains(evt)) {
            LOGGER.info("*** Event Queue already contains this event {}! ***", evt);
        }
        else {
            eventQueue.add(evt);
            this.schedule.scheduleOnceIn(delta, event);
        }
    }
}
