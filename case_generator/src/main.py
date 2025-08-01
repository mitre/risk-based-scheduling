from generator import Generator
import utils
import picu
import argparse
import sys
import os
import mlflow
import json

MLFLOW_TRACKING_URI = os.getenv("MLFLOW_TRACKING_URI")

def main():
    """
    Runs Bayesian GRACE Synthetic Case Generator.
    """
    parser=argparse.ArgumentParser(description='Takes in SDV Model and population size and outputs synthetic cases for scheduler framework and risk-based simulation.')
    parser.add_argument('-sdv_model', type=str, help='Filepath to saved sdv model file that was trained on historical case data.')
    parser.add_argument('-pop_size', type=int, help='Number of synthetic cases to sample.')
    parser.add_argument('-randomize_samples', type=str, default=False, help='"True" = randomize samples, "False" = same sample.')
    parser.add_argument('-name', type=str, help='Name of outputted csv file with generated cases.')
    parser.add_argument('-description', type=str, default=None, help='Case population description.')
    parser.add_argument('-manual_files', type=str, nargs=2, default=None, help='Optional elective and add-on case json file strings to save in mlflow as a generator run.')
    parser_args = parser.parse_args()

    sdv_model = parser_args.sdv_model
    pop_size = parser_args.pop_size
    randomize_samples = True if parser_args.randomize_samples=='True' else False
    name = parser_args.name
    description = parser_args.description
    has_files = parser_args.manual_files != None
    if has_files:
        with open(parser_args.manual_files[0]) as json_file:
            elective_file = json.load(json_file)
        with open(parser_args.manual_files[1]) as json_file:
                    addon_file = json.load(json_file)

        elective_file = {'filename': parser_args.manual_files[0], 'json': elective_file}
        addon_file = {'filename': parser_args.manual_files[1], 'json': addon_file}
        upload_case_file(name, elective_file, addon_file, description)
    else:
        run_generator(sdv_model, pop_size, randomize_samples, name, has_files, description)

def run_generator(sdv_model, pop_size, randomize_samples, name, has_files, description=""):
    """
    Generates elective and add-on case files from trained sdv model. Logs files to MLFLOW.
    """
    mlflow.set_tracking_uri(MLFLOW_TRACKING_URI)

    mlflow.set_experiment("case_generator")
    with mlflow.start_run(run_name=name):
        mlflow.set_tag("run_type","case_generator")
        mlflow.set_tag("description", description)
        mlflow.log_param("name",name)
        mlflow.log_param("sdv_model",sdv_model)
        mlflow.log_param("pop_size",pop_size)
        mlflow.log_param("randomize_samples",randomize_samples)

        generator = Generator(sdv_model)
        generator.getSynthCases(pop_size, randomize_samples)
        mlflow.end_run()

def upload_case_file(name, elective_cases, addon_cases, description=""):
    """
    Uploads elective and add-on case files to MLFLOW.
    """
    mlflow.set_tracking_uri(MLFLOW_TRACKING_URI)

    mlflow.set_experiment("case_generator")
    with mlflow.start_run(run_name=name):

        mlflow.set_tag("run_type","case_generator")
        mlflow.set_tag("description", description)
        mlflow.log_param("name",name)
        mlflow.log_param("elective_cases_file", elective_cases["filename"])
        mlflow.log_param("addon_cases_file", addon_cases["filename"])

        mlflow.log_dict(elective_cases["json"], "elective_cases.json")
        mlflow.log_dict(addon_cases["json"], "addon_cases.json")
        mlflow.end_run()

if __name__=="__main__":
    main()


