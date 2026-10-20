import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

function clearLoginState() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}

function redirectToLogin() {
  clearLoginState()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const { code, message } = response.data || {}
    if (code !== 200) {
      ElMessage.error(message || '请求失败')
      if (code === 401) {
        redirectToLogin()
      }
      return Promise.reject(new Error(message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.error(error.response.data?.message || '未登录或登录已过期')
      redirectToLogin()
      return Promise.reject(error)
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
