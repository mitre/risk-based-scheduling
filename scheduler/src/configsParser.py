from weekdayConfig import WeekdayConfig
from attending import Attending
from case import Case
from lab import Lab
import datetime
import mlflow
import pandas as pd
import json
from csv import DictReader

def parseConfig(config_file, inputs=None):
    """
    Parses a scheduler config file.
    """
    try:
        f = open(config_file)
        data = json.load(f)
        if inputs:
            data['start_date'] = inputs['startDate']
            data['end_date'] = inputs['endDate']
            data['case_arrivals']['distribution'] = inputs['arrDistrib']
            if inputs['arrDistrib'] == 'Poisson':
                data['case_arrivals']['params'] = [float(inputs['arrLambda'])]
            else:
                data['case_arrivals']['params'] = [int(inputs['minDailyArr']), int(inputs['maxDailyArr'])]
            data['case_arrivals']['time_window'] = int(inputs['timeWindow'])
            data['case_arrivals']['lead_weeks'] = int(inputs['leadTime'])
            data['config_files']['lab'] = inputs['labConfigName']
            data['config_files']['scheduling'] = inputs['schedRulesConfigName']
            data['algo_name'] = inputs['algoName']
            data['reorder_cases']['whether'] = inputs['reorderCases']
            data['case_file'] = inputs['caseFile']

        mlflow.log_dict(data, "scheduler_config.json")

        start_date = datetime.datetime.strptime(data['start_date'],'%Y-%m-%d')
        end_date = datetime.datetime.strptime(data['end_date'],'%Y-%m-%d')
        case_arrivals = data['case_arrivals']
        allowed_arrival_dists = ['Poisson', 'Uniform']
        assert data['case_arrivals']['distribution'] in allowed_arrival_dists, f"{data['case_arrivals']['distribution']} is not an accepted arrival distribution."

        config_files = data['config_files']
        lab_config = config_files['lab']
        scheduling_config = config_files['scheduling']

        case_file = data['case_file']
        algo_name = data['algo_name']
        json_file = data['json_file']
        reorder_cases = data['reorder_cases']

        labs, attendings, labs_open = parseLab(lab_config, inputs)
        weekdays, points_active, point_mapping, point_ignore, active_attrs, point_imp_order, added_slot_attr = parseSchedulingRules(scheduling_config, attendings, labs, inputs)

        return start_date, end_date, case_arrivals, case_file, algo_name, json_file, weekdays, labs_open, points_active, point_mapping, point_ignore, active_attrs, point_imp_order, added_slot_attr, reorder_cases
    except:
        raise Exception("Problem parsing scheduler config file.")

def parseLab(lab_config, inputs=None):
    """
    Parses a lab config file to instantiate Lab and Attending objects.
    """
    try:
        if inputs:
            data = inputs['labConfiguration']
        else:
            f = open(lab_config)
            data = json.load(f)
        mlflow.log_dict(data, "lab_config.json")

        lab_configs = []
        attendings_dict = {}
        labs_open = []
        weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

        for lab_dict in data['lab_configs']:
            lab = Lab(lab_dict['lab'],
                            lab_dict['schedule'],
                            lab_dict['day_params'])
            lab_configs.append(lab)
            if len(lab_dict['schedule']) != 0:
                labs_open.append(lab_dict['lab'])
                for d in lab_dict['schedule']:
                    assert d in weekdays, f"Non-day value entered in lab '{lab_dict['lab']}' schedule."

            for day, day_params in lab_dict['day_params'].items():
                attends = day_params['attendings']
                procedures = day_params['procedures']
                for attend in attends:
                    if attend not in attendings_dict:
                        attendings_dict[attend] = {'procedures': procedures, 'schedule': [day]}
                    else:
                        for procedure in procedures:
                            if procedure not in attendings_dict[attend]['procedures']:
                                attendings_dict[attend]['procedures'].append(procedure)
                        attendings_dict[attend]['schedule'].append(day)
        attendings = []
        for attending_name, attending_values in attendings_dict.items():
            attending = Attending(attending_name,
                                attending_values['procedures'],
                                attending_values['schedule'])
            attendings.append(attending)

        return lab_configs, attendings, labs_open
    except:
        raise Exception("Problem parsing lab config.")

def parseSchedulingRules(scheduling_config, all_attendings, all_labs, inputs=None):
    """
    Parses a scheduling rules config file.
    """
    try:
        if inputs:
            data = inputs['schedulingRules']
        else:
            f = open(scheduling_config)
            data = json.load(f)
        mlflow.log_dict(data, "scheduling_rules_config.json")

        points_active = data['point_system']['active']
        point_mapping = []
        active_attrs = []
        weekday_limits = {"Monday": {}, "Tuesday": {}, "Wednesday": {}, "Thursday": {}, "Friday": {}, "Saturday": {}, "Sunday": {}}
        for point_att in data['point_system']['attributes']:
            if point_att['active']:
                point_mapping.append(point_att)
                active_attrs.append(point_att['name'])
                for point_day, point_limit in point_att['point_limits'].items():
                    weekday_limits[point_day][point_att['name']] = point_limit
        if len(data['point_system']['ignore_cases']) != 0:
            point_ignore = data['point_system']['ignore_cases']
        else:
            point_ignore = []
        point_imp_order = data['point_system']['attribute_importance_order']
        added_slot_attr = data['point_system']['added_slot_attribute']
        for pt_att in point_imp_order:
            if pt_att not in active_attrs:
                raise Exception(f"Point attribute `{pt_att}` in importance order is either not active or not defined.")
        if added_slot_attr not in active_attrs:
            raise Exception(f"Point attribute `{pt_att}` set as added slot deciding attribute, but it is either not active or not defined.")
        for point_day, point_limit in data['point_system']['overall_limits'].items():
            weekday_limits[point_day]['overall'] = point_limit

        weekday_configs = []
        for weekday_name, point_dict in weekday_limits.items():
            attendings = []
            for attending in all_attendings:
                if weekday_name in attending.schedule:
                    attendings.append(attending)

            labs = []
            for lab in all_labs:
                if weekday_name in lab.schedule:
                    labs.append(lab)

            weekday_config = WeekdayConfig(weekday_name,
                                        labs,
                                        attendings,
                                        point_dict)

            weekday_configs.append(weekday_config)

        return weekday_configs, points_active, point_mapping, point_ignore, active_attrs, point_imp_order, added_slot_attr
    except:
        raise Exception("Problem parsing scheduling rules config.")


def parseCases(client, case_file, points_active, point_mapping, point_ignore):
    """
    Parses a case file into list of cases for the scheduler.
    """

    if case_file[-3:] == 'csv':
        with open(case_file, 'r') as csv_file:
            dict_reader = DictReader(csv_file)
            case_dicts = list(dict_reader)
    elif case_file[-4:] == 'json':
        with open(case_file) as json_file:
            case_dicts = json.load(json_file)
    else:
        try:
            case_list_json =  mlflow.artifacts.download_artifacts(run_id=case_file)+"/elective_cases.json"
            case_df = pd.read_json(case_list_json)
            with open(case_list_json) as json_file:
                case_dicts = json.load(json_file)
        except:
            case_gen_experiment = client.get_experiment_by_name("case_generator")
            case_gen_experiment_id = case_gen_experiment.experiment_id
            filter = "run_name = '"+case_file+"'"
            case_gen_run_results = client.search_runs(case_gen_experiment_id, filter)
            if len(case_gen_run_results) == 0:
                raise Exception("No matching case file found.")
            elif len(case_gen_run_results) > 1:
                raise Exception("More than 1 matching case file found.")
            else:
                case_gen_run_id = case_gen_run_results[0].info.run_id
            case_list_json =  mlflow.artifacts.download_artifacts(run_id=case_gen_run_id)+"/elective_cases.json"
            case_df = pd.read_json(case_list_json)
            with open(case_list_json) as json_file:
                case_dicts = json.load(json_file)

    case_list = []
    for case_dict in case_dicts:
        next_case = Case(case_dict, points_active, point_mapping, point_ignore)
        case_list.append(next_case)

    return case_list
