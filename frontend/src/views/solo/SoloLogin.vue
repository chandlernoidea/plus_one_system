<script setup>
/**
 * 单人版专用登录：一个账号，一屏两栏。
 *
 * 与多角色版的 Login.vue 分开：那边要讲三个角色怎么试，这边只有老板一个人，
 * 所以把「这是什么系统」压成一句话，把版面让给输入框。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../../api/auth'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorText = ref('')

async function submit() {
  if (!form.username.trim() || !form.password) {
    errorText.value = '账号和密码都要填'
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const response = await login({
      username: form.username.trim(),
      password: form.password
    })
    const payload = response.data.data
    if (payload.roleCode !== 'ADMIN') {
      userStore.logout()
      errorText.value = '这个账号不是老板账号，单人版只认管理员'
      return
    }
    userStore.setLogin(payload)
    await router.replace('/solo/today')
  } catch (error) {
    errorText.value = error.response?.data?.message || '登录失败，检查账号密码'
  } finally {
    loading.value = false
  }
}

/** 只填账号名：密码按实例不同（部署后已经改掉初始口令），不替用户填。 */
function fillDemo() {
  form.username = 'admin'
}
</script>

<template>
  <div class="login">
    <section class="panel">
      <header class="brand">
        <span class="brand-mark" aria-hidden="true">指</span>
        <span class="brand-text">
          <span class="brand-name">指间工作室</span>
          <span class="brand-sub mono">SOLO STUDIO · V0.8.5</span>
        </span>
      </header>

      <h1 class="title">登录</h1>
      <p class="note">档期与现金流的台账。老板一个人用，账号也只有一个人。</p>

      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">账号</span>
          <input
            v-model="form.username"
            class="input"
            type="text"
            autocomplete="username"
            placeholder="admin"
          />
        </label>

        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="form.password"
            class="input"
            type="password"
            autocomplete="current-password"
            placeholder="••••••"
          />
        </label>

        <p v-if="errorText" class="error">{{ errorText }}</p>

        <button class="submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>

      <footer class="foot">
        <button type="button" class="link" @click="fillDemo">填入账号</button>
      </footer>
    </section>

    <aside class="side" aria-hidden="true">
      <span class="side-eyebrow mono">ONE FINGER · SINCE 2026</span>
      <p class="side-quote">
        防止漏人、<br />漏时间、<br />漏款、漏料。
      </p>
      <span class="side-rule"></span>
      <p class="side-meta">今天给谁做 · 材料够不够 · 定金收没收 · 这个月对得上</p>
    </aside>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1fr);
  background: var(--paper);
}

.panel {
  display: flex;
  width: 100%;
  max-width: 380px;
  margin: 0 auto;
  flex-direction: column;
  justify-content: center;
  padding: var(--s6) var(--s5) var(--s8);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--s3);
  margin-bottom: var(--s7);
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
  letter-spacing: 0.16em;
  line-height: 1.1;
}

.brand-sub {
  color: var(--ink-4);
  font-size: 8px;
  letter-spacing: 0.22em;
  line-height: 1;
}

.title {
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 400;
  letter-spacing: 0.04em;
  line-height: 1.1;
}

.note {
  margin-top: var(--s2);
  color: var(--ink-3);
  font-size: 12px;
  line-height: 1.7;
}

.form {
  margin-top: var(--s6);
}

.field {
  display: block;
  margin-bottom: var(--s4);
}

.field-label {
  display: block;
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.input {
  width: 100%;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 16px;
  outline: none;
  transition: border-color var(--dur-1) var(--ease);
}

.input:focus {
  border-bottom-color: var(--lacquer);
}

.error {
  margin-bottom: var(--s3);
  color: var(--lacquer);
  font-size: 12px;
}

.submit {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.3em;
  transition: background var(--dur-1) var(--ease);
}

.submit:hover:not(:disabled) {
  background: var(--lacquer-deep);
  border-color: var(--lacquer-deep);
}

.submit:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.foot {
  display: flex;
  justify-content: space-between;
  gap: var(--s3);
  margin-top: var(--s5);
}

.link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-4);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.link:hover {
  color: var(--ink);
}

/* 宽屏：左边一句定位，右边登录 */
.side {
  display: none;
}

@media (min-width: 900px) {
  .login {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .panel {
    max-width: 420px;
    padding: var(--s8) var(--s6);
    border-right: 1px solid var(--line);
  }

  .side {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: var(--s4);
    padding: var(--s8) var(--s7);
  }

  .side-eyebrow {
    color: var(--ink-4);
    font-size: 10px;
    letter-spacing: 0.24em;
  }

  .side-quote {
    font-family: var(--font-display);
    font-size: 40px;
    font-weight: 400;
    letter-spacing: 0.02em;
    line-height: 1.3;
  }

  .side-rule {
    width: 64px;
    height: 1px;
    background: var(--lacquer);
  }

  .side-meta {
    max-width: 320px;
    color: var(--ink-3);
    font-size: 12px;
    line-height: 1.9;
  }
}
</style>
