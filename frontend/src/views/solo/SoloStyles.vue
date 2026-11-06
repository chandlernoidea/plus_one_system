<script setup>
/**
 * 款式库（P5，二级入口）。款式兼作品集，能按标签筛、能导出案例图。
 */
import SoloPlaceholder from './SoloPlaceholder.vue'
</script>

<template>
  <SoloPlaceholder
    eyebrow="SOLO · STYLES"
    title="款式库"
    phase="P5 落地：款式与作品集"
    :items="[
      '网格卡片：封面、名称、时长、价格、标签',
      '新建 / 编辑：封面、材料清单、标准时长、标准价',
      '按标签筛选后导出竖版九宫格案例图，带水印，可直接发小红书'
    ]"
    note="建单时的款式选择已经读这里的数据；下架用 enabled=0，历史单的项目仍保留名称与价格快照。"
  />
</template>
