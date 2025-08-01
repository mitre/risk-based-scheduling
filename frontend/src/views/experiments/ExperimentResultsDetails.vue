<template>
    <div class="results" v-if="results.length == ids.length">
        <h1>Experiment Results</h1>
        <v-row justify="center" align="center" grid-list-md>
            <v-card 
                v-for="result in results" :key=result.run.id 
                class="grey lighten-4 mx-2"
                variant="tonal"
                min-height="100%"
                :title=result.run.parentRunInfo.runName_
                :subtitle="`Schedule: ${result.run.schedule}`"
            >
            </v-card>
        </v-row>
        <div class='charts'>
            
            <h2>Summary Metrics</h2>
            <h3>Mean Across All Experiment Instantiations</h3>
            <v-table>
                <thead>
                    <tr>
                        <th class="text-center">
                        Metric
                        </th>
                        <th class="text-center" v-for="result in results" :key=result.run.id>
                        {{ result.run.parentRunInfo.runName_ }}
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <tr
                        v-for="item, element in results[0].summaryMetrics"
                        :key="item.name"
                    >
                        <td>{{ item.name }}</td>
                        <td v-for="result in results" :key=result.run.id>{{ result.summaryMetrics[element].value }}</td>
                    </tr>
                </tbody>
            </v-table>
            
            <h2>Duration Plots</h2>
            <BoxPlot :title="'Count of Days With Time After EOD'" :boxplotData="compareBoxplotDataWeekdaysAfterEODCountMetrics" />
            <BoxPlot :title="'System Avg Daily Time After EOD (Minutes)'" :boxplotData="compareBoxplotDataSysAvgDailyMinAfterEODMetricsMetrics" />
            <h2>Risk Plots</h2>
            <BoxPlot :title="'Total Lab Minutes Spent at High Risk - pICU'" :boxplotData="compareBoxplotDataPicu" />
    </div>
    </div>
    <div v-else  class='loading'> 
        <p>Loading experiment details...</p>
        <v-row justify="center">
            <v-progress-circular
            indeterminate
            color=#42b983
            ></v-progress-circular>
        </v-row>
    </div>
</template>

<script>
import performHttpRequest from '../../composables/performHttpRequest.js'
import BoxPlot from '../../components/BoxPlot.vue'
import { ref, watchEffect } from 'vue'
import { DoughnutChart } from 'vue-chart-3';
import { useRouter } from "vue-router"

export default {
    components: { DoughnutChart, BoxPlot},
    setup() {

        const ids = useRouter().currentRoute.value.query.id
    
        const results = ref([])
        const compareBoxplotDataWeekdaysAfterEODCountMetrics = ref([])
        const compareBoxplotDataSysAvgDailyMinAfterEODMetricsMetrics = ref([])
        const compareBoxplotDataPicu = ref([])
        const compareBoxplotDataPae = ref([])
        
        for (const id of ids) {
            
            let run = null;
            let caseCountData = [];
            let summaryMetrics = [];


            const config = {
                method: 'get',
                url: '/api/simulation/get-sim-run-details?runId=' + id,
              }

             performHttpRequest(config)
                .then(response => {
                if (response.status === 200) {
                    run = response.data
                    caseCountData = { 
                        labels: ['Elective', 'Addon'],
                        datasets: [
                            {
                            data: [response.data.meanElectiveCount, response.data.meanAddonCount],
                            backgroundColor: ['#77CEFF', '#0079AF'],
                            },
                        ]
                    }

                summaryMetrics = [
                    {'name': 'Count of Weekdays After EOD', 'value': Number.parseFloat(response.data.meanWeekdaysAfterEOD).toFixed(1)},
                    {'name': 'System Avg Daily Minutes After EOD', 'value':  Number.parseFloat(response.data.meanSysAvgDailyTimeAfterEOD).toFixed(1)},
                    {'name': 'Total Lab Minutes Spent at High pICU Risk', 'value': Math.round(response.data.meanTotalHighPicuRiskLabMin)},
                ]

                compareBoxplotDataWeekdaysAfterEODCountMetrics.value.push({'x': run.parentRunInfo.runName_  , 'y': run.boxplotDataWeekdaysAfterEODCountMetrics[0].y})
                compareBoxplotDataSysAvgDailyMinAfterEODMetricsMetrics.value.push({'x': run.parentRunInfo.runName_  , 'y': run.boxplotDataSysAvgDailyMinAfterEODMetricsMetrics[0].y})
                compareBoxplotDataPicu.value.push({'x': run.parentRunInfo.runName_  , 'y': run.boxplotDataPicu[2].y})

                results.value.push({run: run, caseCountData: caseCountData, summaryMetrics: summaryMetrics})
            

                } 
            })  

        }


        return { 
            ids, 
            results, 
            compareBoxplotDataWeekdaysAfterEODCountMetrics,
            compareBoxplotDataSysAvgDailyMinAfterEODMetricsMetrics,
            compareBoxplotDataPicu, 
            compareBoxplotDataPae
        }

    }
    
}
</script>

<style>
.loading p {
  padding: 1.5em;
}

.charts h2 {
  padding-top: 3em;
}

.charts h3 {
  padding-top: 0.25em;
}

.charts v-table {
  padding-bottom: 5em;
}

.results h1  {
  padding-bottom: 1em;
}
</style>