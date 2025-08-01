<template>
  <div class="experiments">
      <h1>Experiments</h1>
      <div v-if="error" class="bg-red">
        <span class="text-white">
              <p>{{ error }}</p>
        </span>
        </div>
        <div v-else>
          <div v-if="loading" class="loading">
            <p class="font-weight-regular">Loading experiments...</p>
            <v-row justify="center">
              <v-progress-circular
                indeterminate
                color=#42b983
              ></v-progress-circular>
            </v-row>
          </div>
          <div v-else>
            <ExperimentResultsTable :items="items" @update-selected="updateSelected"/>
            <v-btn @click="showModal = !showModal" rounded='lg' size="x-large" color="#42b983" class="text-white">new experiment</v-btn>
            <v-btn @click="handleViewResultsClick"  rounded='lg' size="x-large" color="#42b983" class="text-white">view results</v-btn>
          </div>
        </div>
      <ExperimentsModal v-if="showModal" @close="showModal = false" :showModal="showModal"></ExperimentsModal>
    </div>
</template>

<script>
import ExperimentResultsTable from '../../components/ExperimentResultsTable.vue'
import ExperimentsModal from '../../components/ExperimentsModal.vue'
import { useExperimentStore } from '../../stores/experimentStore.js'
import { ref, watchEffect } from 'vue'
import { useRouter } from 'vue-router'

export default {
    name: 'Experiments',
    components: { ExperimentResultsTable, ExperimentsModal },
    setup() {

        const showModal = ref(false)

        const items = ref([])
        const error = ref(null)
        const loading = ref(true)

        const store = useExperimentStore()
         watchEffect( () => {
            items.value = store.items
            error.value = store.error
            loading.value = store.loading
         })

        const resultsToCompare = ref(null)
        const updateSelected = (selected) => {
          resultsToCompare.value = selected
        }

        const router = useRouter()

        const handleViewResultsClick = () => {
          router.push({ name: 'ExperimentResultsDetails', query: {id: resultsToCompare.value } })
        }
        
        
        return { showModal, items, error, loading, updateSelected, resultsToCompare, handleViewResultsClick }
    }
}
</script>

<style>
.loading p {
  padding: 1.5em;
}

</style>