/<template>
    <div v-if="run">
      <div class="inputs" v-if="run">
        <h1>{{ run.info_.runName_ }}</h1>
        <h2 v-if="description">Description</h2>
        <p v-if="description">{{ description }}</p>
        <h2>Inputs</h2>
        <v-row justify="center" align="center" grid-list-lg>
        <v-card
          class="grey lighten-4 mx-2"
          variant="tonal"
          min-height="100%"
        >
          <p v-if="algoName">Heuristic: {{ algoName }}</p>
          <p v-if="numIters">Number of Iterations: {{ numIters }}</p>
        </v-card>
      </v-row>
    </div>
        <div div v-if="series" class='charts'>
            <h3>Daily Complexity</h3>
            <div v-for="schedDetail in schedDetails" :key="schedDetail.run_data.run_id">
                <ScheduleHeatMap :schedDetail="schedDetail"/>
            </div>
        </div>
        <div v-else  class='loading'> 
        <p>Loading case files details...</p>
        <v-row justify="center">
            <v-progress-circular
            indeterminate
            color=#42b983
            ></v-progress-circular>
        </v-row>
    </div>
    </div>
    <div v-else  class='loading'> 
        <p>Loading schedule details...</p>
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
import { ref, watchEffect } from 'vue'
import ScheduleHeatMap from '../../components/ScheduleHeatMap.vue'

export default {
    props: ['id'],
    components: { ScheduleHeatMap  },
    setup(props) {

        const run = ref(null)
        const description = ref(null)
        const algoName = ref(null)
        const numIters = ref(null)

        const config = {
                method: 'get',
                url: '/api/simulation/get-sim-run?runId=' + props.id,
              }

        performHttpRequest(config)
                .then(response => {
                  if (response.status === 200) {
                    run.value = response.data

                    description.value = String(response.data.data_.tags_.filter(function (tag) {
                      return tag.key_ == 'description'
                      }).map(function(tag) {return tag.value_ }))

                    algoName.value = String(response.data.data_.params_.filter(function (param) {
                      return param.key_ == 'algo_name'
                      }).map(function(param) {return param.value_ }))

                    numIters.value = String(response.data.data_.params_.filter(function (param) {
                      return param.key_ == 'iters'
                      }).map(function(param) {return param.value_ }))
                  } 
            })

        const schedDetails = ref(null)
        const series = ref(null)

         watchEffect( () => {
            performHttpRequest({
                method: 'get',
                url: '/api/scheduler/get-sched-details?parentRunId=' + props.id,
              })
                .then(response => {
                  if (response.status === 200) {
                    schedDetails.value = response.data
                    series.value = schedDetails.value[0]['heatmap']

                } else {
                    console.log('error')
                }
            })
         })



        return { run, series, description, algoName, numIters, schedDetails }

    }
    
}
</script>

<style>
.loading p {
  padding: 1.5em;
}

.inputs h2 {
  padding: 0.5em;
}

.inputs p {
  padding: 0.5em;
}

.charts h3 {
  padding-top: 3em;
}
</style>