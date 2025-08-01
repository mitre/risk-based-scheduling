/<template>
  <apexchart type="heatmap" height="350"  :options="chartOptions" :series="schedDetail.heatmap"></apexchart>
</template>

<script>
import VueApexCharts from "vue3-apexcharts";

export default {
    props: ['schedDetail'],
    components: { apexchart: VueApexCharts,  },
    setup(props) {
        const chartOptions = {
            chart: {
                height: 350,
                type: 'heatmap',
            },
            dataLabels: {
                enabled: false
            },
            xaxis: {
                categories: Array(52).fill().map((_, i) => i+1),
                title: {
                    text: 'Week'
                },
            },
            colors: ["#008FFB"],
            title: {
                text: props.schedDetail.run_data['tags.mlflow.runName'],
                style: {
                    fontSize:  '16px',
                },
            },
            subtitle: {
                text: 'Foribly scheduled cases: '+props.schedDetail.run_data['metrics.total_forcibly_scheduled_cases']+'; Added slots: '+props.schedDetail.run_data['metrics.added_slot_scheduled_cases'],
                align: 'left',
                style: {
                    fontSize:  '14px',
                    fontWeight:  'normal',
                },
            },
            plotOptions: {
                heatmap: {
                    radius: 2,
                    enableShades: false,
                    colorScale: {
                        ranges: [
                            {
                                from: 0,
                                to: 0.001,
                                color: "#00008b",
                                foreColor: undefined,
                                name: '0',
                            },
                            {
                                from: 0.001,
                                to: 3,
                                color: "#229bf5",
                                foreColor: undefined,
                                name: '0 - 3',
                            },
                            {
                                from: 3,
                                to: 4,
                                color: "#c4e0f5",
                                foreColor: undefined,
                            },
                            {
                                from: 4,
                                to: 4,
                                color: "#faf5f5",
                                foreColor: undefined,
                                name: '4',
                            },
                            {
                                from: 4.01,
                                to: 5,
                                color: "#f5c2bc",
                                foreColor: undefined,
                                name: '4 - 5',
                            },
                            {
                                from: 5,
                                to: 6,
                                color: "#eb6f60",
                                foreColor: undefined,
                                name: '> 5',
                            },
                        ],
                    },      
                }
            }
            }


        return { chartOptions }

    }

}
</script>

<style>

</style>