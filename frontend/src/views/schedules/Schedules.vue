/<template>
    <div class="schedules">
      <h1>Schedules</h1>
      <div v-if="error" class="bg-red">
        <span class="text-white">
              <p>{{ error }}</p>
        </span>
      </div>
      <div v-else>
        <div v-if="loading" class="loading">
            <p class="font-weight-regular">Loading schedules...</p>
            <v-row justify="center">
              <v-progress-circular
                indeterminate
                color=#42b983
              ></v-progress-circular>
            </v-row>
          </div>
        <div v-else>
          <ResultsTable :items="items" :detailsPage="detailsPage"/>
          <v-btn @click="showModal = !showModal" rounded='lg' size="x-large" color="#42b983" class="text-white">new schedule</v-btn>
        </div>
      </div>
      <SchedulesModal v-if="showModal" @close="showModal = false" :showModal="showModal"></SchedulesModal>
    </div>
</template>

<script>
import ResultsTable from '../../components/ResultsTable.vue'
import SchedulesModal from '../../components/SchedulesModal.vue'
import { useScheduleStore } from '../../stores/scheduleStore.js'
import { ref, watchEffect } from 'vue'

export default {
    name: 'Schedules',
    components: { ResultsTable, SchedulesModal },
    setup() {
        
        const showModal = ref(false)
        const detailsPage = 'ScheduleDetails'
        
        const items = ref([])
        const error = ref(null)
        const loading = ref(true)

        const store = useScheduleStore()
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