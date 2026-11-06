import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import Login from '../views/Login.vue'
import AdminHome from '../views/AdminHome.vue'
import StaffHome from '../views/StaffHome.vue'
import CustomerHome from '../views/CustomerHome.vue'
import SoloToday from '../views/solo/SoloToday.vue'
import SoloSchedule from '../views/solo/SoloSchedule.vue'
import SoloClients from '../views/solo/SoloClients.vue'
import SoloStyles from '../views/solo/SoloStyles.vue'
import SoloFinance from '../views/solo/SoloFinance.vue'

/**
 * 单人版分支：管理员登录后直接落在「今天」。
 * 多角色页面仍保留（答辩用 main 分支，这里只是不抢占入口）。
 */
const ROLE_HOME = Object.freeze({
  ADMIN: '/solo/today',
  STAFF: '/staff',
  CUSTOMER: '/customer'
})

function homeForRole(roleCode) {
  return ROLE_HOME[roleCode] || ''
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: Login
    },
    {
      path: '/admin',
      name: 'AdminHome',
      component: AdminHome,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/staff',
      name: 'StaffHome',
      component: StaffHome,
      meta: { role: 'STAFF' }
    },
    {
      path: '/customer',
      name: 'CustomerHome',
      component: CustomerHome,
      meta: { role: 'CUSTOMER' }
    },
    {
      path: '/solo',
      redirect: '/solo/today'
    },
    {
      path: '/solo/today',
      name: 'SoloToday',
      component: SoloToday,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/solo/schedule',
      name: 'SoloSchedule',
      component: SoloSchedule,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/solo/clients',
      name: 'SoloClients',
      component: SoloClients,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/solo/styles',
      name: 'SoloStyles',
      component: SoloStyles,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/solo/finance',
      name: 'SoloFinance',
      component: SoloFinance,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/',
      redirect: '/login'
    }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (!userStore.isLoggedIn) {
    return to.path === '/login' ? true : '/login'
  }

  const ownHome = homeForRole(userStore.roleCode)

  if (to.path === '/login') {
    if (ownHome) {
      return ownHome
    }
    userStore.logout()
    return true
  }

  const requiredRole = to.meta.role
  if (requiredRole && userStore.roleCode !== requiredRole) {
    if (ownHome) {
      return ownHome
    }
    userStore.logout()
    return '/login'
  }

  return true
})

export default router
