import pandas as pd
import json
import mlflow
import random
import numpy as np

def getScheduledCases(synth_cases):
    """
    Returns all synthetically generated scheduled cases (addon=0).
    """
    scheduled_df = synth_cases[synth_cases['addon']==0].reset_index(drop=True)
    scheduled_df['attending'] = [None for i in range(len(scheduled_df))]

    return scheduled_df


def getAddonCases(synth_cases):
    """
    Returns all synthetically generated add-on cases (addon=1).
    """
    addon_df = synth_cases[synth_cases['addon']==1].reset_index(drop=True)
    addon_df['attending'] = [None for i in range(len(addon_df))]

    return addon_df

def casesToJSON(cases_df):
    """
    Returns cases in JSON format with correct attribute names.
    """
    cases = []
    for i in range(len(cases_df)):
        case_dict = {'procedure':int(cases_df['procedure'][i]),'adverseScore':cases_df['adverseScore'][i], 'riskScore': cases_df['riskScore'][i],'addon': True if cases_df['addon'][i] == 1 else False, 'priorLocation': cases_df['prior_loc'][i], 'pICU': cases_df['pICU'][i], 'durationScore': int(cases_df['durationScore'][i])}
        cases.append(case_dict)

    return cases

def exportCasesJSON(elective_cases, addon_cases):
    """
    Exports json of addon_bucket for use in Risk-Based Simulation.
    """
    elective_cases_json = casesToJSON(elective_cases)
    addon_cases_json = casesToJSON(addon_cases)

    mlflow.log_dict(elective_cases_json, "elective_cases.json")
    mlflow.log_dict(addon_cases_json, "addon_cases.json")

def setDurationScores(cases):
    """
    Adds a duration score for every case based on the procedure type and the distribution of scores for each risk score.
    This model can be modified/exchanged.
    """
    durationScores = []
    for i in range(len(cases)):
        if cases.loc[i, 'procedure'] in [0, 1]:
            durationScores.append(1)
        else:
            prob = random.uniform(0,1)
            riskScore = cases.loc[i, 'riskScore']
            if riskScore == 1:
                if prob <= .9221:
                    durationScores.append(1)
                elif prob > 1 - .0042:
                    durationScores.append(2)
                else:
                    durationScores.append(3)
            elif riskScore == 2:
                if prob <= .5:
                    durationScores.append(1)
                elif prob > 1 - .1713:
                    durationScores.append(2)
                else:
                    durationScores.append(3)
            elif riskScore == 3:
                if prob <= .1659:
                    durationScores.append(1)
                elif prob > 1 - .4773:
                    durationScores.append(2)
                else:
                    durationScores.append(3)
            elif riskScore == 4:
                if prob <= .4599:
                    durationScores.append(1)
                elif prob > 1 - .3613:
                    durationScores.append(2)
                else:
                    durationScores.append(3)
            else:
                if prob <= .1189:
                    durationScores.append(1)
                elif prob > 1 - .8445:
                    durationScores.append(2)
                else:
                    durationScores.append(3)
    cases['durationScore'] = durationScores

    return cases