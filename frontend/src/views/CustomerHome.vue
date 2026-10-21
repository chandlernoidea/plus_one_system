<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import CustomerSlotView from './customer/CustomerSlotView.vue'
import CustomerAppointments from './customer/CustomerAppointments.vue'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('booking')
const appointmentsRef = ref(null)

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || ''
)
const roleName = computed(() => {
  const roleNames = {
    ADMIN: '管理员',
    STAFF: '美甲师',
    CUSTOMER: '顾客'
  }
  return roleNames[userStore.roleCode] || userStore.roleCode
})

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}

async function refreshAppointments() {
  await nextTick()
  appointmentsRef.value?.refresh()
}

function handleAppointmentCreated() {
  activeTab.value = 'appointments'
  refreshAppointments()
}

function handleTabChange(name) {
  if (name === 'appointments') {
    refreshAppointments()
  }
}
</script>

<template>
  <div class="home-page">
    <header class="home-header">
      <div class="brand">预约制美甲管理系统</div>
      <div class="user-area">
        <span class="welcome">欢迎，{{ displayName }}（{{ roleName }}）</span>
        <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <main class="home-main">
      <el-tabs v-model="activeTab" class="home-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="预约美甲" name="booking">
          <CustomerSlotView @appointment-created="handleAppointmentCreated" />
        </el-tab-pane>
        <el-tab-pane label="我的预约" name="appointments">
          <CustomerAppointments ref="appointmentsRef" />
        </el-tab-pane>
      </el-tabs>
    </main>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 32px;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
}

.brand {
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.welcome {
  color: #606266;
  font-size: 14px;
}

.home-main {
  max-width: 1080px;
  margin: 32px auto;
  padding: 0 24px;
}

.home-tabs {
  padding: 16px 20px 20px;
  background: #ffffff;
  border-radius: 8px;
}
</style>
