import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'
import SoloLogin from '../views/solo/SoloLogin.vue'
import SoloToday from '../views/solo/SoloToday.vue'
import SoloSchedule from '../views/solo/SoloSchedule.vue'
import SoloClients from '../views/solo/SoloClients.vue'
import SoloClientDetail from '../views/solo/SoloClientDetail.vue'
import SoloStyles from '../views/solo/SoloStyles.vue'
import SoloMaterials from '../views/solo/SoloMaterials.vue'
import SoloFinance from '../views/solo/SoloFinance.vue'
import SoloSettings from '../views/solo/SoloSettings.vue'

/** 免登录页面：只有单人版登录 */
const PUBLIC_PATHS = ['/solo/login']

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 多角色演示页留给 main 分支，本分支不挂路由：
    // 之前挂在 /login、/staff、/customer，点进去后没有任何回单人版的入口，成了死胡同。
    {
      path: '/login',
      redirect: '/solo/login'
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
      path: '/solo/settings',
      name: 'SoloSettings',
      component: SoloSettings,
      meta: { role: 'ADMIN' }
    },
    {
      path: '/',
      redirect: '/solo/today'
    },
    {
      // 任何不认识的地址都回到首页，避免停在空白页
      path: '/:pathMatch(.*)*',
      redirect: '/solo/today'
    }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  // 单人版只有老板：历史上残留的 staff/customer 登录态直接清掉，
  // 否则会卡在一个已经没有入口的页面上，退不出来。
  if (userStore.isLoggedIn && userStore.roleCode !== 'ADMIN') {
    userStore.logout()
  }

  if (!userStore.isLoggedIn) {
    return PUBLIC_PATHS.includes(to.path) ? true : '/solo/login'
  }

  if (PUBLIC_PATHS.includes(to.path)) {
    return '/solo/today'
  }

  return true
})

export default router
