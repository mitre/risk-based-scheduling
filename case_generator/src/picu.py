import math

def getProbICU(cases_df):
    """
    Gets the risk level associated with the with the elective or add-on case..
    This model calculates pICU from variables available at time of scheduling. This model can be modified/exchanged.
    """
    risk_map = {1:0, 2:0.6081, 3:0.8386, 4:1.0212, 5:1.8527}
    age_cat_map = {0:0, 1:1.0296}
    sys_illness_map = {0:0, 1:1.1763}
    phys_cat_map = {1:0, 2:0.7543, 3:1.557}
    last_interv_map = {0:0, 1:0.862}

    picu_data = []
    for i in range(len(cases_df)):
        logit = (-4.0769) + risk_map[cases_df['riskScore'][i]] + age_cat_map[int(cases_df['age_cat'][i])] + sys_illness_map[cases_df['sys_illness'][i]] + phys_cat_map[int(cases_df['phys_cat'][i])] + last_interv_map[cases_df['last_interv'][i]]
        odds = math.exp(logit)
        pICU = (odds)/(1+odds)
        picu_data.append(pICU)

    cases_df['pICU'] = picu_data

    return cases_df
