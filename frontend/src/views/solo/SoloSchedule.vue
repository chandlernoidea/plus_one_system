<script setup>
/**
 * 排期（P4）。这一页要做周视图 7 列、空档插队、顺延预览，
 * 依赖的接口已在 P2 就绪（/schedule/week、/jumps、/shifts）。
 */
import SoloPlaceholder from './SoloPlaceholder.vue'
</script>

<template>
  <SoloPlaceholder
    eyebrow="SOLO · WEEK"
    title="排期"
    phase="P4 落地：周视图与插队 / 顺延交互"
    :items="[
      '周视图 7 列，显示预约块与机动位，点某天进入作业单',
      '空档识别：任意两单之间标「可塞 N 分钟」',
      '插队：从空档点「插一单」，校验时长后建单（来源标记插队）',
      '顺延：选中后续预约预览平移结果，确认后逐单改期并生成待通知清单',
      '冲突：允许强排但标红，作业单顶部提示冲突数'
    ]"
    note="接口已就绪：GET /api/solo/schedule/week、POST /api/solo/jumps、POST /api/solo/shifts/preview、POST /api/solo/shifts。"
  />
</template>
