/<template>
    <div class="caseFiles">
      <h1>Case Files</h1>
      <div v-if="error" class="bg-red">
        <span class="text-white">
              <p>{{ error }}</p>
        </span>
      </div>
      <div v-else>
          <div v-if="loading" class="loading">
          <p class="font-weight-regular">Loading case files...</p>
          <v-row justify="center">
            <v-progress-circular
              indeterminate
              color=#42b983
            ></v-progress-circular>
          </v-row>
        </div>
        <div v-else>
         <ResultsTable :items="items" :detailsPage="detailsPage"/>
          <v-btn @click="showModal = !showModal" rounded='lg' size="x-large" color="#42b983" class="text-white">upload case file</v-btn>
        </div>
      </div>
      <CaseFilesModal v-if="showModal" @close="showModal = false" :showModal="showModal"></CaseFilesModal>
    </div>
</template>

<script>
import ResultsTable from '../../components/ResultsTable.vue'
import CaseFilesModal from '../../components/CaseFilesModal.vue'
import { useCaseFileStore } from '../../stores/caseFileStore.js'
import { ref, watchEffect } from 'vue'

export default {
    name: 'CaseFiles',
    components: { ResultsTable, CaseFilesModal },
    setup() {
        
        const showModal = ref(false)
        const detailsPage = 'CaseFileDetails'
        const items = ref([])
        const error = ref(null)
        const loading = ref(true)

        const store = useCaseFileStore()
         watchEffect( () => {
            items.value = store.items
            error.value = store.error
            loading.value = store.loading
         })
        
        return { showModal, detailsPage, items, error, loading }
    }
}
</script>

<style>
.loading p {
  padding: 1.5em;
}
</style>