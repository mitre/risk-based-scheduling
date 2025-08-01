from flask import Flask, request
from flask_cors import CORS
import os
import re
import main
import mlflow
import json
import jsonschema

app = Flask(__name__)
CORS(app)


@app.route("/run-generator", methods=['POST'])
def generate_cases():
    """
    Runs the case generator based on the provided inputs.
    """

    input = request.json
    app.logger.info(input)

    main.run_generator(input["sdv_model"], input["pop_size"], input["random"], input["name"], input["description"])

    return {"message": "Successfully ran case generator", "inputs": input}

@app.route("/get-case-buckets", methods=['GET'])
def get_case_buckets():
    """
    Get all generated case bucket names from MLFlow.
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
        .search_runs(experiment_names=["case_generator"])
        .rename(columns = _MLFLOW_COLUMNS)
        .filter(items = list(_MLFLOW_COLUMNS.values()))
        .to_dict(orient="records")
    )

@app.route("/upload-case-bucket", methods=['POST'])
def upload_case_bucket():
    """
    Upload case bucket from existing json files to MLFlow.
    """
    app.logger.info(request)
    input = request.json

    main.upload_case_file(input["name"], input["electiveFile"], input["addonFile"], input["description"])

    return {"message": "Successfully uploaded case file", "inputs": input, "status": 500 }

@app.route("/get-sdv-models", methods=['GET'])
def get_sdv_models():
    """
    Get all trained sdv models in working directory.
    """

    files = [f for f in os.listdir('.') if os.path.isfile(f)]
    sdv_models = []
    for file in files:
        if re.search(".pkl$", file):
            sdv_models.append({'name':file, 'value': file})

    return {"sdv_models": sdv_models}

@app.route("/validate-case-file", methods=['POST'])
def validate_case_file():
    """
    Validates case file instance against case file config json schema.
    """

    return validate_file(request.json, 'resources/case_file_schema.json')

@app.route("/get-template", methods=['POST'])
def get_template():
   """
   Gets json for case file template.
   """

   if request.json['file_type'] == 'schema':
    template_filepath = 'resources/case_file_schema.json'
   elif request.json['config_type'] == 'elective':
    template_filepath = 'resources/elective_case_file_template.json'
   elif request.json['config_type'] == 'addon':
    template_filepath = 'resources/addon_case_file_template.json'
   else:
     return {"template": "Not a valid template type."}

   return get_template_json(template_filepath)

def validate_file(instance, schema_filepath):

    # Opening JSON file
    with open(schema_filepath) as file:

        # return JSON object as a dictionary
        schema = json.load(file)

         # validate instance against schema
        validator = jsonschema.Draft7Validator(schema)
        errors = [{"message": error.message} for error in validator.iter_errors(instance)]
        valid = True if len(errors) == 0 else False

        if len(errors) > 7:
            errors = errors[:7]

    return {"valid": valid, "error": errors}

def get_template_json(template_filepath):

    # Opening JSON file
    with open(template_filepath) as file:

        # return JSON object as a dictionary
        template = json.load(file)

    return {"template": template}