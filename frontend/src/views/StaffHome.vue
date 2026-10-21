<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import StaffSchedule from './staff/StaffSchedule.vue'
import StaffAppointments from './staff/StaffAppointments.vue'
import StaffSettlement from './staff/StaffSettlement.vue'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('schedule')

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
      <el-tabs v-model="activeTab" class="home-tabs">
        <el-tab-pane label="我的排班" name="schedule">
          <StaffSchedule />
        </el-tab-pane>
        <el-tab-pane label="预约管理" name="appointments">
          <StaffAppointments />
        </el-tab-pane>
        <el-tab-pane label="我的工资" name="settlement">
          <StaffSettlement />
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
  max-width: 1180px;
  margin: 32px auto;
  padding: 0 24px;
}

.home-tabs {
  padding: 16px 20px 20px;
  background: #ffffff;
  border-radius: 8px;
}
</style>
