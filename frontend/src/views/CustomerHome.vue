<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import CustomerSlotView from './customer/CustomerSlotView.vue'

const router = useRouter()
const userStore = useUserStore()

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
      <h1 class="page-title">查看美甲师档期</h1>
      <CustomerSlotView />
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

.page-title {
  margin: 0 0 20px;
  color: #303133;
  font-size: 24px;
}
</style>
