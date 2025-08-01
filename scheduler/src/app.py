from flask import Flask, request
from flask_cors import CORS
import os
import re
import json
import jsonschema
import mlflow
import pandas as pd
import numpy as np
import datetime
import main

app = Flask(__name__)
CORS(app)


@app.route("/run-scheduler", methods=['POST'])
def generate_schedule():
    """
    Runs the scheduler based on the provided inputs.
    """

    input = request.json
    app.logger.info(input)

    main.run_scheduler("scheduler_config_local.json", input['name'], int(input['iterations']), input['description'], input["data"])

    return {"message": "Successfully ran scheduler", "inputs": input}

@app.route("/get-pop-schedules", methods=['GET'])
def get_pop_schedules():
    """
    Get all populated schedule names from MLFlow.
    """

    _MLFLOW_COLUMNS = {
        "run_id": "id",
        "tags.mlflow.runName": "name",
        "tags.mlflow.user": "user",
        "start_time": "startTime",
        "end_time": "endTime",
        "status": "status",
        "tags.description": "description",
    }

    return (
        mlflow
        .search_runs(experiment_names=["scheduler"], filter_string="tags.run_type = 'scheduler_parent'")
        .rename(columns = _MLFLOW_COLUMNS)
        .filter(items = list(_MLFLOW_COLUMNS.values()))
        .to_dict(orient="records")
    )



@app.route("/get-sched-configs", methods=['GET'])
def get_sdv_models():
    """
    Get all pre-generated schedule config files in working directory.
    """

    files = [f for f in os.listdir('.') if os.path.isfile(f)]
    sched_configs = []
    for file in files:
        if re.search("^scheduler_config", file):
            sched_configs.append({'name':file, 'value': file})

    return {"sched_configs": sched_configs}


@app.route("/get-sched-details", methods=['GET'])
def get_schedule_details():
    """
    Get points heatmap data for all schedules under given parent runID from MLFlow.
    """
    child_sched_df = mlflow.search_runs(experiment_names=["scheduler"], filter_string = "tags.mlflow.parentRunId = '{}'".format(request.args['parentRunId']))
    parent_run_df = mlflow.search_runs(experiment_names=["scheduler"], filter_string = "run_id = '{}'".format(request.args['parentRunId']))
    lab_config = mlflow.artifacts.load_dict(parent_run_df['artifact_uri'][0] + '/lab_config.json')
    scheduling_rules = mlflow.artifacts.load_dict(parent_run_df['artifact_uri'][0] + '/scheduling_rules_config.json')
    scheduler_config = mlflow.artifacts.load_dict(parent_run_df['artifact_uri'][0] + '/scheduler_config.json')

    start_date = datetime.datetime.strptime(scheduler_config['start_date'],'%Y-%m-%d')
    end_date = datetime.datetime.strptime(scheduler_config['end_date'],'%Y-%m-%d')
    total_days = (end_date-start_date).days

    slots_per_day = {weekday: 0 for weekday in ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']}
    ignore_procedures = []
    for ignore_attribute in scheduling_rules['point_system']['ignore_cases']:
        if ignore_attribute['attribute'] == 'procedure':
            ignore_procedures = ignore_attribute['values']

    for config in lab_config['lab_configs']:
        for day in config['day_params'].keys():
            if day in config['schedule'] and sum([True if procedure not in ignore_procedures else False for procedure in config['day_params'][day]['procedures']])>0:
                slots_per_day[day] += config['day_params'][day]['n_daily_cases']

    print(slots_per_day)

    schedule_data = []
    for index in child_sched_df.index:
        run_data = child_sched_df.iloc[index].to_dict()

        schedule = pd.DataFrame(mlflow.artifacts.load_dict(child_sched_df['artifact_uri'][index] + '/schedule.json'))
        agg_days = schedule[['day','points']].groupby(['day']).sum().reset_index()
        all_weekdays = pd.DataFrame({'day': [day for day in range(total_days+1) if (start_date+datetime.timedelta(days=day)).weekday() not in [5,6]]})
        all_weekdays['dow'] = [ (start_date + datetime.timedelta(days=day)).weekday() for day in all_weekdays['day'] ]
        full_agg_days_df = all_weekdays.merge(agg_days, on='day', how='left')
        full_agg_days_df.fillna(0, inplace=True)

        points_values = {dow: full_agg_days_df['points'][full_agg_days_df['dow'] == dow].to_list() for dow in [0,1,2,3,4] }
        heatmap_data = [{'name': weekday, 'data': list(np.array(points_values[dow]) / slots_per_day[weekday])} for dow, weekday in enumerate(['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'])]
        heatmap_data.reverse()

        schedule_data.append({'run_data': run_data, 'heatmap': heatmap_data})

    return schedule_data


@app.route("/validate-scheduling-rules", methods=['POST'])
def validate_scheduling_rules():
    """
    Validates scheduling rules instance against scheduling rules json schema.
    """

    return validate_config(request.json, 'resources/scheduling_rules_schema.json')


@app.route("/validate-lab-config", methods=['POST'])
def validate_lab_config():
    """
    Validates lab config instance against lab config json schema.
    """

    return validate_config(request.json, 'resources/lab_config_schema.json')


@app.route("/get-template", methods=['POST'])
def get_template():
   """
   Gets json for scheduling rules template.
   """

   if request.json['config_type'] == 'scheduling_rules':
        template_filepath = 'resources/scheduling_rules_'+request.json['file_type']+'.json'
   elif request.json['config_type'] == 'lab_config':
        template_filepath = 'resources/lab_config_'+request.json['file_type']+'.json'
   else:
        return {"template": "Not a valid template type."}


   return get_template_json(template_filepath)




def validate_config(instance, schema_filepath):
    """
    checks config file against template
    """
    # Opening JSON file
    with open(schema_filepath) as file:

        # return JSON object as a dictionary
        schema = json.load(file)

         # validate instance against schema
        validator = jsonschema.Draft7Validator(schema)
        errors = [{"message": error.message} for error in validator.iter_errors(instance)]
        valid = True if len(errors) == 0 else False

    return {"valid": valid, "error": errors}


def get_template_json(template_filepath):
    """
    Gets template from JSON file to dictionary
    """
    # Opening JSON file
    with open(template_filepath) as file:

        # return JSON object as a dictionary
        template = json.load(file)

    return {"template": template}

