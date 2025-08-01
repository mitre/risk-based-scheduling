package org.mitre.bch.cath.simulation.utils;


import org.mlflow.api.proto.Service;
import org.mlflow.tracking.ActiveRun;
import org.mlflow.tracking.MlflowClient;
import org.mlflow.tracking.MlflowContext;

import java.util.Optional;

public class MlFlowManager {
    public final MlflowClient client;
    public final MlflowContext context;
    public final String expName;
    public final String expId;
    public String parentRunName;
    public String parentRunId;

    public MlFlowManager(String expName) {
        this.client = new MlflowClient();
        this.context = new MlflowContext();
        this.expName = expName;
        this.expId = getExpId();
    }

    private String getExpId() {
        Optional<Service.Experiment> exp = client.getExperimentByName(expName);
        if (exp.isEmpty()) {
            return client.createExperiment(expName);
        } else {
            return exp.get().getExperimentId();
        }
    }

    public void setParentRunName(String parentRunName) {
        this.parentRunName = parentRunName;
    }

    public void startNestedRun() {
        context.setExperimentId(expId);
        ActiveRun parentRun = context.startRun(parentRunName);
        this.parentRunId = parentRun.getId();
    }

    public String startRun(String runName) {
        ActiveRun activeRun = context.startRun(runName, parentRunId);
        return activeRun.getId();
    }

}
