# 小组开发约定 — E 组

> 项目名称：鲜识（FreshScan）  
> 小组成员：陈润扬、黄哲轩  
> 制定日期：2026 年 5 月下旬  

---

## 一、工程规范

### 1.1 目录结构

项目采用 Clean Architecture 三层分离结构，各层职责明确：

- `data/` — 数据访问层：AI 服务、数据库 DAO、推理引擎、网络请求
- `domain/` — 业务逻辑层：纯 Kotlin 领域模型、Repository 接口、UseCase
- `ui/` — 表现层：Compose Screen、ViewModel、UiState
- `di/` — 依赖注入：Hilt Module、Qualifier 定义
- `navigation/` — 路由管理：NavGraph、路由常量
- `util/` — 工具类：Logger、ImagePreprocessor、MathUtils

新增功能模块按以下方式放置：数据源/引擎放 `data/` 对应子包，ViewModel 和 Screen 放 `ui/screen/` 对应子包，状态类放 `ui/state/` 或同 Screen 包内。

### 1.2 命名规则

| 类型 | 规则 | 示例 |
|------|------|------|
| 类文件 | 大驼峰，功能+类型 | `QwenAIService.kt`、`DietPlanEngine.kt` |
| UI 状态类 | 功能名+UiState | `AnalysisUiState`、`PersonalizeUiState` |
| ViewModel | 功能名+ViewModel | `AnalysisViewModel`、`DietPlanViewModel` |
| Screen | 功能名+Screen | `PersonalizeScreen`、`DietPlanScreen` |
| 数据实体 | 功能名+Entity | `UserProfileEntity`、`DietPlanEntity` |
| DAO | 功能名+Dao | `DietPlanDao`、`UserProfileDao` |
| 测试类 | 被测类+Test | `QwenAIServiceTest`、`SettingsViewModelTest` |

### 1.3 注释规范

- 所有 `public` 类和 `public` 方法必须附带 KDoc 注释，说明功能、参数含义和可能抛出的异常
- 复杂算法（如 TDEE 计算、三阶段推理管线）需在方法体内添加行内注释解释关键步骤
- TODO 注释格式：`// TODO(姓名): 描述`，便于后续追踪

---

## 二、过程约定

### 2.1 代码提交

- **提交频率**：开发期间每日至少 1 次有效提交，禁止积压多天后批量提交
- **提交规范**：遵循 Conventional Commits 格式，前缀标识变更类型：
  - `feat`: 新功能
  - `fix`: Bug 修复
  - `test`: 测试相关
  - `chore`: 构建、配置、文档等非功能性变更
  - `refactor`: 重构（不改变外部行为）
- **提交说明**：简洁明了，说明"做了什么"和"为什么"，例如 `feat(v3): add AI seven-day diet plan generation` 而非 `update code`

### 2.2 分支管理

- `main`：稳定分支，只接受通过测试的代码
- 功能开发使用本地分支，命名格式 `feat/模块名-功能描述`（如 `feat/v3-ai-service`），完成后合并回 `main`
- Bug 修复使用 `fix/问题描述` 分支（如 `fix/tdee-gender-calculation`）
- 合并前必须确保 `assembleDebug` 和 `testDebugUnitTest` 全部通过

### 2.3 冲突解决

- 合并前先在本地 `rebase main`，解决冲突后再推送
- 冲突解决后必须重新运行单元测试，确认无回归
- 重大冲突需在群内讨论后决定合并策略

---

## 三、沟通机制

### 3.1 日常沟通

- **主要渠道**：项目 QQ 群，所有技术讨论、进度同步、决策记录均在群内以文字形式留存
- **每日站会**（线上或群内文字）：简要同步"昨日完成、今日计划、当前阻塞"，每人不超过 3 分钟
- **紧急问题**：电话或语音通话，但结论必须在群内以文字形式补记

### 3.2 技术决策

- 涉及架构变更或技术方案选择时，需在群内发起专题讨论
- 讨论格式：提出问题 → 列出可选方案（至少 2 个）→ 分析各方案利弊 → 给出推荐并说明理由 → 确认后记录决策结论
- 技术决策记录格式：`[决策] 主题：结论（原因）`

### 3.3 问题反馈

- 发现 bug 或设计问题时，在群内描述现象并标注严重等级（Critical/High/Medium/Low）
- Critical 和 High 级别问题须当日修复，Medium 级别问题在本迭代内修复，Low 级别问题排入下一迭代
- 修复后在群内回复确认，并附相关提交链接

---

## 四、测试验证

### 4.1 测试要求

| 测试类型 | 要求 | 责任人 |
|----------|------|--------|
| 单元测试 | 新增 Engine/Service/ViewModel 必须附带 JUnit 测试，覆盖正常路径和主要异常路径 | 开发者（陈润扬） |
| UI 适配测试 | 在至少 2 种屏幕尺寸（手机竖屏 + 横屏）下验证 UI 布局正确性 | 测试者（黄哲轩） |
| 功能验收 | 每个版本发布前进行全功能回归测试 | 测试者（黄哲轩） |

### 4.2 测试框架

- 单元测试：JUnit 4 + MockK + kotlinx.coroutines.test + Turbine
- 测试命名：`given 前提 when 操作 then 预期结果`
- 每个测试方法只验证一个行为点

### 4.3 Bug 管理

- Bug 通过 Leangoo 看板录入，标注标题、复现步骤、严重等级、指派人
- Critical bug 修复时限：4 小时；High：当日；Medium：3 日内；Low：下一迭代
- 修复后测试者验证关闭，未通过则重新打开

---

## 五、执行检查清单

以下为小组在开发过程中实际执行的具体举措：

- [x] 项目目录结构按 Clean Architecture 三层分离组织
- [x] 文件命名遵循统一的大驼峰+类型后缀规则
- [x] 所有公开 API 附带 KDoc 文档注释
- [x] 提交信息遵循 Conventional Commits 规范（19 次结构化提交）
- [x] 合并前执行 `assembleDebug` + `testDebugUnitTest` 双重验证
- [x] 三轮代码审查共发现 53 个问题，全部修复并验证通过
- [x] 325 个单元测试，通过率 100%
- [x] 技术决策通过 QQ 群文字讨论并留存记录
