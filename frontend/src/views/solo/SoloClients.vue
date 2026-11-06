<script setup>
/**
 * 客户（P5）。档案按最近到访排序，三合一搜索；详情里回看历史与做过的款。
 */
import SoloPlaceholder from './SoloPlaceholder.vue'
</script>

<template>
  <SoloPlaceholder
    eyebrow="SOLO · CLIENTS"
    title="客户"
    phase="P5 落地：客户档案"
    :items="[
      '按最近到访排序，搜索支持短号 / 昵称 / 手机后四位',
      '新建自动生成 #C 短号，重名时提示加区分词',
      '详情显示历史预约、做过的款式、常用色号、备注',
      '数据可删除：删除后历史预约保留并标「客户已删除」'
    ]"
    note="建单时已经可以临时搜客户、快速建档；这一页把它们整理成完整档案。"
  />
</template>
