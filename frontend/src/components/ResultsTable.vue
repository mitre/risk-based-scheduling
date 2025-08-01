/<template>
    <div class="results-table">
        <v-data-table 
        :headers="headers"
        :items="items"
        :items-per-page="10"
        class="elevation-1"
        :hover="true"
        >
            <template v-slot:item.viewDetails="{ item }">
                <v-btn v-if="item.columns.status=='FINISHED'" color='black' density="compact" rounded="xl" variant="tonal" style="cursor:pointer;"  @click="handleViewDetailsClick(item.raw.id)">view</v-btn>
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

    export default {
        components: { VDataTable },
        props: ['items', 'detailsPage'],
        setup(props) {

            const router = useRouter()
            
            const handleViewDetailsClick = (id) => {
                router.push({ name: props.detailsPage, params: { id: id } })
            }

            const getStatusColor = (status) => {
                if (status == 'FINISHED') return 'green'
                else if (status == 'FAILED') return 'red'
                else return 'yellow'
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
                        { title: 'Details', align: 'center', width: '10%', key: 'viewDetails', sortable: false },
                    ], 
                    handleViewDetailsClick,
                    getStatusColor,
                    formatDatetime,
                    getTextChunk,
                }
        }
    }
</script>

<style>
.inactive v-btn {
    background-color: black
}
.results-table v-table {
    table-layout: fixed;
  }
</style>