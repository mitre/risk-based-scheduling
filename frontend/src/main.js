/**
 * main.js
 *
 * Bootstraps Vuetify and other plugins then mounts the App`
 */

// Components
import App from './App.vue'

// Composables
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import VueApexCharts from "vue3-apexcharts";

// Plugins
import { registerPlugins } from '@/plugins'

const pinia = createPinia()
const app = createApp(App)

registerPlugins(app)
app.use(pinia)
app.use(VueApexCharts);

app.mount('#app')
