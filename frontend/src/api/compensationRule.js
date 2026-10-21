import request from './request'

export function saveRule(data) {
  return request.post('/compensation-rules', data)
}

export function listRules() {
  return request.get('/compensation-rules')
}

export function getRuleByStaff(staffId) {
  return request.get(`/compensation-rules/staff/${staffId}`)
}
