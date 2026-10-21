import request from './request'

export function createReschedule(appointmentId, data) {
  return request.post(`/appointments/${appointmentId}/reschedules`, data)
}

export function acceptReschedule(id) {
  return request.post(`/reschedules/${id}/accept`)
}

export function rejectReschedule(id) {
  return request.post(`/reschedules/${id}/reject`)
}
