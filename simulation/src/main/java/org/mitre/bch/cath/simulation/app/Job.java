package org.mitre.bch.cath.simulation.app;


public class Job {

    public enum JobStatus {DOES_NOT_EXIST, NOT_STARTED, IN_PROGRESS, FAILED, COMPLETE};

    private final InputArgs inputArgs;
    private int jobId;
    private JobStatus jobStatus;


    /** Constructor for Job instance
     * @param inputArgs instance of InputArgs containing all necessary inputs to run the simulation
     */
    public Job(InputArgs inputArgs) {
        this.inputArgs = inputArgs;
        this.jobStatus = JobStatus.NOT_STARTED;
    }

    public InputArgs getInputArgs() {
        return this.inputArgs;
    }

    public int getJobId() {
        return this.jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public JobStatus getJobStatus() {
        return this.jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

}
