<template>
  <div class="tf-shell">
    <!-- ============ 左：环形工序盘导航 ============ -->
    <aside class="tf-disc-panel">
      <div class="tf-card">
        <div class="tf-card-title">
          工序盘
          <small>鲜叶 → 成茶</small>
        </div>

        <div class="tf-disc">
          <div class="tf-disc-ring"></div>
          <div class="tf-disc-pointer" :style="{ transform: 'rotate(' + pointerAngle + 'deg)' }"></div>

          <div
            v-for="node in nodes"
            :key="node.key"
            class="tf-disc-node"
            :class="{ 'is-active': node.key === activeKey, 'is-done': isDone(node.key) }"
            :style="nodeStyle(node)"
            :title="node.label"
            @click="go(node)"
          >
            {{ node.label }}
          </div>

          <div class="tf-disc-center">
            <div class="tf-center-step">{{ activeNode ? activeNode.label : '工序' }}</div>
            <div class="tf-center-tea">{{ teaTypeName }}</div>
          </div>
        </div>

        <el-select v-model="teaType" size="small" style="width: 100%">
          <el-option v-for="t in teaTypes" :key="t" :label="teaTypeNames[t]" :value="t" />
        </el-select>

        <div class="tf-stat" style="margin-top: 10px; line-height: 1.9">
          今日进厂 <b>{{ dash.intakeCount }}</b> 车 · 净重 <b>{{ dash.intakeNetWeightKg }}</b> kg<br />
          在槽 <b>{{ dash.witheringCount }}</b> 槽（达标可放行 <b>{{ dash.readyTroughCount }}</b>）<br />
          在制 <b>{{ dash.processingOrderCount }}</b> 批 · 完成 <b>{{ dash.doneOrderCount }}</b> 批<br />
          设备运行 <b>{{ dash.runningEquipmentCount }}</b> 台 · 故障 <b>{{ dash.faultEquipmentCount }}</b> 台
        </div>

        <router-link class="tf-cleaning-link" :class="{ 'is-active': route.name === 'cleaning' }" to="/cleaning">
          🧽 设备清洗交接台
          <small>交接单 · 开工可用性</small>
        </router-link>
        <router-link class="tf-cleaning-link" :class="{ 'is-active': route.name === 'yield' }" to="/yield">
          📈 产出率与损耗统计
          <small>同一条数量时间线 · 逐批对账</small>
        </router-link>
      </div>
    </aside>

    <!-- ============ 右：内容区 ============ -->
    <main class="tf-main">
      <component :is="currentView" :tea-type="teaType" :step="activeStep" />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import router from './router'
import IntakeView from './views/IntakeView.vue'
import WitheringView from './views/WitheringView.vue'
import ProcessView from './views/ProcessView.vue'
import EvaluationView from './views/EvaluationView.vue'
import CleaningView from './views/CleaningView.vue'
import { statsApi } from './api'

const RADIUS = 100

const teaTypes = ['GREEN', 'RED', 'OOLONG', 'WHITE', 'PUER']
const teaTypeNames = {
  GREEN: '绿茶',
  RED: '红茶',
  OOLONG: '乌龙茶',
  WHITE: '白茶',
  PUER: '普洱茶'
}

/** 圆环上的 8 个制茶工序节点 */
const nodes = [
  { key: 'INTAKE', label: '鲜叶验收', angle: -90, route: '/intake' },
  { key: 'SPREAD', label: '摊青', angle: -45, route: '/withering' },
  { key: 'FIX', label: '杀青', angle: 0, route: '/process/FIX' },
  { key: 'ROLL', label: '揉捻', angle: 45, route: '/process/ROLL' },
  { key: 'FERMENT', label: '发酵', angle: 90, route: '/process/FERMENT' },
  { key: 'DRY', label: '干燥', angle: 135, route: '/process/DRY' },
  { key: 'PICK', label: '拣剔', angle: 180, route: '/process/PICK' },
  { key: 'REVIEW', label: '审评', angle: 225, route: '/evaluation' }
]

const teaType = ref('GREEN')
const dash = ref({ intakeCount: 0, intakeNetWeightKg: 0, witheringCount: 0, readyTroughCount: 0, processingOrderCount: 0, doneOrderCount: 0, runningEquipmentCount: 0, faultEquipmentCount: 0 })

const route = computed(() => router.currentRoute.value)

const activeKey = computed(() => {
  const r = route.value
  if (!r) return 'INTAKE'
  if (r.name === 'intake') return 'INTAKE'
  if (r.name === 'withering') return 'SPREAD'
  if (r.name === 'evaluation') return 'REVIEW'
  const step = String(r.params?.step || 'FIX').toUpperCase()
  return nodes.some((n) => n.key === step) ? step : 'FIX'
})

const activeNode = computed(() => nodes.find((n) => n.key === activeKey.value) || nodes[0])
/**
 * 指针角度：节点按 rotate(a) translate(0,-R) rotate(-a) 排布，落在圆周角 a 处；
 * 指针以圆心为旋转原点、静止时竖直向下，要指到该节点需旋转 180 + a 度。
 */
const pointerAngle = computed(() => activeNode.value.angle + 180)
const activeStep = computed(() => (activeKey.value === 'FIX' || activeKey.value === 'ROLL' || activeKey.value === 'FERMENT' || activeKey.value === 'DRY' || activeKey.value === 'PICK' ? activeKey.value : 'FIX'))
const teaTypeName = computed(() => teaTypeNames[teaType.value] || '绿茶')

const views = { intake: IntakeView, withering: WitheringView, process: ProcessView, evaluation: EvaluationView, cleaning: CleaningView }
const currentView = computed(() => views[route.value?.name] || IntakeView)

/** 已完成节点（当前节点之前的制茶工序） */
function isDone(key) {
  const chain = ['INTAKE', 'SPREAD', 'FIX', 'ROLL', 'FERMENT', 'DRY', 'PICK', 'REVIEW']
  return chain.indexOf(key) >= 0 && chain.indexOf(key) < chain.indexOf(activeKey.value)
}

function nodeStyle(node) {
  return { transform: `rotate(${node.angle}deg) translate(0, -${RADIUS}px) rotate(${-node.angle}deg)` }
}

function go(node) {
  router.push(node.route).catch(() => {})
}

async function loadDashboard() {
  try {
    const res = await statsApi.dashboard()
    if (res && res.ok) dash.value = res.data
  } catch (e) {
    // 概览失败不阻断页面
  }
}

onMounted(async () => {
  loadDashboard()
  const target = window.location.pathname === '/' ? '/intake' : window.location.pathname
  try {
    await router.replace(target)
  } catch (e) {
    await router.replace('/intake').catch(() => {})
  }
})
</script>

<style scoped>
.tf-cleaning-link {
  display: flex;
  flex-direction: column;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #c4b99f;
  border-radius: 6px;
  background: #f4efe4;
  color: #2f3327;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s ease;
}

.tf-cleaning-link small {
  font-weight: 400;
  font-size: 11px;
  color: #5d6353;
  margin-top: 2px;
}

.tf-cleaning-link:hover,
.tf-cleaning-link.is-active {
  background: #33691e;
  border-color: #33691e;
  color: #fff;
}

.tf-cleaning-link:hover small,
.tf-cleaning-link.is-active small {
  color: #d9e6cd;
}
</style>
