import { defineStore } from 'pinia'
import { ref } from 'vue'
import performHttpRequest from '../composables/performHttpRequest.js'

export const useCaseFileStore = defineStore('caseFileStore', () => {
    const items = ref([])
    const error = ref(null)
    const loading = ref(true)

    const config = {
            method: 'get',
            url: '/api/case-gen/get-case-buckets',
        }

    performHttpRequest(config)
            .then(response => {
            if (response.status === 200) {
                items.value = response.data
                loading.value = false
            } else {
                error.value = response.data
            }
        })
    
    return { items, error, loading }
})