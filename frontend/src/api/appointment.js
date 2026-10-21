import request from './request'

export function createAppointment(data) {
  return request.post('/appointments', data)
}

export function listMyAppointments() {
  return request.get('/appointments/my')
}

export function listStaffAppointments(params) {
  return request.get('/appointments/staff', { params })
}

export function approveAppointment(id) {
  return request.post(`/appointments/${id}/approve`)
}

export function rejectAppointment(id, data) {
  return request.post(`/appointments/${id}/reject`, data)
}

export function cancelAppointment(id) {
  return request.post(`/appointments/${id}/cancel`)
}


export function startAppointment(id) {
  return request.post(`/appointments/${id}/start`)
}

export function completeAppointment(id, data) {
  return request.post(`/appointments/${id}/complete`, data)
}

export function noShowAppointment(id) {
  return request.post(`/appointments/${id}/no-show`)
}


export function exemptNoShow(id) {
  return request.post(`/appointments/${id}/no-show-exempt`)
}

export function updateAppointmentTotalAmount(id, data) {
  return request.post(`/appointments/${id}/total-amount`, data)
}
