package org.mitre.bch.cath.simulation.app;

import org.mitre.bch.cath.simulation.model.CathLabSim;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.mitre.bch.cath.simulation.utils.MlFlowManager;
import org.mlflow.api.proto.Service;

public class JobManager {
    private LinkedList<Job> jobQueue;

    AtomicInteger atomicInteger = new AtomicInteger(0);

    /** Constructor for JobManager instance
     */
    public JobManager() {
        this.jobQueue = new LinkedList<>();
    }

    /** Creates and submits Job to job queue.
     * @param inputArgs InputArgs instance with all inputs needed to run the simulation
     */
    public int submit(InputArgs inputArgs) {
        Job job = new Job(inputArgs);
        job.setJobId(atomicInteger.getAndIncrement());
        jobQueue.add(job);
        return job.getJobId();
    }

    /** Checks status of given job
     * @param jobId Id of job.
     */
    public Job.JobStatus checkStatus(int jobId) {
        Optional<Job> job = jobQueue.stream().filter(j -> j.getJobId() == jobId).findFirst();
        if (job.isPresent()) {
            return job.get().getJobStatus();
        } else {
            return Job.JobStatus.DOES_NOT_EXIST;
        }
    }

    /** Returns all incomplete jobs in the job queue
     */
    public List<Job> checkAllStatus() {
        return jobQueue.stream().filter(j -> j.getJobStatus() == Job.JobStatus.NOT_STARTED || j.getJobStatus() == Job.JobStatus.IN_PROGRESS || j.getJobStatus() == Job.JobStatus.FAILED).toList();
    }

    /** Returns whether there is a job is currently running
     */
    public Boolean currentlyRunningJob() {
        return !jobQueue.stream().filter(j -> j.getJobStatus() == Job.JobStatus.IN_PROGRESS).toList().isEmpty();
    }

    /** Runs next job in job queue if no job is currently running.
     * @param mlFlowManager instance of MLFlowManager to manage MLFlow calls
     */
    public void runNextJob(MlFlowManager mlFlowManager) throws IOException {
        if (Boolean.FALSE.equals(currentlyRunningJob()) && !this.jobQueue.isEmpty()) {
            Job nextJob = this.jobQueue.element();
            if (nextJob.getJobStatus() == Job.JobStatus.NOT_STARTED) {
                nextJob.setJobStatus(Job.JobStatus.IN_PROGRESS);
                InputArgs inputArgs = nextJob.getInputArgs();
                mlFlowManager.setParentRunName(inputArgs.expName);
                try {
                    CathLabSim.runSim(
                            inputArgs.iterations,
                            inputArgs.sched,
                            inputArgs.seed,
                            inputArgs.folderName,
                            inputArgs.expName,
                            inputArgs.extraDays,
                            inputArgs.description,
                            inputArgs.addonBucket,
                            mlFlowManager,
                            inputArgs.configData,
                            null,
                            inputArgs.verbose,
                            null
                    );
                    nextJob.setJobStatus(Job.JobStatus.COMPLETE);
                } catch(Exception e) {
                    mlFlowManager.client.setTerminated(mlFlowManager.parentRunId, Service.RunStatus.FAILED);
                    nextJob.setJobStatus(Job.JobStatus.FAILED);
                }
                this.jobQueue.remove();
                runNextJob(mlFlowManager);
            }
        }
    }

}
