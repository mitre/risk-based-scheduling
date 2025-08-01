<template>
    <apexchart type="boxPlot" height="350" :options="chartOptions" :series="series"></apexchart>
</template>

<script>
import VueApexCharts from "vue3-apexcharts";

export default {
    props: [ 'title', 'boxplotData' ],
    components: { apexchart: VueApexCharts,  },
    setup(props) {
        const series = [
            {
              name: 'box',
              type: 'boxPlot',
              data: props.boxplotData
            },
        ]

        const chartOptions = {
            chart: {
              type: 'boxPlot',
              height: 350
            },
            colors: ['#008FFB', '#FEB019'],
            title: {
              text: props.title,
              align: 'left'
            },
            xaxis: {
              type: 'category',
            },
          }

          return { series, chartOptions }
    }

}
</script>

<style>

</style>