import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import Login from '../views/Login.vue'
import AdminHome from '../views/AdminHome.vue'
import StaffHome from '../views/StaffHome.vue'
import CustomerHome from '../views/CustomerHome.vue'

const ROLE_HOME = Object.freeze({
  ADMIN: '/admin',
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
