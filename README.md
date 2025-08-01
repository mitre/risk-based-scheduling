# GRACE Risk-Based Scheduling Tool

This repository builds and runs the full stack of case_generator, scheduler, simulation & frontend with Vue.js + Java + Python + Postgres using Docker Compose.


## Prerequisites

- Docker
- Docker Compose 

## Installing, Running and Building

1. Clone this repository
2. Generate a GitLab acces token:
    - In GitLab, go to "Edit Profile" > "Access Tokens" > "Add new token"
    - Paste the generated token in the .env file as the value of `TOKEN`
3. Start up the docker network by running the following command from within the `grace_scheduling_tool` repository:

```bash
$ docker compose up
```

4. Wait for the docker containers to build. This will take a few minutes for the first build.
5. Navigate to the following url address: `http://localhost:3000`

## Using the GRACE Risk-Based Scheudling Tool

The GRACE Risk-Based Scheduling Tool was designed to evaluate the impact of scheudling decisions on system-level operating department metrics before implementing them. The tool supports the generation and evaluation of schedules by using a multi-step approach:
1. Upload of case population files [`Case File` tab]
2. Schedule creation using inputted population files, a selected schedule heuristic, and a given lab configuration [`Schedule` tab]
3. Simulation of schedules to evaluate system-level risk and efficiency performance metrics  [`Experiments` tab]

The tool helps users validate scheduling heuristics by visualizing: 1. schedule heatmaps that show how daily cumulative case complexity is distributed throughout the scheduling time-period, and 2. simulated system-level operating department risk and efficiency metrics.

The frontend interface supports case file upload, schedule creation, and schedule simulation. Model inputs and results can also be visualized within the tool. After generating a case file run, schedule run, or simulation experiment run, the page will have to be refreshed to see the details populate in the results table. When generating a case file run or schedule run, the user will see a spinner and will be directed to wait until the run is complete. File uploads and schedule creation are relatively quick processes. Simulation experiments take longer, so these run in the background. A user can submit multiple simulation experiment runs in sequence. These runs will be added to a job queue, and the status will show as "Running" or "Queued" in the experiments results table. 

More detailed information on model inputs, additional performance metrics, and log files are tracked and stored using an MLFlow server and database. The user can view these details, as well as delete or rename model runs, by navigating to the MLFlow dashboard at the following url address:  `http://localhost:5000`

More details for using MLFlow can be found in the [MLFlow docs](https://mlflow.org/docs/latest/index.html#).

Refer to the GRACE Risk-Based Scheduling Tool User Guide (located in the docs folder) for more details on tool usage. 


# Public Release
©2023 The MITRE Corporation and Boston Children's Hospital. 
Approved for Public Release; Distribution Unlimited. 
Public Release Case Number 23-3453

# License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
