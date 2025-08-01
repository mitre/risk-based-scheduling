/<template>
  <v-row justify="center">
    <v-dialog
      v-model="showDialog"
      persistent
      width="auto"
    >
      <v-card>
        <v-card-title>
          <span class="text-h5">New Experiment</span>
        </v-card-title>
          <v-card-text style="width: 400px;">
            <v-container>
              <v-col
              cols="12"
              >
              <div v-if="!runningSim">
                <v-text-field
                        v-model="experimentName"
                        label="Experiment name"
                        required
                    ></v-text-field>

                    <v-select
                        label="Schedule"
                        v-model="selectedSchedule"
                        :items="schedules"
                        item-title="name"
                        item-value="id"
                        required
                    ></v-select>

                    <v-textarea
                        v-model="description"
                        height=3
                        label="Short description of schedule..."
                        :rules="descriptionRules"
                    ></v-textarea>

                    <v-text-field
                        v-model="numIterations"
                        label="Number of simulation iterations"
                        :rules="iterationsRules"
                        required
                        placeholder="10"
                    ></v-text-field>

                    <v-text-field
                        v-model="numExtraDays"
                        label="Number of extra simulation days"
                        :rules="integerRules"
                        required
                        placeholder="0"
                    ></v-text-field>

                    <v-text-field
                        v-model="startSeed"
                        label="Start seed"
                        :rules="integerRules"
                        required
                        placeholder="0"
                    ></v-text-field>

                    <v-file-input
                            v-model="simConfigFile"
                            label="Upload simulation config json"
                            accept=".json"
                            required
                        ></v-file-input>
                        <div v-if="configValidationError">
                          <div v-for="error in configValidationError" :key="error.message" class="validation-error">
                              <v-card class="bg-red">{{ error.message }}</v-card>
                          </div>
                        </div>
                        <v-btn
                          color="indigo"
                          size="small"
                          variant="tonal"
                          block
                          @click="downloadConfig('template')"
                          class="download-btn"
                        >
                          Download Configuration Template
                          <template v-slot:append>
                            <v-icon class="primary--text">mdi-download</v-icon>
                          </template>  
                        </v-btn>
                        <v-btn
                          color="indigo"
                          size="small"
                          variant="tonal"
                          block
                          @click="downloadConfig('schema')"
                          class="download-btn"
                        >
                          Download Configuration Schema
                          <template v-slot:append>
                            <v-icon class="primary--text">mdi-download</v-icon>
                          </template>  
                        </v-btn>
                   <div v-if="invalid" class="bg-red">
                    <p>Populate all required fields.</p>
                  </div>
                </div>
                <div v-else>
                  <div v-if="simResponse" class="bg-green">
                      <p>{{ simResponse }}</p>
                  </div>
                  <div v-else-if="simError" class="bg-red">
                      <p>{{ simError }}</p>
                  </div>
                  <div v-else>
                    <v-row justify="center">
                      <p>Experiment was added to the job queue.</p>
                    </v-row>

                  </div>
                </div>
              </v-col>
          </v-container>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <div v-if="!runningSim">
            <v-btn
              color="green-darken-1"
              variant="text"
              @click="$emit('close')"
            >
              Cancel
            </v-btn>
            <v-btn
              color="green-darken-1"
              variant="text"
              @click="handleSubmit"
            >
              Submit
            </v-btn>
        </div>
        <div v-else>
          <div>
              <v-btn
              color="green-darken-1"
              variant="text"
              @click="$emit('close')"
            >
              Close
            </v-btn>
          </div>
        </div>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-row>
</template>


<script>
import performHttpRequest from '../composables/performHttpRequest.js'
import extractFileContent from '../composables/extractFileContent.js'
import download from 'downloadjs'
import { ref, computed, watchEffect } from 'vue'

export default {
    props: { showModal: { type: Boolean, required: true } },
    setup(props) {
        const showDialog = ref(props.showModal)
        const runningSim = ref(false)
        
        const experimentName = ref('')
        const description = ref('')
        const numIterations = ref(10)
        const numExtraDays = ref(0)
        const startSeed = ref(0)
        const folderName = computed(() => {return experimentName.value})
        const selectedSchedule = ref('')
        const simConfigFile = ref(null)
        const simConfigJson = ref(null)

        const integerRules =  ref([
            value => {
            if  ( /^-?\d+$/.test(value) ) return true

            return 'Number of iterations must be an integer.'
            },
      ])

      const iterationsRules =  ref([
        value => {
        if  ( /^-?\d+$/.test(value) && value > 0) return true

        return 'Must be an integer greater than 0.'
        },
      ])

      const descriptionRules =  ref([
          value => {
            if (value.length <= 100) return true
            return 'Max 100 characters'
          },
        ])

      const schedules = ref([])
      const config = {
                method: 'get',
                url: '/api/scheduler/get-pop-schedules',
                timeout: 3600000,
              }

      performHttpRequest(config)
                .then(response => {
                  if (response.status === 200) {
                    schedules.value = response.data
                  }
            })

      const downloadConfig = (file_type) => {
        const downloadTemplate = {
          method: 'post',
          url: '/api/simulation/get-template',
          data: { file_type: file_type },
        } 

  
        performHttpRequest(downloadTemplate)
                .then(response => {
                    if (response.status === 200) {
                      download(JSON.stringify(JSON.parse(response.data.template)), 'config_'+file_type+'.json', "text/plain")
                    } else {
                      console.log(response)
                }
            })
        }

      const configValidationError = ref(null);
      watchEffect( () => { 

              if (simConfigFile.value && simConfigFile.value.length >= 1) {

                extractFileContent(simConfigFile.value[0]).then(result => {

                  try {
                    simConfigJson.value = JSON.parse(result.response)

                    const validateConfig = {
                            method: 'post',
                            url: '/api/simulation/validate-config',
                            data: simConfigJson.value,
                        }

                    
                    performHttpRequest(validateConfig)
                            .then(response => {
                                if (response.status === 200) {
                                  configValidationError.value = response.data
                                } else {
                                  console.log(response)
                            }
                        })
                    } catch (error) {
                    
                      configValidationError.value = [error]
                  
                  }
                
                })
              }
      })

        const simResponse = ref(null)
        const simError = ref(null)
        const submittedJob = ref(false)
        const invalid = ref(false)
        const handleSubmit = () => {

          if (numIterations.value && selectedSchedule.value && experimentName.value && simConfigFile.value) {

            extractFileContent(simConfigFile.value[0]).then(result => {
                simConfigJson.value = JSON.parse(result.response)
                })


            watchEffect( () => { 
              
                
                const data = {
                  iterations: numIterations.value,
                  sched: selectedSchedule.value,
                  seed: startSeed.value,
                  folderName: folderName.value,
                  extraDays: numExtraDays.value,
                  expName: experimentName.value,
                  description: description.value,
                  configData: simConfigJson.value,
                  }
                

              const config = {
                  method: 'post',
                  url: '/api/simulation/submit',
                  data: data,
                }

                if (!submittedJob.value) {

                  performHttpRequest(config)
                    .then(response => {
                      if (response.status === 200) {
                        simResponse.value = 'Experiment was added to the job queue'
                      } else {
                    simError.value = 'There was a problem running the simulation: Status '+response.status
                      }
                  })

                  submittedJob.value = true



                }
                
                
            })
            
            runningSim.value = true
          } else {
            invalid.value = true
          }

        }

        

        return { 
          showDialog,
          downloadConfig, 
          experimentName, 
          selectedSchedule, 
          description, 
          numIterations, 
          numExtraDays, 
          startSeed, 
          folderName,
          simConfigFile, 
          integerRules,
          iterationsRules,
          descriptionRules, 
          schedules, 
          handleSubmit, 
          runningSim, 
          simResponse, 
          simError,
          invalid,
          configValidationError,
        }
    }
}
</script>

<style>
    .modal {
        width: 600px;
        height: 700px;
        padding: 30px;
        margin: 100px auto;
        background: white;
        border-radius: 10px;
    }
    .backdrop {
        top: 0;
        position: fixed;
        background: rgba(0,0,0,0.5);
        width: 100%;
        height: 100%;
    }

    .modal h1 {
        color: #42b983;
        padding: 20px;
    }

    .modal p {
        font-style: normal;
    }
    
    .download-btn {
      margin-bottom: 1em
    }

    .validation-error {
      padding-bottom: 0.5em
    }
</style>