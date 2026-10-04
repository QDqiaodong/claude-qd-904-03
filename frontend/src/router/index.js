import { createRouter, createWebHistory } from 'vue-router'
import IntakeView from '../views/IntakeView.vue'
import WitheringView from '../views/WitheringView.vue'
import ProcessView from '../views/ProcessView.vue'
import EvaluationView from '../views/EvaluationView.vue'
import CleaningView from '../views/CleaningView.vue'
import YieldView from '../views/YieldView.vue'

const routes = [
  { path: '/', redirect: '/intake' },
  { path: '/intake', name: 'intake', component: IntakeView, meta: { step: 'INTAKE', title: '鲜叶验收' } },
  { path: '/withering', name: 'withering', component: WitheringView, meta: { step: 'SPREAD', title: '摊青' } },
  { path: '/process/:step?', name: 'process', component: ProcessView, meta: { title: '制茶工序' } },
  { path: '/cleaning', name: 'cleaning', component: CleaningView, meta: { title: '设备清洗交接' } },
  { path: '/yield', name: 'yield', component: YieldView, meta: { title: '产出率统计' } },
  { path: '/evaluation', name: 'evaluation', component: EvaluationView, meta: { step: 'PICK', title: '审评' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
