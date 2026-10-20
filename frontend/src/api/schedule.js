import request from './request'

export function listSchedules(params) {
  return request.get('/schedules', { params })
}

export function createSchedule(data) {
  return request.post('/schedules', data)
}

export function updateSchedule(id, data) {
  return request.put(`/schedules/${id}`, data)
}

export function deleteSchedule(id) {
  return request.delete(`/schedules/${id}`)
}

export function listSlots(params) {
  return request.get('/schedules/slots', { params })
}
