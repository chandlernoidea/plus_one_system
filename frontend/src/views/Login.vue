<script setup>
/**
 * 登录页：左侧品牌叙事 + 演示账号，右侧表单，中间一条竖线分栏。
 *
 * 版面意图是「打开一本册子」：左边讲这是什么系统、怎么试，
 * 右边只留一件事——登录。演示账号直接列在左侧，答辩时不用翻文档。
 */
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

const demoAccounts = [
  { role: '管理员', username: 'admin', note: '全店档期与结算' },
  { role: '美甲师', username: 'staff1', note: '排班、审批与工资' },
  { role: '顾客', username: 'cust1', note: '看空档与预约' }
]

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
    <div class="login-brand">
      <span class="brand-mark" aria-hidden="true">指</span>
      <span class="brand-text">
        <span class="brand-name">指间预约</span>
        <span class="brand-sub">ONE FINGER STUDIO</span>
      </span>
    </div>

    <div class="login-grid">
      <section class="login-lede">
        <span class="eyebrow">预约制美甲管理 · SINCE 2026</span>
        <h1 class="login-title">
          一张表，<br />
          看清全店的档期。
        </h1>
        <p class="login-copy">
          排班、预约、款式物料与月底结算，围绕同一天展开。
          <br />
          管理者看进度，美甲师看自己的空档，顾客只挑还没被约走的时间。
        </p>

        <div class="login-demo">
          <div class="demo-head">
            <span class="mono">DEMO ACCOUNTS</span>
            <span class="demo-hint">密码均为 123456</span>
          </div>
          <ul class="demo-list">
            <li v-for="account in demoAccounts" :key="account.username" class="demo-row">
              <span class="demo-role">{{ account.role }}</span>
              <span class="demo-user mono">{{ account.username }}</span>
              <span class="demo-note">{{ account.note }}</span>
            </li>
          </ul>
        </div>
      </section>

      <section class="login-panel">
        <div class="panel-head">
          <span class="eyebrow">SIGN IN</span>
          <h2 class="panel-title">登录</h2>
        </div>

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
              placeholder="admin / staff1 / cust1"
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

          <el-button class="login-button" type="primary" :loading="loading" @click="handleLogin">
            进入工作台
          </el-button>
        </el-form>

        <p class="panel-foot">
          课程演示环境，数据可重置；正式使用前请替换 JWT 密钥与数据库口令。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: var(--s6) var(--s7) var(--s7);
}

/* ---- 品牌 ---- */

.login-brand {
  display: flex;
  align-items: center;
  gap: var(--s3);
}

.brand-mark {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  background: var(--ink);
  color: var(--paper-raised);
  font-family: var(--font-display);
  font-size: 16px;
  line-height: 1;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-name {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.16em;
  line-height: 1.1;
}

.brand-sub {
  color: var(--ink-4);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.24em;
  line-height: 1;
}

/* ---- 版面 ---- */

.login-grid {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: var(--s8);
  align-items: center;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: var(--s7) 0;
}

.login-lede {
  display: flex;
  flex-direction: column;
  gap: var(--s5);
}

.login-title {
  font-family: var(--font-display);
  font-size: 50px;
  font-weight: 400;
  line-height: 1.14;
  letter-spacing: -0.02em;
}

.login-copy {
  color: var(--ink-3);
  font-size: 13px;
  line-height: 1.9;
  letter-spacing: 0.02em;
}

/* ---- 演示账号 ---- */

.login-demo {
  margin-top: var(--s4);
  padding-top: var(--s4);
  border-top: 1px solid var(--line);
}

.demo-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--s3);
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.2em;
}

.demo-hint {
  font-family: var(--font-ui);
  letter-spacing: 0.06em;
}

.demo-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.demo-row {
  display: grid;
  grid-template-columns: 56px 72px minmax(0, 1fr);
  gap: var(--s2);
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
  align-items: baseline;
}

.demo-role {
  color: var(--ink-2);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.demo-user {
  color: var(--lacquer);
  font-size: 11px;
}

.demo-note {
  color: var(--ink-4);
  font-size: 11px;
}

/* ---- 登录面板 ---- */

.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-self: stretch;
  padding-left: var(--s7);
  border-left: 1px solid var(--line);
}

.panel-head {
  display: flex;
  flex-direction: column;
  gap: var(--s3);
  margin-bottom: var(--s5);
}

.panel-title {
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: var(--s2);
  letter-spacing: 0.16em;
}

.panel-foot {
  margin-top: var(--s5);
  color: var(--ink-4);
  font-size: 11px;
  line-height: 1.8;
}

@media (max-width: 960px) {
  .login-page {
    padding: var(--s4);
  }

  .login-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--s6);
    padding: var(--s5) 0;
    align-items: start;
  }

  .login-title {
    font-size: 34px;
  }

  .login-copy br {
    display: none;
  }

  .login-panel {
    padding: var(--s5) 0 0;
    border-top: 1px solid var(--line);
    border-left: 0;
  }
}
</style>
