import request from './request'

export function listServiceTypes() {
  return request.get('/service-types')
}
