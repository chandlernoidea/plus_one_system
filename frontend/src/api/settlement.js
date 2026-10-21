import request from './request'

export function generateSettlements(month) {
  return request.post('/settlements/generate', null, { params: { month } })
}

export function listSettlements(month) {
  return request.get('/settlements', { params: { month } })
}

export function listMySettlements() {
  return request.get('/settlements/my')
}

export function revokeSettlement(id) {
  return request.post(`/settlements/${id}/revoke`)
}

export function settleSettlement(id) {
  return request.post(`/settlements/${id}/settle`)
}
