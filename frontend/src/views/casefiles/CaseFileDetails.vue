/<template>
    <div class="inputs" v-if="run">
       <h1>{{ run.info_.runName_ }}</h1>
       <h2 v-if="description">Description</h2>
        <p v-if="description">{{ description }}</p>
        <h2>Inputs</h2>
        <v-row justify="center" align="center" grid-list-md>
        <v-card
          class="grey lighten-4 mx-2"
          variant="tonal"
          min-height="100%"
        >
          <p v-if="electiveFile">Elective Case File: {{ electiveFile }}</p>
          <p v-if="addonFile">Add-On Case File: {{ addonFile }}</p>
        </v-card>
      </v-row>
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
</template>

<script>
import performHttpRequest from '../../composables/performHttpRequest.js'
import { ref } from 'vue'

export default {
    props: ['id'],
    setup(props) {

        const run = ref(null)
        const description = ref(null)
        const electiveFile = ref(null)
        const addonFile = ref(null)

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

                    electiveFile.value = String(response.data.data_.params_.filter(function (param) {
                      return param.key_ == 'elective_cases_file'
                      }).map(function(param) {return param.value_ }))

                    addonFile.value = String(response.data.data_.params_.filter(function (param) {
                      return param.key_ == 'addon_cases_file'
                      }).map(function(param) {return param.value_ }))
                  } 
            })

        const schedDetails = ref(null)

        return { run, description, electiveFile, addonFile, schedDetails }

    }
    
}
</script>

<style>
.loading p {
  padding: 1.5em;
}

.inputs p {
  padding: 0.5em;
}

.inputs h2 {
  padding: 1em;
}

.charts h3 {
  padding-top: 3em;
}
</style>