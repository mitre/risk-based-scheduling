/<template>
  <v-row justify="center">
    <v-dialog
      v-model="showDialog"
      persistent
      width="auto"
    >
      <v-card>
        <v-card-title>
          <span class="text-h5">Upload Case File</span>
        </v-card-title>
          <v-card-text style="width: 400px;">
            <v-container>
              <v-col
              cols="12"
              >
                <div v-if="!uploadingCaseFile">
                    <v-text-field
                        v-model="caseFileName"
                        label="Case file name"
                        required
                    ></v-text-field>
                      <v-textarea
                          v-model="description"
                          height=3
                          label="Short description of schedule..."
                          :rules="descriptionRules"
                      ></v-textarea>
                    <v-file-input
                        v-model="electiveFile"
                        label="Upload elective cases json"
                        required
                    ></v-file-input>
                    <div v-if="electiveCaseFileValidationError">
                      <div v-for="error in electiveCaseFileValidationError" :key="error.message" class="validation-error">
                          <v-card class="bg-red">{{ error.message }}</v-card>
                      </div>
                    </div>
                    <v-file-input
                        v-model="addonFile"
                        label="Upload addon cases json"
                        required
                    ></v-file-input>
                    <div v-if="addonCaseFileValidationError">
                      <div v-for="error in addonCaseFileValidationError" :key="error.message" class="validation-error">
                          <v-card class="bg-red">{{ error.message }}</v-card>
                      </div>
                    </div>
                    <div v-if="invalid" class="bg-red">
                      <p>Populate all required fields.</p>
                    </div>
                </div>
                <div v-else>
                    <div v-if="uploadResponse" class="bg-green">
                        <p>{{ uploadResponse }}</p>
                    </div>
                    <div v-else-if="uploadError" class="bg-red">
                        <p>{{ uploadError }}</p>
                    </div>
                    <div v-else>
                    <v-row justify="center">
                        <p>Uploading case file...</p>
                    </v-row>
                    <v-row justify="center">
                        <v-progress-circular
                        indeterminate
                        color=#42b983
                        ></v-progress-circular>
                    </v-row>
                    </div>
                </div>
                <div v-if="!uploadingCaseFile">
                  <v-btn
                    color="indigo"
                    size="small"
                    variant="tonal"
                    block
                    @click="downloadConfig('elective','template')"
                    class="download-btn"
                  >
                    Download Elective Template
                    <template v-slot:append>
                      <v-icon class="primary--text">mdi-download</v-icon>
                    </template>  
                  </v-btn>
                  <v-btn
                    color="indigo"
                    size="small"
                    variant="tonal"
                    block
                    @click="downloadConfig('addon','template')"
                    class="download-btn"
                  >
                    Download Add-On Template
                    <template v-slot:append>
                      <v-icon class="primary--text">mdi-download</v-icon>
                    </template>  
                  </v-btn>
                  <v-btn
                    color="indigo"
                    size="small"
                    variant="tonal"
                    block
                    @click="downloadConfig('case_file','schema')"
                    class="download-btn"
                  >
                    Download Case File Schema
                    <template v-slot:append>
                      <v-icon class="primary--text">mdi-download</v-icon>
                    </template>  
                  </v-btn>
                </div>
            </v-col>
          </v-container>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <div v-if="!uploadingCaseFile">
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
          <div v-if="uploadResponse || uploadError">
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
        const uploadingCaseFile = ref(false)
        
        const caseFileName = ref('')
        const description = ref('')
        const electiveFile = ref(null)
        const addonFile = ref(null)
        const electiveJson = ref(null)
        const addonJson = ref(null)

        const descriptionRules =  ref([
          value => {
            if (value.length <= 100) return true
            return 'Max 100 characters'
          },
        ])

        const downloadConfig = (config_type, file_type) => {
        
        const downloadTemplate = {
          method: 'post',
          url: '/api/case-gen/get-template',
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

      const validateCaseFile = (file, json, validationError) => {
                extractFileContent(file.value[0]).then(result => {
                  try {
                   
                    json.value = JSON.parse(result.response)
                    
                    const validateCaseFileConfig = {
                          method: 'post',
                          url: '/api/case-gen/validate-case-file',
                          data: json.value,
                      }

                    performHttpRequest(validateCaseFileConfig)
                            .then(response => {
                                if (response.status === 200) {
                                  validationError.value = response.data.error
                                } else {
                                  console.log(response)
                                }
                            })
                 
                 } catch (error) {
                    
                    validationError.value = [error]
                  
                  }
                  
                })
            }

      
      const electiveCaseFileValidationError = ref(null);
      watchEffect( () => { 

              if (electiveFile.value && electiveFile.value.length >= 1) {
                validateCaseFile(electiveFile, electiveJson, electiveCaseFileValidationError)
              }
      })
      const addonCaseFileValidationError = ref(null);
      watchEffect( () => { 

              if (addonFile.value && addonFile.value.length >= 1) {
                validateCaseFile(addonFile, addonJson, addonCaseFileValidationError)
              }
      })
  
      const uploadResponse = ref('')
      const uploadError = ref('')
      const invalid = ref(false)
      const handleSubmit = () => {
        
        if (caseFileName.value && electiveFile.value && addonFile.value) {


            extractFileContent(electiveFile.value[0]).then(result => {
                electiveJson.value = JSON.parse(result.response)
                })
            extractFileContent(addonFile.value[0]).then(result => {
                addonJson.value = JSON.parse(result.response)
                })

            if (electiveJson.value && addonJson.value) {

                const data = {
                    name: caseFileName.value,
                    description: description.value,
                    electiveFile: {filename: electiveFile.value[0].name, json: electiveJson.value},
                    addonFile: {filename: addonFile.value[0].name, json: addonJson.value},
                }


                const config = {
                    method: 'post',
                    url: 'api/case-gen/upload-case-bucket',
                    data: data,
                }


                performHttpRequest(config)
                .then(response => {
                    if (response.status === 200) {
                        uploadResponse.value = 'Successfully uploaded case file.'
                    } else {
                        uploadError.value = 'There was a problem uploading the case file: Status '+response.status
                    }   
                })

            }
      
            
        uploadingCaseFile.value = true


        } else {
          invalid.value = true
        }
        
      }

      return { 
        showDialog, 
        downloadConfig,
        uploadingCaseFile,
        descriptionRules, 
        caseFileName, 
        description, 
        electiveFile, 
        addonFile, 
        handleSubmit, 
        uploadResponse, 
        uploadError,
        invalid,
        electiveCaseFileValidationError,
        addonCaseFileValidationError
      }
    }
}
</script>

<style>
.download-btn {
  margin-bottom: 1em
}

.validation-error {
  padding-bottom: 0.5em
}
</style>