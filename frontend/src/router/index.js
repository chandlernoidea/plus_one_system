import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import Login from '../views/Login.vue'
import AdminHome from '../views/AdminHome.vue'
import StaffHome from '../views/StaffHome.vue'
import CustomerHome from '../views/CustomerHome.vue'
import SoloLogin from '../views/solo/SoloLogin.vue'
import SoloToday from '../views/solo/SoloToday.vue'
import SoloSchedule from '../views/solo/SoloSchedule.vue'
import SoloClients from '../views/solo/SoloClients.vue'
import SoloClientDetail from '../views/solo/SoloClientDetail.vue'
import SoloStyles from '../views/solo/SoloStyles.vue'
import SoloMaterials from '../views/solo/SoloMaterials.vue'
import SoloFinance from '../views/solo/SoloFinance.vue'

/** 免登录页面：单人版专用登录 + 多角色演示登录 */
const PUBLIC_PATHS = ['/solo/login', '/login']

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
      path: '/solo/login',
      name: 'SoloLogin',
      component: SoloLogin
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
      path: '/solo/clients/:id',
      name: 'SoloClientDetail',
      component: SoloClientDetail,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/solo/styles',
      name: 'SoloStyles',
      component: SoloStyles,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/solo/materials',
      name: 'SoloMaterials',
      component: SoloMaterials,
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
      redirect: '/solo/today'
    }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (!userStore.isLoggedIn) {
    return PUBLIC_PATHS.includes(to.path) ? true : '/solo/login'
  }

  const ownHome = homeForRole(userStore.roleCode)

  if (PUBLIC_PATHS.includes(to.path)) {
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
