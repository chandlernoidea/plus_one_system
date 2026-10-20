import request from './request'

export function listStaffs() {
  return request.get('/staffs')
}
