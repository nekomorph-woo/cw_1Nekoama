# M3阶段开发计划：HTML模板和前端改造

## 功能概述

### 功能名称
M3阶段：HTML模板和前端改造

### 功能目标
简化HTML报告模板，移除复杂功能，专注于核心的调用关系图和分析功能，通过前端JavaScript实现模糊匹配，提升用户体验和分析效率。在调用链分析中实现完整调用链筛选，基于选中方法递归显示项目内完整调用关系。

### 任务状态
- **当前状态**: 开发中

---

## 需求分析 (规划阶段)

### 功能需求
1. **HTML模板简化** - 移除图表类型选择器、场景分析标签页、POJO分析标签页
2. **调用关系图重构** - 单一标签页通过模式切换在"调用关系图"和"调用链分析"间切换
3. **前端模糊匹配** - 纯前端实现方法名模糊匹配，使用Levenshtein距离算法
4. **完整调用链筛选** - 基于选中方法递归筛选直至调用链中方法不再有项目内调用关系
5. **力导向图优化** - 性能和交互平衡，支持大数据量渲染和丰富交互
6. **数据结构适配** - 优化JSON数据结构支持前端高效模糊匹配

### 技术需求
- **依赖组件**: AntV G6、Font Awesome、自定义CSS/JavaScript
- **数据模型**: 扩展AnalysisResult包含完整的方法列表和调用链数据
- **UI需求**: 保守简化包级和类级视图，简化筛选条件为复杂度和调用次数
- **性能要求**: 大数据量渲染优化(>1000节点)，模糊匹配响应<500ms

### 非功能需求
- **可扩展性**: 保留扩展点，便于后续添加新的图表类型
- **安全性**: 纯前端处理，避免后端接口安全风险

---

## 任务分解 (执行阶段)

### 大目标: 完成M3阶段HTML模板和前端改造

#### 子功能A: HTML模板大幅简化
> **优先级**: 高
> **状态**: ⏳ 待开始

##### [阶段1] 核心HTML结构调整
- [ ] **任务A1**: 移除图表类型选择器(layout-select控件)
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务A2**: 移除场景分析标签页(scene-view)和相关组件
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务A3**: 移除POJO分析标签页(pojo-view)和相关组件
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务A4**: 简化统计概览，移除业务场景统计卡片
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务A5**: 修改分析范围显示，展示项目绝对路径
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

---

#### 子功能B: 调用关系图Tab重构
> **优先级**: 高
> **状态**: ⏳ 待开始

##### [阶段1] 模式切换架构设计
- [ ] **任务B1**: 重构method-view标签页为模式切换架构
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

##### [阶段2] 筛选功能简化
- [ ] **任务B2**: 简化筛选条件为复杂度和调用次数两个核心筛选
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务B3**: 重构功能按钮区域，仅保留重置视图和导出图片
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

---

#### 子功能C: 调用链分析模式实现
> **优先级**: 高
> **状态**: ⏳ 待开始

##### [阶段1] 双下拉框控件实现
- [ ] **任务C1**: 添加入口方法下拉框控件布局
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务C2**: 添加搜索下拉框控件布局支持模糊匹配
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

- [ ] **任务C3**: 添加方法搜索输入框和分析按钮布局
  - 涉及文件: `src/main/resources/templates/reports/dependency-analysis-template.html`
  - 状态: ⏳ 待开始

---

#### 子功能D: 前端JavaScript模糊匹配实现
> **优先级**: 高
> **状态**: ⏳ 待开始

##### [阶段1] 模糊匹配算法实现
- [ ] **任务D1**: 实现Levenshtein距离计算字符串相似度算法
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务D2**: 实现实时搜索，输入即匹配功能
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务D3**: 添加搜索结果限制和相似度排序
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

---

#### 子功能E: 完整调用链筛选逻辑
> **优先级**: 高
> **状态**: ⏳ 待开始

##### [阶段1] 调用链递归筛选算法
- [ ] **任务E1**: 实现基于选中方法的完整调用链递归筛选算法
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务E2**: 实现调用链终止条件判断(项目内不再有调用关系)
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务E3**: 实现筛选后的调用链渲染(力导向图)
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

---

#### 子功能F: 力导向图性能和交互优化
> **优先级**: 中
> **状态**: ⏳ 待开始

##### [阶段1] 性能优化
- [ ] **任务F1**: 优化大数据量下的力导向图渲染性能(>1000节点)
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务F2**: 实现懒加载和增量渲染机制
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

##### [阶段2] 交互优化
- [ ] **任务F3**: 实现平滑的缩放、拖拽、高亮交互
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务F4**: 实现节点搜索和快速定位功能
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

---

#### 子功能G: 数据结构适配
> **优先级**: 中
> **状态**: ⏳ 待开始

##### [阶段1] JSON数据结构优化
- [ ] **任务G1**: 修改DependencyReportGenerator确保JSON包含完整方法列表
  - 涉及文件: `src/main/kotlin/com/cw2/nekoama/core/reporting/DependencyReportGenerator.kt`
  - 状态: ⏳ 待开始

- [ ] **任务G2**: 优化数据结构支持前端高效模糊匹配和筛选
  - 涉及文件: `src/main/kotlin/com/cw2/nekoama/core/reporting/DependencyReportGenerator.kt`
  - 状态: ⏳ 待开始

- [ ] **任务G3**: 添加复杂度和fanIn统计数据到JSON中
  - 涉及文件: `src/main/kotlin/com/cw2/nekoama/core/reporting/DependencyReportGenerator.kt`
  - 状态: ⏳ 待开始

---

#### 子功能H: 导出图片功能实现
> **优先级**: 低
> **状态**: ⏳ 待开始

##### [阶段1] 图片导出实现
- [ ] **任务H1**: 实现G6图导出为PNG/JPG格式
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

- [ ] **任务H2**: 确保导出图片包含当前筛选状态和高质量
  - 涉及文件: `src/main/resources/static/js/g6-visualizer.js`
  - 状态: ⏳ 待开始

---

## 检查点记录

### M3阶段进度跟踪
- **完成内容**:
  - [ ] HTML模板大幅简化
  - [ ] 调用关系图Tab重构
  - [ ] 前端模糊匹配实现
  - [ ] 完整调用链筛选逻辑
  - [ ] 力导向图优化
  - [ ] 数据结构适配
  - [ ] 导出图片功能
- **技术要点**:
  - 前端纯JavaScript实现，无需后端接口
  - Levenshtein距离算法实现模糊匹配
  - 递归算法实现完整调用链筛选
  - 力导向图性能和交互平衡优化
- **遗留问题**:
  - [ ] 待解决性能问题(如发现)
  - **下一步**: M4阶段性能优化和全面测试

---

## 相关文档

- [TASK_PLAN.md](TASK_PLAN.md) - 总体任务规划
- [CLAUDE.md](CLAUDE.md) - 项目架构和开发指南
- [AntV G6文档](https://g6.antv.vision/zh/docs/api/graph) - G6图可视化库
- [Levenshtein距离算法](https://en.wikipedia.org/wiki/Levenshtein_distance) - 字符串相似度算法

---

## ✅ 完成标准

- [ ] HTML模板大幅简化，移除复杂功能
- [ ] 调用关系图重构为模式切换架构
- [ ] 前端模糊匹配功能正常工作
- [ ] 完整调用链筛选逻辑实现
- [ ] 力导向图性能和交互优化完成
- [ ] 数据结构适配前端需求
- [ ] 导出图片功能正常
- [ ] 代码通过编译检查
- [ ] 功能完整性测试通过

---

## 备注

**关键决策记录**:
1. 调用关系图架构: 选择方案A - 单一标签页通过模式切换
2. 前端模糊匹配范围: 选择选项A - 仅在"调用链分析"模式中提供
3. 力导向图优化: 性能和交互平衡
4. 数据结构简化程度: 保守简化，保留包级和类级视图
5. 筛选功能复杂度: 简化，仅保留复杂度和调用次数两个核心筛选

**特殊需求**: 完整调用链筛选逻辑需要基于选中方法递归筛选直至调用链中的方法不再有项目内调用关系。

---

**最后更新**: 2025-11-29
**文档版本**: v1.0