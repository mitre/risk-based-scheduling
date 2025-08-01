from scheduler import Scheduler
from board import Board
from algorithm import Algorithm
import mlflow
from mlflow import MlflowClient
from configsParser import *
import argparse
import os
import sys
import datetime
import numpy as np
import copy
import statistics

MLFLOW_TRACKING_URI = os.getenv("MLFLOW_TRACKING_URI")
DAYS_PER_WEEK = 7

def main():
    """
    Runs the Bayesian GRACE Scheduler Framework.
    """
    parser=argparse.ArgumentParser(description='Takes in schedule rules, case list, and scheduling algorithm and outputs populated schedule.')
    parser.add_argument('-config_file', type=str, help='Filepath to json config file that specifies scheduling rules.')
    parser.add_argument('-schedule_name', type=str,  help='MLFlow name for schedule.')
    parser.add_argument('-iters', type=int, default=1, help='Number of times to run the scheduler. Defaults to 1 if not specified.')
    parser.add_argument('-description', type=str, default=None, help='Schedule description.')
    parser_args = parser.parse_args()
    
    config_file = parser_args.config_file
    schedule_name = parser_args.schedule_name
    iters = parser_args.iters

    run_scheduler(config_file, schedule_name, iters, description="")

def run_scheduler(config_file, schedule_name, iters, description="", inputs=None):
    """
    Runs the scheduling components of the Bayesian GRACE Scheduler Framework.
    Starts up the MLflow run and logs parent run metrics and parameters.
    Then makes calls to parse config files, instantiate necessary scheduler objects,
    schedule cases, reorder if necessary, and log child run metrics and schedules.
    """
    rng_shuffle = np.random.default_rng(seed=42)

    client = MlflowClient()
    mlflow.set_tracking_uri(MLFLOW_TRACKING_URI)

    mlflow.set_experiment("scheduler")

    with mlflow.start_run(run_name=schedule_name, nested=True):
        # Split and name outputs
        start_date, end_date, case_arrivals, case_file, algo_name, json_file, weekdays, labs_open, points_active, point_mapping, point_ignore, active_attrs, point_imp_order, added_slot_attr, reorder_cases = parseConfig(config_file, inputs)
        orig_case_list = parseCases(client, case_file, points_active, point_mapping, point_ignore)
        algo_class = getattr(sys.modules['algorithm'], algo_name)
        algorithm = algo_class(algo_name)

        # Log parent schedule run tags and params
        mlflow.set_tag("run_type","scheduler_parent")
        mlflow.set_tag("description", description)
        mlflow.log_param("case_file",case_file)
        mlflow.log_param("algo_name",algo_name)
        mlflow.log_param("name",schedule_name)
        mlflow.log_param("iters",iters)

        metric_lists = {'unscheduled_overall_cases': [], 'added_slot_scheduled_cases': [], 'total_forcibly_scheduled_cases': []}
        for cat in active_attrs:
            metric_lists['forcibly_scheduled_over_'+cat+'_cases'] = []
        if len(active_attrs) > 1:
            metric_lists['forcibly_scheduled_over_all_cases'] = []

        dow_dist_dict = dict(zip(["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], [[], [], [], [], [], [], []]))
        for i in range(iters):
            child_name = schedule_name+'_child_'+str(i+1)
            case_list = copy.deepcopy(orig_case_list)
            with mlflow.start_run(run_name=child_name,nested=True):
                # Shuffle case list, from which arrivals will be pulled
                case_array = np.array(case_list)
                rng_shuffle.shuffle(case_array)
                case_list = case_array.tolist()

                # Create board and schedule cases
                board = Board(start_date, end_date, case_arrivals, weekdays, labs_open, point_ignore, active_attrs, point_imp_order, added_slot_attr)
                case_dict = {}
                scheduleWithArrivalRates(board, case_list, start_date, algorithm, i, case_dict)

                # Perform post-scheduling prints, loggings, and case reordering if applicable
                print(f"Finished scheduling cases for iteration {i+1}. Logging scheduling metrics to mlflow.")
                recordIterationMetrics(board, metric_lists)
                for d in board.sched_dist:
                    num_on_day = board.getScheduleDist(d)
                    print(str(num_on_day) + " scheduled on " + d + "s")
                    dow_dist_dict[d].append(num_on_day)
                    mlflow.log_metric(d.lower()+'_count', num_on_day)
                if reorder_cases['whether']:
                    print(f"Rearranging cases within days according to {reorder_cases['what']}.")
                    board.reorderCasesWithinDays(reorder_cases['what'])
                print("Saving schedule and logging parameters to mlflow.")
                board.exportScheduleJSON(json_file, schedule_name, i)
                mlflow.set_tag("run_type","scheduler_child")
                mlflow.log_param("case_file",case_file)
                mlflow.log_param("algo_name",algo_name)
                mlflow.log_param("parent_name",schedule_name)
                mlflow.log_param("name",child_name)
        for m in metric_lists.keys():
            mlflow.log_metric(m, statistics.mean(metric_lists[m]))
        for d in dow_dist_dict:
            mlflow.log_metric(d.lower()+'_count', statistics.mean(dow_dist_dict[d]))
        print('Average schedule metrics saved to mlflow.')

def recordIterationMetrics(board, metric_lists):
    """
    Records metrics for the iteration and tracks them for eventual computation of parent run average metrics.
    """
    count_metrics = board.getCountMetrics()
    for m, c in count_metrics.items():
        metric_lists[m].append(c)
        mlflow.log_metric(m, c)
        print(f"{m.replace('_', ' ').capitalize()}: {c}")

def scheduleWithArrivalRates(board, case_list, start_date, algorithm, iter, case_dict):
    """
    Has cases arrive one day at a time, excluding weekends, according to the config-specified distribution.
    Instantiates Scheduler and calls the scheduling method of the designated algorithm.
    """
    rng_arrivals = np.random.default_rng(seed=iter)
    scheduling_start_date = start_date - datetime.timedelta(board.warmup_days)
    next_case_to_arrive = 0
    for day_number in range(len(board.days) - ((board.case_arrivals['time_window'] + board.case_arrivals['lead_weeks']) * DAYS_PER_WEEK)):
        case_arrival_date = scheduling_start_date + datetime.timedelta(days=day_number)
        dow_key = case_arrival_date.weekday()
        dow = board.dow_list[dow_key]
        if dow != "Saturday" and dow != "Sunday":
            if board.case_arrivals['distribution'] == "Uniform":
                # for np.random.integers: low is inclusive and high is exclusive
                number_cases_arrived = rng_arrivals.integers(board.case_arrivals['params'][0],board.case_arrivals['params'][1]+1)
            elif board.case_arrivals['distribution'] == "Poisson":
                number_cases_arrived = rng_arrivals.poisson(lam=board.case_arrivals['params'][0])
            cases_arrived = case_list[next_case_to_arrive:next_case_to_arrive+number_cases_arrived]
            for c in cases_arrived:
                case_dict[c] = {'arrival_dow': case_arrival_date}
            print(str(len(cases_arrived)) + " cases arrived on "+dow+" "+str(case_arrival_date))
            next_case_to_arrive += number_cases_arrived
            for case in cases_arrived:
                case.arrival_scheduler_day = day_number
            scheduler = Scheduler(board, cases_arrived, algorithm)
            scheduler.populateBoard()

if __name__=="__main__":
    main()
