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

/** 管理员确认物料已备好 */
export function confirmPrep(appointmentId) {
  return request.post(`/preps/${appointmentId}/confirm`)
}

/** 管理员撤回「已备好」 */
export function unconfirmPrep(appointmentId) {
  return request.post(`/preps/${appointmentId}/unconfirm`)
}

/**
 * 今天起 N 天内的物料工单，默认 7 天。
 * 后端未升级时参数会被忽略，退化为「只看今天」，不会报错。
 */
export function listUpcomingPreps(days = 7) {
  return request.get('/preps/today', { params: { days } })
}
