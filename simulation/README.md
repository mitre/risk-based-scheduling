# Risk Based Clinical Scheduling Simulation
The Risk Based Clinical Scheduling Simulation is intended to model the system-level dynamics of the Boston Children's Hospital's cardiac catheterization lab. The main objective of the model is to estimate the time dependent system-level risk, currently defined as the system probability of a case admission to the ICU. Patients with different levels of risk and different procedures are scheduled in the various labs. When cases inevitably overlap, the system-level risk changes. This model simulates the schedule and estimates the risk.

## Setup and Running

### Requirements

- Java OpenJDK 17+ (Azul Zulu's JDK 18 was used in the development)
- (optional) Gradle 7.4.2+. If not installed, the bundled `./gradlew` can be used to build and run the project.

### Setup
The configuration files are stored in `src/main/resources`:
- `config.json`
    This file contains the configuration for the lab environment and simulation, including the following:
  - labs
    - id
    - name
    - labType
    - weekdays, as integers, which are the days the lab can have new cases or bumped cases scheduled
    - preferred, as integers, which are the days that are checked first for scheduling new addon cases
  - procedures (including the steps within the procedures)
  - resources
  - (bump) thresholds for how many times a case urgency can be bumped
  - the simulation start date
  - endTime, after which labs are considered to be working late in 'overtime' hours, used for pushing and metrics 
  - the startTime and earlyEndTime, used for metric calculations; in the simulation, labs don't officially end early, 
and they start stochastically, not at this start time.
  - whether add-on cases are pushed at the end of the day
  - what push logic they should follow, if any; if pushCases is true, a valid pushLogic must be given
  - pAEThresholds for cumulative probability of an AE, for tracking risk-related metrics
  - pICUThresholds for cumulative probability of at least one ICU admission, for tracking additional risk-related metrics
- `resources` 
    This folder can contain schedules that can be used in the simulation when running locally. 
The schedule files must be named `schedule_NAME.json`, where `NAME` is the name of the schedule.
Regardless of whether schedules come from local or from an Mlflow Scheduler run, each schedule contains a list of elective cases 
to be performed with the required attributes:
  - day: integer for the day on the schedule of that case
  - lab: integer for the assigned lab
  - procedure: integer for the procedure to be done
  - adverseScore: integer score of adverse event profile
  - riskScore: integer score of risk profile
  - addon: binary integer for whether it is an addon (1) or elective (0) case
  - priorLocation: string for where the patient came from
  - pICU: float for the probability the case is admitted to the ICU upon completion
  - durationScore: integer score of duration profile
  There may be additional attributes attached to cases which are not used and which will not negatively impact the performance of the simulation. 
  The cases within each day and lab are assumed to be performed in the order in which they are listed on a schedule, 
  except for addon cases, which are performed based on urgency criteria.

- `prob_dist`
    This folder contains a list of probability distributions
  - `p_ae.json` - Probability of an adverse event, as a function of adverseScore, procedure, and procedure step.
  - `time_case.json` - Probability distributions for duration of a step, as a function of adverseScore, procedure, and procedure step.
  - `time_start.json` - Probability distributions for the start time of the first case each day, as a function of the lab.
  - `time_turnover.json` - Probability distributions for the turnover time between cases.
  - `addon.json` - Probability distributions for the arrival rate of addons, as a function of day of the week.

  These probability distributions are derived from the historical population of BCH's Cath Lab (2005-2019). 
  The adverse event probabilities are derived based on patients from (2013-2019), 
  due to such data was not recorded prior to 2013. To run the simulation without an admission to the ICU, 
  cases in a schedule should all have a `pICU` of 0. To run without any adverse events, the distributions in 
  the `p_ae.json` file should map each possible combination to a probability of 0. To run without addons, 
  the distributions in the `addon.json` file should be adjusted for each weekday to set arrivals at 0.

### Run Simulation
`gradle` (or `./gradlew`) is used to build and run the simulation.
To run the simulation locally: Set mainClassName = 'org.mitre.bch.cath.simulation.model.CathLabSim' in the build.gradle file. Start up an Mlflow instance and must have your system environment variable MLFLOW_TRACKING_URI set appropriately.

The simulation takes 6 arguments:
- `-n` Number of iterations, default to 1. This defines the number of simulation iterations a run will contain, to be executed in parallel. Each iteration (except first iteration) uses a random number seed that is the integer increment from the seed from previous iteration.
- `-s` Schedule name, required. This specifies the schedule to be used in the simulation, either as the suffix of a local schedule file or the name of an Mlflow scheduler experiment from which to pull the schedule file. For example, `-s elective_local` will run the simulation with the local schedule `schedule_elective_local.json` and `-s example_schedule` will run with the schedule pulled from the Mlflow scheduler experiment named `example_schedule`.
- `-d` Starting seed, integer expected, default to 0. This is the random number seed used by the first iteration. Each subsequent iteration uses a seed that is incremented from the previous iteration.
- `-f` Metrics folder suffix, default to the current system time. This is the suffix of the folder within `/logs` in which to place the various metric csv files from the simulation run. That folder will take the form `metrics_``[-f]`. *Note* -- If a folder of the same name already exists, any existing files within will be overwritten if new runs have metrics files of the same name.
- `-e` MlFlow experiment name, required. This will be the name of the parent run under the Simulation experiment in the MlFlow client. 
- `-l` Extra days, default to 0. This is the length of time the simulation should run, in days, beyond the day of the last scheduled case in the passed schedule.
- `-b` File of addon patients. This can be excluded if the schedule input refers to an Mlflow Scheduler experiment run, and it defines the patients that are addons. For example, `-b addon_bucket_local` will run the simulation with addons from the local file `addon_bucket_local.json`.
- `-c` Name of the config file to be used.
- `-v` Boolean for whether to run with extra Mlflow metric, Mlflow artifact, and local log saving. If false, saves only a select few metrics (which are used by the front-end tool, if that is being used). If true, saves many additional metrics, and csv files logging what happened in the simulation. Defaults to `false`.

For example, to run 10 iteration of the simulation with schedule file `schedule_elective_test.json` with a starting seed of 12, in a folder called `metrics_testRun` with 3 extra days, run:

    ./gradlew run --args="-n 10 -s elective_local -d 12 -f testRun -l 3 -b addon_bucket_local -e testMlflowEntry -c config.json" 

## Public Release
©2023 The MITRE Corporation. Public Release Pending.

## License
Will be licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
