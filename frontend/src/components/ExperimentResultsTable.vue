/<template>
    <div class="results-table">
        <v-data-table 
        :headers="headers"
        :items="items"
        :items-per-page="10"
        class="elevation-1"
        :hover="true"
        >
            <template v-slot:item.isSelected="{ item }">
                <v-checkbox v-if="item.columns.status=='FINISHED'" :v-model="item.isSelected" @click="handleCheckboxClick(item)"></v-checkbox>
            </template>
            <template v-slot:item.status="{ item }">
                <v-chip :color="getStatusColor(item.columns.status)">
                    {{ item.columns.status }}
                </v-chip>
            </template>
            <template v-slot:item.startTime="{ item }">
                {{ formatDatetime(item.columns.startTime) }}
            </template>
            <template v-slot:item.description="{ item }">
                {{ getTextChunk(item.columns.description) }}
            </template>
        </v-data-table>
    </div>
    <div class="pa-4 text-center">
    </div>
</template>

<script>
    import { VDataTable } from 'vuetify/labs/VDataTable'
    import { useRouter } from 'vue-router'
    import { ref } from 'vue'

    export default {
        components: { VDataTable },
        emits: [['update-selected']],
        props: ['items', 'detailsPage'],
        setup(props, { emit }) {

            const router = useRouter()

            const selected = ref([])

            const handleCheckboxClick = (item) => {
                item.isSelected=!item.isSelected
                if (item.isSelected) {
                    selected.value.push(item.raw.id)
                } else {
                    selected.value = selected.value.filter(function (id) {return id !== item.raw.id;})
                }
                emit('update-selected', selected.value)
            }
            
            const handleViewDetailsClick = (id) => {
                router.push({ name: props.detailsPage, params: { id: id } })
            }

            const getStatusColor = (status) => {
                if (status == 'FINISHED') return 'green'
                else if (status == 'FAILED') return 'red'
                else if (status == 'RUNNING') return 'yellow'
                else return 'grey'
            }

            const formatDatetime = (inputTime) => {
                if (typeof(inputTime) === 'string') {
                    var newDate = new Date(inputTime);
                    return newDate.toLocaleString()
                } else if (inputTime == 0) {
                    return 'unavailable'
                }
                else {
                    var newDate = new Date();
                    newDate.setTime(inputTime);
                    return newDate.toLocaleString()
                }
            }

            const getTextChunk = (inputDescription) => {
                if (inputDescription) {
                    return inputDescription.length <= 25 ? inputDescription : inputDescription.substring(0, 25)+'...';
                }
            }

            return {
                //Headers for Datatable
                headers: [
                        { title: 'Name', align: 'center', width: '20%', key: 'name', value: 'name'},
                        { title: 'Description', align: 'center', width: '20%', key: 'description', value: 'description'},
                        { title: 'Created Time',  align: 'center', width: '20%', key: 'startTime', value: 'startTime'},
                        { title: 'Status',  align: 'center', width: '10%', key: 'status', value: 'status'},
                        { title: 'User',  align: 'center', width: '10%', key: 'userId',  value: 'user'},
                        { title: 'Results', align: 'right', width: '10%', key: 'isSelected', sortable: false },
                    ], 
                    handleCheckboxClick,
                    handleViewDetailsClick,
                    getStatusColor,
                    formatDatetime,
                    getTextChunk,
                    selected
                }
        }
    }
</script>

<style>
.results-table v-table {
    table-layout: fixed;
  }
</style>