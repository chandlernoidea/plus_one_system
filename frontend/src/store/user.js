import { defineStore } from 'pinia'
import { getMe } from '../api/auth'

const TOKEN_KEY = 'token'
const USER_INFO_KEY = 'userInfo'

function loadUserInfo() {
  const raw = localStorage.getItem(USER_INFO_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch (error) {
    localStorage.removeItem(USER_INFO_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: loadUserInfo()
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    roleCode: (state) => state.userInfo?.roleCode || ''
  },

  actions: {
    setLogin(loginResponse) {
      this.token = loginResponse.token
      this.userInfo = {
        userId: loginResponse.userId,
        username: loginResponse.username,
        realName: loginResponse.realName,
        roleCode: loginResponse.roleCode
      }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(this.userInfo))
    },

    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_INFO_KEY)
    },

    async fetchMe() {
      const response = await getMe()
      this.userInfo = response.data.data
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(this.userInfo))
      return this.userInfo
    }
  }
})
