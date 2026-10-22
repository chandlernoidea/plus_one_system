<script setup>
import { computed } from 'vue'
import { useUserStore } from '../store/user'
import { monthName } from '../utils/date'

const props = defineProps({
  nav: {
    type: Array,
    default: () => []
  },
  active: {
    type: String,
    default: ''
  },
  roleLabel: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:active', 'logout'])

const userStore = useUserStore()

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || ''
)

const numbered = computed(() =>
  props.nav.map((item, index) => ({
    ...item,
    index: String(index + 1).padStart(2, '0')
  }))
)

const today = new Date()
const dateStamp = computed(() => {
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  const weekday = ['日', '一', '二', '三', '四', '五', '六'][today.getDay()]
  return `${month}.${day} 周${weekday}`
})

const seasonStamp = computed(() => `${today.getFullYear()} ${monthName(today)}`)
</script>

<template>
  <div class="shell">
    <header class="shell-bar">
      <div class="shell-inner">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">指</span>
          <span class="brand-text">
            <span class="brand-name">指间预约</span>
            <span class="brand-sub">ONE FINGER STUDIO</span>
          </span>
        </div>

        <nav class="shell-nav" aria-label="主导航">
          <button
            v-for="item in numbered"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ 'is-active': item.key === active }"
            :aria-current="item.key === active ? 'page' : undefined"
            @click="emit('update:active', item.key)"
          >
            <span class="nav-index mono">{{ item.index }}</span>
            <span class="nav-label">{{ item.label }}</span>
          </button>
        </nav>

        <div class="shell-user">
          <span class="shell-date mono">{{ dateStamp }}</span>
          <span class="shell-divider" aria-hidden="true"></span>
          <span class="shell-person">
            <span class="shell-name">{{ displayName }}</span>
            <span v-if="roleLabel && roleLabel !== displayName" class="shell-role">{{ roleLabel }}</span>
          </span>
          <button type="button" class="logout-link" @click="emit('logout')">退出</button>
        </div>
      </div>
    </header>

    <main class="shell-main">
      <slot />
    </main>

    <footer class="shell-foot">
      <span class="mono">ONE FINGER · {{ seasonStamp }}</span>
      <span class="mono">预约制美甲管理系统</span>
    </footer>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
}

.shell-bar {
  position: sticky;
  top: 0;
  z-index: 40;
  border-bottom: 1px solid var(--line);
  background: rgba(243, 238, 228, 0.94);
  backdrop-filter: blur(6px);
}

.shell-inner {
  display: flex;
  align-items: center;
  gap: var(--s6);
  width: 100%;
  max-width: var(--shell-max);
  margin: 0 auto;
  padding: 0 var(--s6);
  height: 68px;
}

/* ---- 品牌 ---- */

.brand {
  display: flex;
  flex: none;
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

/* ---- 导航：编号 + 小字，当前项用朱砂细线 ---- */

.shell-nav {
  display: flex;
  flex: 1 1 auto;
  align-items: stretch;
  gap: var(--s5);
  height: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.shell-nav::-webkit-scrollbar {
  display: none;
}

.nav-item {
  position: relative;
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 13px;
  letter-spacing: 0.06em;
  white-space: nowrap;
  transition: color var(--dur-1) var(--ease);
}

.nav-item:hover {
  color: var(--ink);
}

.nav-item.is-active {
  color: var(--ink);
}

.nav-item::after {
  position: absolute;
  bottom: 18px;
  left: 0;
  width: 0;
  height: 1.5px;
  background: var(--lacquer);
  content: "";
  transition: width var(--dur-2) var(--ease);
}

.nav-item.is-active::after {
  width: 100%;
}

.nav-index {
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.nav-item.is-active .nav-index {
  color: var(--lacquer);
}

/* ---- 右侧 ---- */

.shell-user {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--s3);
  height: 100%;
}

.shell-date {
  color: var(--ink-4);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.shell-divider {
  width: 1px;
  height: 14px;
  background: var(--line);
}

.shell-person {
  display: flex;
  align-items: baseline;
  gap: var(--s2);
}

.shell-name {
  font-size: 13px;
  letter-spacing: 0.04em;
}

.shell-role {
  color: var(--ink-4);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.logout-link {
  padding: 0 0 2px;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: 12px;
  letter-spacing: 0.06em;
  transition: border-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.logout-link:hover {
  border-bottom-color: var(--lacquer);
  color: var(--ink);
}

/* ---- 主体 ---- */

.shell-main {
  flex: 1 1 auto;
  width: 100%;
  max-width: var(--shell-max);
  margin: 0 auto;
  padding: var(--s6) var(--s6) var(--s8);
}

.shell-foot {
  display: flex;
  justify-content: space-between;
  max-width: var(--shell-max);
  width: 100%;
  margin: 0 auto;
  padding: var(--s4) var(--s6) var(--s6);
  border-top: 1px solid var(--line);
  color: var(--ink-4);
  font-size: 9px;
  letter-spacing: 0.16em;
}

@media (max-width: 960px) {
  .shell-inner {
    flex-wrap: wrap;
    gap: var(--s3);
    height: auto;
    padding: var(--s3) var(--s4);
  }

  .shell-nav {
    order: 3;
    width: 100%;
    gap: var(--s4);
    padding-bottom: 2px;
    mask-image: linear-gradient(to right, #000 88%, transparent 100%);
  }

  .nav-item::after {
    bottom: -1px;
  }

  .shell-date,
  .shell-divider {
    display: none;
  }

  .shell-user {
    margin-left: auto;
  }

  .shell-main {
    padding: var(--s4) var(--s4) var(--s7);
  }

  .shell-foot {
    padding: var(--s3) var(--s4) var(--s5);
  }
}
</style>
