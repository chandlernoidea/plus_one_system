import request from './request'

/**
 * 单人版接口。返回值沿用既有约定：调用方取 `response.data.data`。
 */

// ---------------------------------------------------------------- 排期
export function fetchDaySchedule(date) {
  return request.get('/solo/schedule/day', { params: { date } })
}

export function fetchWeekSchedule(from) {
  return request.get('/solo/schedule/week', { params: { from } })
}

// ---------------------------------------------------------------- 顺延 / 待通知
export function previewShift(data) {
  return request.post('/solo/shifts/preview', data)
}

export function applyShift(data) {
  return request.post('/solo/shifts', data)
}

export function fetchPendingChanges() {
  return request.get('/solo/changes/pending')
}

export function markChangeNotified(id) {
  return request.post(`/solo/changes/${id}/notified`)
}

// ---------------------------------------------------------------- 客户
export function searchClients(keyword) {
  return request.get('/solo/clients', { params: { keyword: keyword || undefined } })
}

export function createClient(data) {
  return request.post('/solo/clients', data)
}

// ---------------------------------------------------------------- 款式
export function fetchStyles(params = {}) {
  return request.get('/solo/styles', { params })
}

// ---------------------------------------------------------------- 预约
export function createBooking(data) {
  return request.post('/solo/bookings', data)
}

export function jumpBooking(data) {
  return request.post('/solo/jumps', data)
}

export function arriveBooking(id) {
  return request.post(`/solo/bookings/${id}/arrive`)
}

export function startBooking(id) {
  return request.post(`/solo/bookings/${id}/start`)
}

export function completeBooking(id) {
  return request.post(`/solo/bookings/${id}/complete`)
}

export function noShowBooking(id) {
  return request.post(`/solo/bookings/${id}/no-show`)
}

export function rescheduleBooking(id, data) {
  return request.post(`/solo/bookings/${id}/reschedule`, data)
}

export function cancelBooking(id, data) {
  return request.post(`/solo/bookings/${id}/cancel`, data)
}

// ---------------------------------------------------------------- 定金 / 物料
export function collectDeposit(id, data = { type: 'COLLECT' }) {
  return request.post(`/solo/bookings/${id}/deposits`, data)
}

export function fetchBookingMaterials(id) {
  return request.get(`/solo/bookings/${id}/materials`)
}

export function fetchMaterials(params = {}) {
  return request.get('/solo/materials', { params })
}
