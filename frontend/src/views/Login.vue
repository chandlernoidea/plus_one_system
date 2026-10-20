<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useUserStore } from '../store/user'

const ROLE_HOME = {
  ADMIN: '/admin',
  STAFF: '/staff',
  CUSTOMER: '/customer'
}

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) {
    return
  }

  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  loading.value = true
  try {
    const response = await login(form)
    const loginResponse = response.data.data
    userStore.setLogin(loginResponse)

    const target = ROLE_HOME[loginResponse.roleCode]
    if (!target) {
      userStore.logout()
      ElMessage.error('当前账号角色不支持登录')
      return
    }
    await router.replace(target)
  } catch (error) {
    // 失败提示已由 request.js 响应拦截器统一处理。
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <h1>预约制美甲管理系统</h1>
          <p>阶段 1 · 登录鉴权</p>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            clearable
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>

        <el-button
          class="login-button"
          type="primary"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <el-divider>演示账号</el-divider>
      <div class="demo-accounts">
        <div>管理员：admin / 123456</div>
        <div>美甲师：staff1 / 123456</div>
        <div>顾客：cust1 / 123456</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: linear-gradient(135deg, #fdf2f8 0%, #eef2ff 100%);
}

.login-card {
  width: 420px;
  border-radius: 14px;
}

.login-header {
  text-align: center;
}

.login-header h1 {
  margin: 4px 0 8px;
  font-size: 22px;
  color: #1f2937;
}

.login-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}

.demo-accounts {
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
  text-align: center;
}
</style>
