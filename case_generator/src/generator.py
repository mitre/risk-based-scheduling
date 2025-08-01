import pandas as pd
from sdv.tabular import CopulaGAN
import utils
import picu


class Generator:
    def __init__(self, sdv_model):
        """
        Constructor class that instantiates Generator.
        """
        self.sdv_model = sdv_model

    def getSynthCases(self, pop_size, randomize_samples=False):
        """
        Loads sdv_model and samples n=pop_size number of cases. To generate the same synthetic data (for repeatability), set randomize_samples=False.
        For randomized samples, set randomize_samples=True. Defaults to False.
        """
        loaded = CopulaGAN.load(self.sdv_model)
        synth_cases = loaded.sample(num_rows=pop_size,randomize_samples=randomize_samples)
        # Can remove if trained model pkl has the updated feature names
        synth_cases.rename(columns={'icatch': 'riskScore', 'predict': 'adverseScore', 'radRisk': 'durationScore'}, inplace=True)

        elective_cases = utils.getScheduledCases(synth_cases)
        addon_cases = utils.getAddonCases(synth_cases)

        # Can remove these if synthetic cases are created with risk scores
        elective_cases = picu.getProbICU(elective_cases)
        addon_cases = picu.getProbICU(addon_cases)

        # Can remove these if synthetic cases are created with duration scores
        elective_cases = utils.setDurationScores(elective_cases)
        addon_cases = utils.setDurationScores(addon_cases)

        utils.exportCasesJSON(elective_cases, addon_cases)




