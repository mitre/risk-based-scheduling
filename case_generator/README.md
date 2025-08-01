# Case Generator

Synthetic Case Generator for the Bayesian GRACE Risk-Based Scheduling project.

## Description

This framework has two potential uses: uploading a case file and creating a case file.
Only the uploading of a previously constructed case file is guaranteed to be supported. This is the main use, and is done through the Case Generator front-end interface,
adding both an elective case file and an add-on case file. From there they can be used to run the Scheduler and Simulation. The other use is running locally,
using a trained model from the SDV library (a .pkl file) to output a specified number of synthetic cases into the two case files, which are then added to Mlflow.
Before running locally, you must start up an Mlflow instance and must have your system environment variable `MLFLOW_TRACKING_URI` set appropriately.
The training of a model is not provided by this framework, and if running in this capacity, the model's case attributes must match up to the code and requirements for the Scheduler.
Again, support for running in this way has no guaranteed support, and there may be additional packages or versions of packages that need to be installed.

## Usage

To use the Synthetic Case Generator in it's encouraged capacity, simply run from the front-end interface Case Generator tab, uploading the appropriate files.

To use it to create the synthetic case files, run with the following arguments:

`python3 src/main.py -sdv_model model.pkl -pop_size 1523 -randomize_samples 'False' -name ThisGeneratorRun -description 'This is an example'`

- Replace `-sdv_model` with your trained sdv model filename. It should be a .pkl file.
- Replace `-pop_size` with your population size of choice.
- Replace `-randomize_samples` with 'True' to randomize samples. Defaults to 'False' for returning the same sample (uses fixed seed when sampling).
- Replace `-name` with the case-generator run name of your choice, which will label it in the case_generator Mlflow experiment.
- Replace `-description` with a description of your choice, or run without the flag to omit a description.

You can also run locally to upload elective and add-on case files, accomplishing the same result as running from the front-end. To do this, run with the following arguments:

`python3 src/main.py -name ThisGeneratorRun -description 'This is an example' -manual_files elective.json addon.json`

- Replace `-name` with the case-generator run name of your choice, which will label it in the case_generator Mlflow experiment.
- Replace `-description` with a description of your choice, or run without the flag to omit a description.
- Replace `-manual_files` arguments with the json file names with whatever the local elective and add-on files are.

This if you are running this way with `-manual_files` and you include any of the arguments `sdv_model`, `pop_size`, and `randomize_samples`, they will be ignored/overwritten, and no synthetic population will be generated.

## Public Release

©2023 The MITRE Corporation. Public Release Pending.

## License

Will be licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
