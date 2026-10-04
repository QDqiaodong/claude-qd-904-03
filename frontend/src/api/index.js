import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const message = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export const intakeApi = {
  list: () => http.get('/intake/today'),
  all: () => http.get('/intake'),
  create: (data) => http.post('/intake', data),
  supplierQuality: () => http.get('/intake/supplier-quality'),
  vocab: () => http.get('/intake/vocab')
}

export const witheringApi = {
  list: () => http.get('/withering'),
  history: (troughNo) => http.get('/withering/history', { params: troughNo ? { troughNo } : {} }),
  ready: () => http.get('/withering/ready'),
  open: (data) => http.post('/withering', data),
  log: (troughNo, data) => http.put(`/withering/${troughNo}/log`, data),
  release: (troughNo) => http.post(`/withering/${troughNo}/release`)
}

export const processApi = {
  list: () => http.get('/process'),
  detail: (batchNo) => http.get(`/process/${batchNo}`),
  create: (data) => http.post('/process', data),
  start: (batchNo, data) => http.post(`/process/${batchNo}/start`, data),
  finish: (batchNo, data) => http.post(`/process/${batchNo}/finish`, data),
  steps: (batchNo) => http.get(`/process/${batchNo}/steps`),
  yieldByTeaType: () => http.get('/process/yield'),
  qtyPending: () => http.get('/process/qty/pending'),
  qtyReview: (handoverNo, data) => http.post(`/process/qty/handovers/${handoverNo}/review`, data),
  qtyCorrect: (handoverNo, data) => http.post(`/process/qty/handovers/${handoverNo}/correct`, data)
}

export const equipmentApi = {
  list: () => http.get('/equipment'),
  board: () => http.get('/equipment/board'),
  byType: (type) => http.get(`/equipment/type/${type}`),
  updateStatus: (code, status, tempC) =>
    http.put(`/equipment/${code}/status`, null, { params: { status, tempC } }),
  rooms: () => http.get('/equipment/rooms'),
  load: (roomNo, params) => http.post(`/equipment/rooms/${roomNo}/load`, null, { params }),
  unload: (roomNo) => http.post(`/equipment/rooms/${roomNo}/unload`)
}

export const cleaningApi = {
  list: (status) => http.get('/cleaning/handovers', { params: status ? { status } : {} }),
  detail: (handoverNo) => http.get(`/cleaning/handovers/${handoverNo}`),
  board: () => http.get('/cleaning/equipment'),
  start: (handoverNo, data) => http.post(`/cleaning/handovers/${handoverNo}/start`, data),
  complete: (handoverNo, data) => http.post(`/cleaning/handovers/${handoverNo}/complete`, data),
  fault: (handoverNo, data) => http.post(`/cleaning/handovers/${handoverNo}/fault`, data),
  confirm: (handoverNo, data) => http.post(`/cleaning/handovers/${handoverNo}/confirm`, data),
  reopen: (handoverNo, data) => http.post(`/cleaning/handovers/${handoverNo}/reopen`, data),
  recheck: (handoverNo, data) => http.post(`/cleaning/handovers/${handoverNo}/recheck`, data)
}

export const evaluationApi = {
  list: () => http.get('/evaluation'),
  create: (data) => http.post('/evaluation', data),
  ranking: () => http.get('/evaluation/ranking'),
  products: () => http.get('/evaluation/product'),
  store: (data) => http.post('/evaluation/product', data)
}

export const statsApi = {
  dashboard: () => http.get('/stats/dashboard'),
  supplierQuality: () => http.get('/stats/supplier-quality'),
  teaYield: () => http.get('/stats/tea-yield'),
  evalRanking: () => http.get('/stats/eval-ranking')
}

export default http
