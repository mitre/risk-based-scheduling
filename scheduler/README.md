# Scheduler

Scheduler framework for the Bayesian GRACE Risk-Based Scheduling project.

## Description

This framework takes in a list of cases (either historical or synthetically-generated). Cases must have a procedure attribute, which we recommend calling 'procedure,' and if cases have an assigned doctor, that field must be called 'attending' (this matches the attendings for a lab in the lab config file). It also takes in an instantiation of the Board class with defined scheduling rules (defined in the overall scheduler config json file) and a scheduling algorithm (a subclass of the abstract Algorithm class).

The framework places the cases on the schedule Board according to the scheduling constraints and scheduling algorithm and outputs a json schedule file that can be used as an input for the Bayesian GRACE Risk-Based Scheduling simulation model. The schedule that is saved covers the time between the config-specified start and end dates, after a warmup period. It is saved in an MLflow run. Before running locally, you must start up an Mlflow instance and must have your system environment variable MLFLOW_TRACKING_URI set appropriately.

## Usage

### Defining scheduler constraints (in the scheduler config file)

- start_date: The start date of the schedule to save, which will also be the start date of the simulation model.
- end_date: The end date of the schedule to save, which will also be the end date of the simulation model, barring additional simulation runline arguments.
- case_arrivals: The parameters defining case arrival rates and scheduling time window constraints.
  - distribution: What distribution the arrivals should follow (can be 'Poisson' or 'Uniform').
  - params: A list of parameters for the arrival distribution, in the order of the Numpy distribution's arguments (for Poisson: rate; for Uniform: min, max). Any excess parameters will not cause an error and will just be ignored. However, too few parameters will lead to an error.
  - time_window: The time window (in weeks) in which to schedule cases.
  - lead_weeks: The number of weeks forward from the date a case arrives to start the time window.
  - warmup_weeks: The number of weeks to have cases arrive before the actual schedule period, which allows the scheduler to warm up from an empty starting state. We recommend testing that this argument is long enough (that lengthening it further does not significantly alter the metric results) for the lab setup and inputs to the scheduler.
- config_files: Additional configuration files that define scheduling rules.
  - lab: The name of the json file* defining lab configuration (schedule, procedure, and attending information).
  - scheduling: The name of the json file that defines scheduling rules.
- case_file: The name of the file that contains cases, or the name of a case_generator experiment in MLflow. In either case, cases must include at least the following attributes:
  - procedure: integer for the procedure to be done
  - adverseScore: integer score of adverse event profile
  - riskScore: integer score of risk profile
  - addon: binary integer for whether it is an addon (1) or elective (0) case
  - priorLocation: string for where the patient came from
  - pICU: float for the probability the case is admitted to the ICU upon completion
  - durationScore: integer score of duration profile
- algo_name: The algorithm class with which to schedule cases, which must be defined in algorithm.py.
- reorder_cases: Parameters for if and how to reorder cases within labs after case scheduling is complete.
  - whether: A boolean for whether to reorder cases or not.
  - what: The case attribute on which to reorder cases.
- json_file: The name of the json file for the saved schedule.

*Note that all file names should contain the extension as part of the argument.

### Defining scheduling constraints (in the lab config file)

- lab: The name of the lab.
- schedule: The days of the week the lab is open (capitalized and spelled out).
- day_params: specific settings for the lab for each day of the week
  - procedures: A list of case procedures that the lab can handle.
  - n_daily_cases: The number of daily cases that can normally be done in the lab (how many slots there will be).
  - attendings: The name(s) of the attending(s) working in the lab. If multiple are included, they should be listed in preferred order for matching/assigning cases.

The best way to turn off a lab on a day is to remove that day from its 'schedule' list, and to turn off a lab completely, leave its 'schedule' blank. A lab can also be effectively turned off on any particular day by specifying 'n_daily_cases' as 0, or by leaving 'attendings' empty.

### Defining scheduling constraints (in the scheduling rules config file)

- point_system: Parameters for point-based scheduling heuristics.
  - active: Whether or not points should be applied to and tracked for cases (regardless of whether or not they are used for scheduling).
  - ignore_cases: A list, which can be empty, of things which can individually disqualify a case from point consideration (give it 0 for everything). Each entry must specify:
    - attribute: A case attribute on which to decide if the case is ignored.
    - values: A list of one or more values for that attribute which would cause the case to be ignored.
  - overall_limits: Limits for points overall on each day (required, even if the particular points-based algorithm doesn't use it).
  - attribute_importance_order: A list of point attributes designating their order of importance for consideration when a case has to be forcibly scheduled (with the first being the most important). If a case cannot be placed in a slot that violates no point constraints, it will try to place into a slot that doesn't violate each particular point attribute constraint in turn. Do not include any point attributes which are not defined or not active in attributes below. If there are active attributes missing from this list (including if the list is left empty), they will be skipped, and anything included in the forcibly_scheduled_over_all_cases total may not actually violate these omitted attribute point limits.
  - added_slot_attribute: An active point attribute to use when deciding what day/lab pair on which to add a slot, if it's needed to schedule a case. To use overall case points, enter 'overall' ('Points' algorithm will automatically use the overall case points).
  - attributes: A list of attributes to include in a point score. Each has:
    - name: A string labeling the attribute, which must be unique.
    - active: A boolean for whether the attribute will be used or not.
    - which: One of 'day' or 'lab' denoting whether the attribute's limits are applied to days or labs (required, even if the particular points-based algorithm doesn't use it).
    - point_limits: Limits for points in a lab or day, specified in 'which' (required, even if the particular points-based algorithm doesn't use it)
    - case_attribute: A string denoting which attribute will be used with the thresholds (this must match to an attribute of the cases themselves).
    - levels: A list of dictionaries, one for each point level. There must be at least one level in the list, and one level must have a 'null' upperbound. There should not be multiple of any given upperbound. Each contains:
      - name: A string for what to call the level.
      - upperbound: An integer or float for the upper bound of that level (if this is the top level, this should be 'null' instead).
      - points: The integer or float value corresponding to the level.

### Running the scheduler (with runline arguments)

The scheduler takes the following runline arguments:

- config_file: The name of the scheduler config file.
- schedule_name: What to call the scheduler experiment in MLflow.
- iters: An integer for how many schedules should be created (as child MLflow runs) with the inputs. Defaults to 1 (will still be a child run).

To run the scheduler, execute the following, replacing the sample parameters with your own:

`python src/main.py -config_file scheduler_config_local.json -schedule_name test_schedule -iters 5`

## MLflow logged data

### Metrics

There are various scheduling metrics which are logged to MLflow. They exist for each child run, and the average exists for the parent run. They are:

- unscheduled_overall_cases: The number of unscheduled cases over the entire arrival period. All other scheduled case metrics count only those cases scheduled during the saved schedule period of interest.
- added_slot_scheduled_cases: The number of cases which were scheduled to a slot that was added beyond the initial lab configuration.
- total_forcibly_scheduled_cases: The total number of cases scheduled while violating at least one point constraint. If there are no point constraints for the chosen algorithm, this will be 0.
- forcibly_scheduled_over_\[point_attribute\]_cases: The number of cases which were scheduled while violating the point constraint for 'point_attribute.' There will be a metric of this form for each active point attribute, when applicable, as well as one where 'point_attribute' is 'all' (when all active attributes are violated). Note that there can be overlap between individual attribute metrics, but they will not overlap with the 'all' metric (this is why 'total_forcibly_scheduled_cases' is also tracked separately).
- \[weekday\]_count: The number of cases scheduled to this day of the week (there are seven counts of this form).

### Parameters

Both the parent and child runs have parameters. They share:

- algo_name: The name of the algorithm used to schedule cases, as specified in the config.
- case_file: The name of the case file, or case generator run, used as a source of cases.
- name: For the parent, this is the same as the name of the run. For the child, this is that name, but with 'child_#' appended, with # being its iteration.

The parent run only has:

- iters: The number of iterations, as specified in the runline argument.

The child runs only have:

- parent_name: The name of its parent scheduler run.

### Artifacts

Each child run contains the output schedule json file. Each parent run contains the three config json files.

## Authors and acknowledgment

Authored by Madi Ramsey (mramsey@mitre.org), Bennett Miller (bennettmiller@mitre.org), and Rebecca Olson (rolson@mitre.org).
