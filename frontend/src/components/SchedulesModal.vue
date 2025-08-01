/<template>
  <v-row justify="center">
    <v-dialog
      v-model="showDialog"
      persistent
      width="auto"
    >
      <v-card>
        <v-card-title>
          <span class="text-h5">New Schedule</span>
        </v-card-title>
          <v-card-text style="width: 800px;">
            <v-container>
              <v-col
              cols="12"
              >
              <div v-if="!runningScheduler">
                <v-row>
                    <v-col cols="12" md="6">
                        <v-text-field
                            v-model="scheduleName"
                            label="Schedule name"
                            required
                        ></v-text-field>
                    </v-col>
                </v-row>
                <v-row>
                    <v-col cols="12" md="12">
                        <v-text-field
                            v-model="description"
                            label="Short description of schedule..."
                            :rules="descriptionRules"
                        ></v-text-field>
                    </v-col>
                </v-row>
                <v-row>
                  <v-col cols="12" md="6">
                        <v-select
                            label="Case file"
                            v-model="selectedCaseFile"
                            :items="caseFiles"
                            item-title="name"
                            item-value="id"
                            required
                        ></v-select>
                    </v-col>
                    <v-col cols="12" md="6">
                        <v-text-field
                            v-model="iterations"
                            label="Number of schedule iterations"
                            :rules="iterationsRules"
                            required
                            placeholder="10"
                        ></v-text-field>
                    </v-col>
                </v-row>
                <v-row>
                    <v-col cols="12" md="6">
                        <v-text-field
                            type="date"
                            v-model="startDate"
                            label="Start date"
                            required
                        ></v-text-field>
                    </v-col>
                    <v-col cols="12" md="6">
                        <v-text-field
                            type="date"
                            v-model="endDate"
                            label="End date"
                            required
                        ></v-text-field>
                    </v-col>
                </v-row>
                <v-row>
                  <v-col cols="12" md="6">
                        <v-select
                            v-model="selectedArrDistrib"
                            :items="arrDistribs"
                            label="Case arrival distribution"
                            required
                            placeholder="Poisson"
                        ></v-select>
                    </v-col>
                    <v-col cols="12" md="6" v-if="selectedArrDistrib == 'Poisson'">
                        <v-text-field
                            v-model="lambdaDailyArr"
                            label="Rate of daily arrivals"
                            :rules="numberRules"
                            required
                            placeholder="3.5"
                        ></v-text-field>
                    </v-col>
                    <v-col cols="12" md="3" v-if="selectedArrDistrib == 'Uniform'">
                        <v-text-field
                            v-model="minDailyArr"
                            label="Min daily arrivals"
                            :rules="integerRules"
                            required
                            placeholder="3.5"
                        ></v-text-field>
                    </v-col>
                    <v-col cols="12" md="3" v-if="selectedArrDistrib == 'Uniform'">
                        <v-text-field
                            v-model="maxDailyArr"
                            label="Max daily arrivals"
                            :rules="integerRules"
                            required
                            placeholder="6"
                        ></v-text-field>
                    </v-col>
                </v-row>
                <v-row>
                    <v-col cols="12" md="6">
                         <v-text-field
                            v-model="timeWindow"
                            label="Scheduling time window (in weeks)"
                            :rules="integerRules"
                            required
                            placeholder="8"
                        ></v-text-field>
                    </v-col>
                    <v-col cols="12" md="6">
                        <v-text-field
                            v-model="leadTime"
                            label="Scheduling lead time (in weeks)"
                            :rules="integerRules"
                            required
                            placeholder="8"
                        ></v-text-field>
                    </v-col>
                </v-row>
                <v-row>
                    <v-col cols="12" md="6">
                         <v-select
                            v-model="selectedAlgoName"
                            :items="algoNames"
                            label="Scheduling heuristic"
                            required
                            placeholder="Randomly"
                        ></v-select>
                    </v-col>
                    <v-col cols="12" md="6">
                        <v-checkbox
                          v-model="reorderCases"
                          label="Order cases within labs"
                          color="primary"
                      ></v-checkbox>
                    </v-col>
                </v-row>
                <v-row>
                    <v-col cols="12" md="6" align="center">
                        <v-file-input
                            v-model="schedulingRulesFile"
                            label="Upload scheduling rules json"
                            accept=".json"
                            required
                        ></v-file-input>
                        <div v-if="schedulingRulesValidationError">
                          <div v-for="error in schedulingRulesValidationError" :key="error.message" class="validation-error">
                              <v-card class="bg-red">{{ error.message }}</v-card>
                          </div>
                        </div>
                        <v-btn
                        color="indigo"
                        size="small"
                        variant="tonal"
                        block
                        @click="downloadConfig('scheduling_rules','template')"
                        class="download-btn"
                      >
                        Download Scheduling Rules Template  
                        <template v-slot:append>
                            <v-icon class="primary--text">mdi-download</v-icon>
                        </template>  
                      </v-btn>
                      <v-btn
                        color="indigo"
                        size="small"
                        variant="tonal"
                        block
                        @click="downloadConfig('scheduling_rules','schema')"
                        class="download-btn"
                      >
                        Download Scheduling Rules Schema
                        <template v-slot:append>
                            <v-icon class="primary--text">mdi-download</v-icon>
                        </template>  
                      </v-btn>
                    </v-col>
                    <v-col cols="12" md="6">
                        <v-file-input
                            v-model="labConfigFile"
                            label="Upload lab configuration json"
                            accept=".json"
                            required
                        ></v-file-input>
                        <div v-if="labConfigValidationError">
                          <div v-for="error in labConfigValidationError" :key="error.message" class="validation-error">
                              <v-card class="bg-red">{{ error.message }}</v-card>
                          </div>
                        </div>
                        <v-btn
                          color="indigo"
                          size="small"
                          variant="tonal"
                          block
                          @click="downloadConfig('lab_config','template')"
                          class="download-btn"
                        >
                          Download Lab Configuration Template
                          <template v-slot:append>
                            <v-icon class="primary--text">mdi-download</v-icon>
                          </template>  
                        </v-btn>
                        <v-btn
                          color="indigo"
                          size="small"
                          variant="tonal"
                          block
                          @click="downloadConfig('lab_config','schema')"
                          class="download-btn"
                        >
                          Download Lab Configuration Schema
                          <template v-slot:append>
                            <v-icon class="primary--text">mdi-download</v-icon>
                          </template>  
                        </v-btn>
                    </v-col>
                </v-row>
                <v-row>
                  <v-col cols="12" md="12">
                    <div v-if="invalid" class="bg-red">
                      <p>Populate or correct all required fields.</p>
                    </div>
                  </v-col>
                </v-row>
                </div>
                <div v-else>
                  <div v-if="schedulerResponse" class="bg-green">
                      <p>{{ schedulerResponse }}</p>
                  </div>
                  <div v-else-if="schedulerError" class="bg-red">
                      <p>{{ schedulerError }}</p>
                  </div>
                  <div v-else>
                    <v-row justify="center">
                      <p>Running scheduler...</p>
                    </v-row>
                    <v-row justify="center">
                      <v-progress-circular
                        indeterminate
                        color=#42b983
                      ></v-progress-circular>
                    </v-row>
                  </div>
                </div>
              </v-col>
          </v-container>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <div v-if="!runningScheduler">
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
          <div v-if="schedulerResponse || schedulerError">
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
import { ref, watchEffect } from 'vue'
import download from 'downloadjs'

export default {
    props: { showModal: { type: Boolean, required: true } },
    setup(props) {
        const showDialog = ref(props.showModal)
        const runningScheduler = ref(false)
        
        const scheduleName = ref('')
        const iterations = ref('10')
        const startDate = ref('2018-01-01')
        const endDate = ref('2018-12-31')
        const minDailyArr = ref(4)
        const maxDailyArr = ref(6)
        const lambdaDailyArr = ref(3.5)
        const timeWindow = ref(8)
        const leadTime = ref(8)
        const description = ref('')
        const selectedAlgoName = ref('Randomly')
        const selectedArrDistrib = ref('Poisson')
        const selectedPointsMetrics = ref([])
        const reorderCases = ref(false)
        const selectedOrderingMetric = ref(null)
        const selectedOrderingType = ref('Labs alternating low-to-high and high-to-low')
        const selectedCaseFile = ref(null)
        const labConfigFile = ref(null)
        const labConfigJson = ref(null)
        const schedulingRulesFile = ref(null)
        const schedulingRulesJson = ref(null)

      const integerRules =  ref([
        value => {
        if  ( /^-?\d+$/.test(value) ) return true

        return 'Must be an integer'
        },
      ])

      const iterationsRules =  ref([
        value => {
        if  ( /^-?\d+$/.test(value) && value > 0) return true

        return 'Must be an integer greater than 0.'
        },
      ])

      const numberRules =  ref([
        value => {
          if ( typeof value == 'number') return true
          if ( !value.split('.').filter( (part) => /^-?\d+$/.test(part) == false && part != '' ).length ) return true
          return 'Must be a number'
        },
      ])

      const descriptionRules =  ref([
        value => {
          if (value.length <= 100) return true
          return 'Max 100 characters'
        },
      ])

      const algoNames = ref(['Randomly', 'Points', 'PointsSplit'])
      const arrDistribs = ref(['Uniform', 'Poisson'])
      const pointsMetrics = ref(['Procedure Duration', 'pICU Risk'])
      const orderingTypes = ref(['All labs lowest to highest', 'All labs highest to lowest', 'Labs alternating low-to-high and high-to-low'])

      const caseFiles = ref([])
      const config = {
                method: 'get',
                url: '/api/case-gen/get-case-buckets',
              }

      performHttpRequest(config)
                .then(response => {
                  if (response.status === 200) {
                    caseFiles.value = response.data
                  }
            })
      
      const downloadConfig = (config_type, file_type) => {
        
        const downloadTemplate = {
          method: 'post',
          url: '/api/scheduler/get-template',
          data: { config_type: config_type, file_type: file_type },
        } 

  
        performHttpRequest(downloadTemplate)
                .then(response => {
                    if (response.status === 200) {
                      download(JSON.stringify(response.data.template), config_type+'_'+file_type+'.json', "text/plain")
                    } else {
                      console.log(response)
                }
            })
      }

      
      const schedulingRulesValidationError = ref(null);
      watchEffect( () => { 

              if (schedulingRulesFile.value && schedulingRulesFile.value.length >= 1) {

                extractFileContent(schedulingRulesFile.value[0]).then(result => {

                  try {
                   
                    schedulingRulesJson.value = JSON.parse(result.response)
                    
                    const validateSchedulingRulesConfig = {
                          method: 'post',
                          url: '/api/scheduler/validate-scheduling-rules',
                          data: schedulingRulesJson.value,
                      }

                  
                    performHttpRequest(validateSchedulingRulesConfig)
                            .then(response => {
                                if (response.status === 200) {
                                  schedulingRulesValidationError.value = response.data.error
                                } else {
                                  console.log(response)
                                }
                            })
                 
                 } catch (error) {
                    
                    schedulingRulesValidationError.value = [error]
                  
                  }
                  
                })
              }
      })

      const labConfigValidationError = ref(null);
      watchEffect( () => { 

              if (labConfigFile.value && labConfigFile.value.length >= 1) {
                
                extractFileContent(labConfigFile.value[0]).then(result => {

                  try {
                    
                    labConfigJson.value = JSON.parse(result.response)

                    const validateLabConfig = {
                            method: 'post',
                            url: '/api/scheduler/validate-lab-config',
                            data: labConfigJson.value,
                        }

                    
                    performHttpRequest(validateLabConfig)
                            .then(response => {
                                if (response.status === 200) {
                                  labConfigValidationError.value = response.data.error
                                } else {
                                  console.log(response)
                            }
                        })

                    } catch (error) {
                    
                    labConfigValidationError.value = [error]
                  
                  }
                })
              }
      })



      const schedulerResponse = ref(null)
      const schedulerError = ref(null)
      const invalid = ref(false)
      const handleSubmit = () => {

      if (scheduleName.value && selectedCaseFile.value && iterations.value && 
        startDate.value && endDate.value && selectedArrDistrib.value && 
        (lambdaDailyArr.value || (minDailyArr.value && maxDailyArr.value)) &&
        leadTime.value && timeWindow.value && schedulingRulesJson.value && labConfigJson.value
        ) {

          watchEffect( () => { 

                    const data = {
                        name: scheduleName.value,
                        iterations: iterations.value,
                        description: description.value,
                        data: {
                            startDate: startDate.value,
                            endDate: endDate.value,
                            arrDistrib: selectedArrDistrib.value,
                            arrLambda: lambdaDailyArr.value,
                            minDailyArr: minDailyArr.value,
                            maxDailyArr: maxDailyArr.value,
                            timeWindow: timeWindow.value,
                            leadTime: leadTime.value,
                            algoName: selectedAlgoName.value,
                            caseFile: selectedCaseFile.value,
                            reorderCases: reorderCases.value,
                            labConfigName: labConfigFile.value,
                            schedRulesConfigName: schedulingRulesFile.value,
                            labConfiguration: labConfigJson.value,
                            schedulingRules: schedulingRulesJson.value,
                        },
                    }

                    const config = {
                        method: 'post',
                        url: '/api/scheduler/run-scheduler',
                        data: data,
                    }
                    
                    
                    performHttpRequest(config)
                        .then(response => {
                            if (response.status === 200) {
                            schedulerResponse.value = 'Scheduler ran successfully'
                            } else {
                        schedulerError.value = 'There was a problem running the scheduler: Status '+response.status
                        }
                    })
                                    
            })
            
            runningScheduler.value = true
          } else {
            invalid.value = true
          }
        }

      return { 
        showDialog, 
        downloadConfig, 
        runningScheduler, 
        integerRules, 
        iterationsRules,
        numberRules, 
        descriptionRules, 
        scheduleName, 
        iterations, 
        description, 
        startDate, 
        endDate, 
        minDailyArr, 
        maxDailyArr, 
        lambdaDailyArr, 
        timeWindow, 
        leadTime, 
        selectedAlgoName, 
        selectedArrDistrib, 
        selectedPointsMetrics, 
        reorderCases, 
        selectedOrderingMetric, 
        selectedOrderingType, 
        selectedCaseFile, 
        labConfigFile, 
        labConfigValidationError,
        schedulingRulesFile, 
        schedulingRulesValidationError,
        algoNames, 
        arrDistribs, 
        pointsMetrics, 
        orderingTypes, 
        caseFiles, 
        handleSubmit, 
        schedulerResponse, 
        schedulerError,
        invalid,
      }
    }
}
</script>

<style>

</style>

<style>
.download-btn {
  margin-bottom: 1em
}

.validation-error {
  padding-bottom: 0.5em
}
</style>