package org.mitre.bch.cath.simulation.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.gson.*;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.javalin.Javalin;
import org.mitre.bch.cath.simulation.utils.MlFlowManager;
import org.mlflow.api.proto.Service;
import org.mlflow.tracking.RunsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.mitre.bch.cath.simulation.utils.FileHandler.fileToString;

public class API {

    private static final int PORT = 7000;
    private static Logger logger = LoggerFactory.getLogger(API.class);

    public static void apiServer() {


        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(it -> {
                    it.reflectClientOrigin = true;
                });
            });
        });

        JobManager jobManager = new JobManager();
        MlFlowManager mlFlowManager = new MlFlowManager("simulation");

        // submit job to job manager queue
        app.post("/submit", ctx -> {
            String inputString = ctx.body();
            InputArgs inputArgs = new Gson().fromJson(inputString, InputArgs.class);
            int jobId = jobManager.submit(inputArgs);
            ctx.json(jobId);
            jobManager.runNextJob(mlFlowManager);
        });

        // query simulation runs from mlflow_db
        app.get("/get-sim-runs", ctx -> {
            List<RunColumns> simulationRuns = getSimulationRuns(ctx, mlFlowManager, jobManager);
            String simRunsJson = new Gson().toJson(simulationRuns);
            ctx.json(simRunsJson);
        });

        // query simulation run data from mlflow_db
        app.get("/get-sim-run", ctx -> {
            String runId = ctx.queryParamAsClass("runId", String.class).get();
            Service.Run simulationRun = mlFlowManager.client.getRun(runId);
            String results = new Gson().toJson(simulationRun);
            ctx.json(results);
        });


        // query & process simulation run data from mlflow_db
        app.get("/get-sim-run-details", ctx -> {
            String runId = ctx.queryParamAsClass("runId", String.class).get();
            Service.Run simulationRun = mlFlowManager.client.getRun(runId);
            String scheduleName = simulationRun.getData().getParamsList().stream().filter(param -> param.getKey().equals("schedule")).toList().isEmpty() ? null : simulationRun.getData().getParamsList().stream().filter(param -> param.getKey().equals("schedule")).map(Service.Param::getValue).toList().get(0);

            Double meanAddonCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Addon_Count_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Addon_Count_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanTotalCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Case_Count_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Case_Count_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanElectiveCount = meanTotalCount - meanAddonCount;

            Double meanWeekdaysAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanSysAvgDailyTimeAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Mean")).map(Service.Metric::getValue).toList().get(0);

            Double meanTotalLowPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanTotalMedPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanTotalHighPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Mean")).map(Service.Metric::getValue).toList().get(0);

            Double meanTotalLowPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanTotalMedPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanTotalHighPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Mean")).map(Service.Metric::getValue).toList().get(0);

            Double meanBumpedCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Bumped_Case_Count_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Bumped_Case_Count_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanNewCasesSentICU = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("New_Cases_Sent_to_ICU_Count_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("New_Cases_Sent_to_ICU_Count_Mean")).map(Service.Metric::getValue).toList().get(0);
            Double meanTotalAECount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_AE_Count_Mean")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_AE_Count_Mean")).map(Service.Metric::getValue).toList().get(0);

            List<RunMetrics> childRuns = getChildRuns(mlFlowManager, runId);

            Double minLowPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1LowPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianLowPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3LowPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxLowPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes_Max")).map(Service.Metric::getValue).toList().get(0);

            Double minMediumPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1MediumPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianMediumPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3MediumPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxMediumPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes_Max")).map(Service.Metric::getValue).toList().get(0);

            Double minHighPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1HighPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianHighPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3HighPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxHighPicuRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes_Max")).map(Service.Metric::getValue).toList().get(0);

            List<Integer> lowPicuRiskLabMinMetrics = Arrays.asList(minLowPicuRiskLabMin.intValue(), Q1LowPicuRiskLabMin.intValue(), medianLowPicuRiskLabMin.intValue(), Q3LowPicuRiskLabMin.intValue(), maxLowPicuRiskLabMin.intValue());
            List<Integer> mediumPicuRiskLabMinMetrics = Arrays.asList(minMediumPicuRiskLabMin.intValue(), Q1MediumPicuRiskLabMin.intValue(), medianMediumPicuRiskLabMin.intValue(), Q3MediumPicuRiskLabMin.intValue(), maxMediumPicuRiskLabMin.intValue());
            List<Integer> highPicuRiskLabMinMetrics = Arrays.asList(minHighPicuRiskLabMin.intValue(), Q1HighPicuRiskLabMin.intValue(), medianHighPicuRiskLabMin.intValue(), Q3HighPicuRiskLabMin.intValue(), maxHighPicuRiskLabMin.intValue());

            List<BoxPlotData> boxplotDataPicu = Arrays.asList(new BoxPlotData("Low", lowPicuRiskLabMinMetrics), new BoxPlotData("Medium", mediumPicuRiskLabMinMetrics), new BoxPlotData("High", highPicuRiskLabMinMetrics));

            Double minLowPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1LowPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianLowPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3LowPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxLowPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes_Max")).map(Service.Metric::getValue).toList().get(0);

            Double minMediumPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1MediumPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianMediumPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3MediumPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxMediumPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes_Max")).map(Service.Metric::getValue).toList().get(0);

            Double minHighPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1HighPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianHighPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3HighPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxHighPaeRiskLabMin = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes_Max")).map(Service.Metric::getValue).toList().get(0);

            List<Integer> lowPaeRiskLabMinMetrics = Arrays.asList(minLowPaeRiskLabMin.intValue(), Q1LowPaeRiskLabMin.intValue(), medianLowPaeRiskLabMin.intValue(), Q3LowPaeRiskLabMin.intValue(), maxLowPaeRiskLabMin.intValue());
            List<Integer> mediumPaeRiskLabMinMetrics = Arrays.asList(minMediumPaeRiskLabMin.intValue(), Q1MediumPaeRiskLabMin.intValue(), medianMediumPaeRiskLabMin.intValue(), Q3MediumPaeRiskLabMin.intValue(), maxMediumPaeRiskLabMin.intValue());
            List<Integer> highPaeRiskLabMinMetrics = Arrays.asList(minHighPaeRiskLabMin.intValue(), Q1HighPaeRiskLabMin.intValue(), medianHighPaeRiskLabMin.intValue(), Q3HighPaeRiskLabMin.intValue(), maxHighPaeRiskLabMin.intValue());
            List<BoxPlotData> boxplotDataPae = Arrays.asList(new BoxPlotData("Low", lowPaeRiskLabMinMetrics), new BoxPlotData("Medium", mediumPaeRiskLabMinMetrics), new BoxPlotData("High", highPaeRiskLabMinMetrics));

            Double minWeekdaysAfterEODCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1WeekdaysAfterEODCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianWeekdaysAfterEODCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3WeekdaysAfterEODCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxWeekdaysAfterEODCount = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count_Max")).map(Service.Metric::getValue).toList().get(0);

            List<Integer> weekdaysAfterEODCountMetrics = Arrays.asList(minWeekdaysAfterEODCount.intValue(), Q1WeekdaysAfterEODCount.intValue(), medianWeekdaysAfterEODCount.intValue(), Q3WeekdaysAfterEODCount.intValue(), maxWeekdaysAfterEODCount.intValue());
            List<BoxPlotData> boxplotDataWeekdaysAfterEODCountMetrics = Arrays.asList(new BoxPlotData("", weekdaysAfterEODCountMetrics));

            Double minSysAvgDailyMinAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Min")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Min")).map(Service.Metric::getValue).toList().get(0);
            Double Q1SysAvgDailyMinAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Q1")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Q1")).map(Service.Metric::getValue).toList().get(0);
            Double medianSysAvgDailyMinAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Median")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Median")).map(Service.Metric::getValue).toList().get(0);
            Double Q3SysAvgDailyMinAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Q3")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Q3")).map(Service.Metric::getValue).toList().get(0);
            Double maxSysAvgDailyMinAfterEOD = simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Max")).toList().isEmpty() ? null : simulationRun.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD_Max")).map(Service.Metric::getValue).toList().get(0);

            List<Integer> sysAvgDailyMinAfterEODMetrics = Arrays.asList(minSysAvgDailyMinAfterEOD.intValue(), Q1SysAvgDailyMinAfterEOD.intValue(), medianSysAvgDailyMinAfterEOD.intValue(), Q3SysAvgDailyMinAfterEOD.intValue(), maxSysAvgDailyMinAfterEOD.intValue());
            List<BoxPlotData> boxplotDataSysAvgDailyMinAfterEODMetricsMetrics = Arrays.asList(new BoxPlotData("", sysAvgDailyMinAfterEODMetrics));


            Gson gson = new Gson();
            Map<String, Object> results = new LinkedHashMap<>();
            results.put("parentRunInfo", simulationRun.getInfo());
            results.put("schedule", scheduleName);
            results.put("meanAddonCount", Math.round(meanAddonCount * 100d) / 100d);
            results.put("meanElectiveCount", Math.round(meanElectiveCount * 100d) / 100d);
            results.put("meanWeekdaysAfterEOD", Math.round(meanWeekdaysAfterEOD * 100d) / 100d);
            results.put("meanSysAvgDailyTimeAfterEOD", Math.round(meanSysAvgDailyTimeAfterEOD * 100d) / 100d);
            results.put("meanTotalLowPaeRiskLabMin", Math.round(meanTotalLowPaeRiskLabMin * 100d) / 100d);
            results.put("meanTotalMedPaeRiskLabMin", Math.round(meanTotalMedPaeRiskLabMin * 100d) / 100d);
            results.put("meanTotalHighPaeRiskLabMin", Math.round(meanTotalHighPaeRiskLabMin * 100d) / 100d);
            results.put("meanTotalLowPicuRiskLabMin", Math.round(meanTotalLowPicuRiskLabMin * 100d) / 100d);
            results.put("meanTotalMedPicuRiskLabMin", Math.round(meanTotalMedPicuRiskLabMin * 100d) / 100d);
            results.put("meanTotalHighPicuRiskLabMin", Math.round(meanTotalHighPicuRiskLabMin * 100d) / 100d);
            results.put("meanBumpedCount", Math.round(meanBumpedCount * 100d) / 100d);
            results.put("meanNewCasesSentICU", Math.round(meanNewCasesSentICU * 100d) / 100d);
            results.put("meanTotalAECount", Math.round(meanTotalAECount * 100d) / 100d);
            results.put("childRuns", childRuns);
            results.put("boxplotDataPicu", boxplotDataPicu);
            results.put("boxplotDataPae", boxplotDataPae);
            results.put("boxplotDataWeekdaysAfterEODCountMetrics", boxplotDataWeekdaysAfterEODCountMetrics);
            results.put("boxplotDataSysAvgDailyMinAfterEODMetricsMetrics", boxplotDataSysAvgDailyMinAfterEODMetricsMetrics);

            String resultsJson = gson.toJson(results);
            ctx.json(resultsJson);
        });

        app.get("/status", ctx -> {
            int jobId = ctx.queryParamAsClass("jobId", Integer.class).get();
            Job.JobStatus jobStatus = jobManager.checkStatus(jobId);
            ctx.json(jobStatus);
        });

        app.get("/incomplete-status", ctx -> {
            Gson gson = new Gson();
            String incompleteStatusJobs = gson.toJson(jobManager.checkAllStatus());
            ctx.json(incompleteStatusJobs);
        });


        app.post("/validate-config", ctx -> {

            JsonNode node = getJsonNodeFromStringContent(ctx.body());
            JsonSchema schema = getJsonSchemaFromStringContent(fileToString("config_schema.json"));
            Set<ValidationMessage> errors = schema.validate(node);

            ctx.json(errors);
        });

        app.post("/get-template", ctx -> {

            JsonObject inputJson = new Gson().fromJson(ctx.body(), JsonObject.class);
            String fileType = String.valueOf(inputJson.get("file_type")).replace("\"", "");;
            String filename = "config_"+fileType+".json";
            Map<String, Object> templateObject = new LinkedHashMap<>();
            templateObject.put("template", fileToString(filename));


            ctx.json(templateObject);
        });

        app.start(PORT);
    }


    public record RunColumns (String id, String name, String user, long startTime, long endTime, String status, String description, boolean isSelected) { }
    public record RunMetrics (
            String id,
            String name,
            Double addonCount,
            Double totalCount,
            Double weekdaysAfterEOD,
            Double systemAvgDailyMinAfterEOD,
            Double totalLowPaeRiskLabMin,
            Double totalMediumPaeRiskLabMin,
            Double totalHighPaeRiskLabMin,
            Double totalLowPicuRiskLabMin,
            Double totalMediumPicuRiskLabMin,
            Double totalHighPicuRiskLabMin,
            Double totalBumpedCount,
            Double totalNewCasesSentICUCount,
            Double totalAECount
    ) { }
    public record BoxPlotData(String x, List<Integer> y) {};

    public static List<RunColumns> getSimulationRuns(io.javalin.http.Context ctx, MlFlowManager mlFlowManager, JobManager jobManager) throws Exception {
        Optional<Service.Experiment> scheduleExp = mlFlowManager.client.getExperimentByName("simulation");
        Optional<String> optionalSimulationExpId = scheduleExp.map(Service.Experiment::getExperimentId);

        if (optionalSimulationExpId.isPresent()) {
            ArrayList<String> simulationExpIdList = new ArrayList<>(List.of(optionalSimulationExpId.get()));
            String filter = "params.run_type = 'simulation_aggregate'";
            RunsPage simulationRunResults = mlFlowManager.client.searchRuns(simulationExpIdList, filter, Service.ViewType.ACTIVE_ONLY, 1000);
            List<Service.Run> simulationRuns = simulationRunResults.getItems();

            assert simulationRuns != null;

            List<RunColumns> simRunsList = simulationRuns.stream()
                    .map(run -> new RunColumns(
                            run.getInfo().getRunId(),
                            run.getInfo().getRunName(),
                            run.getData().getTagsList().stream().filter(tag -> tag.getKey().equals("mlflow.user")).map(Service.RunTag::getValue).toList().get(0),
                            run.getInfo().getStartTime(),
                            run.getInfo().getEndTime(),
                            run.getInfo().getStatus().toString(),
                            run.getData().getTagsList().stream().filter(tag -> tag.getKey().equals("description")).toList().isEmpty() ? "" : run.getData().getTagsList().stream().filter(tag -> tag.getKey().equals("description")).map(Service.RunTag::getValue).toList().get(0),
                            false
                    )).toList();

            List<RunColumns> jobQueueList = jobManager.checkAllStatus().stream()
                    .filter(job -> job.getJobStatus() == Job.JobStatus.NOT_STARTED)
                    .map(job -> new RunColumns(
                    null,
                    job.getInputArgs().expName,
                    null,
                    0L,
                    0L,
                    "QUEUED",
                    job.getInputArgs().description,
                    false
            )).toList();

           return Stream.concat(Lists.reverse(jobQueueList).stream(), simRunsList.stream()).toList();
        } else {
            throw new Exception("Something went wrong accessing MLFlow simulation runs");
        }

    }

    public static List<RunMetrics> getChildRuns(MlFlowManager mlFlowManager, String parentRunId) throws Exception {
        Optional<Service.Experiment> scheduleExp = mlFlowManager.client.getExperimentByName("simulation");
        Optional<String> optionalSimulationExpId = scheduleExp.map(Service.Experiment::getExperimentId);

        if (optionalSimulationExpId.isPresent()) {
            ArrayList<String> simulationExpIdList = new ArrayList<>(List.of(optionalSimulationExpId.get()));
            String filter = String.format("tags.mlflow.parentRunId = '%s'", parentRunId);
            RunsPage simulationRunResults = mlFlowManager.client.searchRuns(simulationExpIdList, filter, Service.ViewType.ACTIVE_ONLY, 1000);
            List<Service.Run> childRuns = simulationRunResults.getItems();

            assert childRuns != null;

            return childRuns.stream()
                    .map(run -> new RunMetrics(
                            run.getInfo().getRunId(),
                            run.getInfo().getRunName(),

                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Addon_Count")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Addon_Count")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Case_Count")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Case_Count")).map(Service.Metric::getValue).toList().get(0),

                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Weekdays_With_After_EOD_Count")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("System_Avg_Daily_Time_After_EOD")).map(Service.Metric::getValue).toList().get(0),

                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pAE_Risk_Lab_Minutes")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pAE_Risk_Lab_Minutes")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pAE_Risk_Lab_Minutes")).map(Service.Metric::getValue).toList().get(0),

                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Low_pICU_Risk_Lab_Minutes")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_Medium_pICU_Risk_Lab_Minutes")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_High_pICU_Risk_Lab_Minutes")).map(Service.Metric::getValue).toList().get(0),

                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Bumped_Case_Count")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Bumped_Case_Count")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("New_Cases_Sent_to_ICU_Count")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("New_Cases_Sent_to_ICU_Count")).map(Service.Metric::getValue).toList().get(0),
                            run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_AE_Count")).toList().isEmpty() ? null : run.getData().getMetricsList().stream().filter(metric -> metric.getKey().equals("Total_AE_Count")).map(Service.Metric::getValue).toList().get(0)

                    )).toList();
        } else {
            throw new Exception("Something went wrong accessing MLFlow simulation runs");
        }

    }

    protected static JsonNode getJsonNodeFromStringContent(String content) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(content);
    }

    protected static JsonSchema getJsonSchemaFromStringContent(String schemaContent) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
        return factory.getSchema(schemaContent);
    }


    public static void main(String[] args) {
        API.apiServer();
    }
}
