import request from './request'

export function getPrep(appointmentId) {
  return request.get(`/preps/${appointmentId}`)
}

export function updatePrep(appointmentId, data) {
  return request.put(`/preps/${appointmentId}`, data)
}

export function getPrepCustomerView(appointmentId) {
  return request.get(`/preps/${appointmentId}/customer-view`)
}

export function listTodayPreps() {
  return request.get('/preps/today')
}
