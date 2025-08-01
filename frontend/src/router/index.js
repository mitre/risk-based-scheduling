// Composables
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: "Home",
    component: () => import('@/views/Home.vue'),
  },
  {
    path: '/about',
    name: "About",
    component: () => import('@/views/About.vue'),
  },
  {
    path: "/experiments",
    name: "Experiments",
    component: () => import('@/views/experiments/Experiments.vue'),
    props: true,
  },
  {
    path: "/experiments/results",
    name: "ExperimentResultsDetails",
    component: () => import('@/views/experiments/ExperimentResultsDetails.vue'),
    props: true,
  },
  {
    path: '/schedules',
    name: "Schedules",
    component: () => import('@/views/schedules/Schedules.vue'),
  },
  {
    path: "/schedules/:id",
    name: "ScheduleDetails",
    component: () => import('@/views/schedules/ScheduleDetails.vue'),
    props: true,
  },
  {
    path: '/casefiles',
    name: "CaseFiles",
    component: () => import('@/views/casefiles/CaseFiles.vue'),
  },
  {
    path: "/casefiles/:id",
    name: "CaseFileDetails",
    component: () => import('@/views/casefiles/CaseFileDetails.vue'),
    props: true,
  },
  // catchall 404
  {
    path: "/:catchAll(.*)",
    name: "NotFound",
    component: () => import('@/views/NotFound.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
})

export default router

