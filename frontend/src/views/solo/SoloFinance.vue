<script setup>
/**
 * 财务（P6，二级入口）：月度概览 + 三本明细 + 对账。
 */
import SoloPlaceholder from './SoloPlaceholder.vue'
</script>

<template>
  <SoloPlaceholder
    eyebrow="SOLO · LEDGER"
    title="财务"
    phase="P6 落地：收支与对账"
    :items="[
      '月度概览：收入、支出、净收入、定金没收额',
      '三本明细：收入流水、支出流水、定金流水，可跳回预约',
      '对账校验：收入 − 支出 与明细求和比对，不一致标出差异项'
    ]"
    note="接口已就绪：GET /api/solo/finance/summary、/finance/flows、/finance/deposits。"
  />
</template>
