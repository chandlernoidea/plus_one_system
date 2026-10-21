import request from './request'

export function createReview(appointmentId, data) {
  return request.post(`/appointments/${appointmentId}/reviews`, data)
}

export function listReviews() {
  return request.get('/reviews')
}
