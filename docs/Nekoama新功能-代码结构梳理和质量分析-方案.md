# 需求背景
1. 我负责的公司业务模块代码是基于Java语言和Spring Boot框架的智能家居设备联动模块，该模块主要负责实现家庭设备的自动化控制和场景管理功能。
2. 当前模块存在以下主要问题：
    - 代码分包不清晰，导致模块间依赖关系混乱，难以维护和扩展。
    - 类职责不明确，导致类的功能过于复杂，难以理解和维护。
    - 方法调用链过长，导致代码可读性差，难以调试和维护。
    - POJO使用不当，难以清晰地使用和复用。
3. 总之，代码维护的心智负担过重。

# 功能目的
在不让外部人员/外部或内部AI/代码外发等渠道接触公司业务代码的前提下（离线）：
- 厘清业务代码的调用情况和业务子模块。
- 明确划分代码中各个子模块的边界（包括业务边界和代码边界）。
- 完成领导下发的任务：能够准确评估新需求在该业务模块的工作量和影响点。

# 基本要求
1. 插件用 PSI 分析多种类数据依赖 → 生成依赖数据（JSON）
2. 插件把 JSON数据全部 **写进一个 HTML 模板里**（嵌到 `<script>` 变量里），生成类似：
    - `项目根目录/neko-analysis/nekoama-deps-20251115-1/具体文件数据`
    - `具体文件数据包含以来数据JSON和HTML文件`
    - HTML以CDN（`<script src="https://unpkg.com/@antv/g6@5/dist/g6.min.js"></script>`）的方式引入AntV G6，为绘制依赖图提供API库
    - HTML文件包含HTML结构、CSS、原生JS，不依赖任何前端框架
    - HTML文件使用多tab的方式切换不同用途的依赖图
    - [我对G6框架不太熟悉，需要你继续补充，可以有哪些实用交互？]
3. 仅支持Java代码项目

# 初步方案

## 大致流程
1. PSI 分析（排除 JDK/第三方包等） → 构建 `DependencyGraph` 对象；
2. 用任意 JSON 库（序列化为字符串；
3. 读取模板 HTML 到字符串；
4. `template.replace("__DATA_PLACEHOLDER__", jsonString)`；
5. 把结果写到指定的输出目录下；
6. 可选：用 `Desktop.getDesktop().browse(outputFile.toURI())` 直接调用系统浏览器打开。

## PSI入口

### IDEA插件集成
- 在允许的PSI入口右键菜单 → Nekoama → 代码依赖分析（Analyzer Code Deps）
- IDEA → Tools → Nekoama
  - 将已有的 `Nekoama: Analyzer Unused Code` 作为二级功能收入 `Nekoama` 菜单，并改名 `Analyzer Unused Code`
  - 新增 `Analyzer Code Deps` 二级功能收入 `Nekoama` 菜单

### 可被分析的入口
- 代码包
- 类
- 方法
- 项目根目录全量代码分析（仅 `IDEA → Tools → Nekoama → Analyzer Code Deps` 支持）

## 完整的PSI分析数据结构： JSON Schema 设计

### 一、顶层结构

```jsonc
{
  "metadata": { ... },      // 分析元数据
  "packages": [ ... ],      // 包信息
  "classes": [ ... ],       // 类信息
  "methods": [ ... ],       // 方法信息
  "fields": [ ... ],        // 字段信息
  "pojos": [ ... ],         // POJO 使用情况
  "callGraph": {            // 调用关系图
    "edges": [ ... ]
  },
  "sceneDefinitions": [ ... ] // 场景定义（可选，可后期手动标注）
}
```

---

### 二、详细 Schema 定义

#### 1. `metadata` - 分析元数据

```jsonc
{
  "metadata": {
    "projectName": "{project.name}",
    "moduleName": "{project.name}",
    "analysisTime": "2025-11-16T10:30:00Z+08:00",
    "scope": {
      "rootPackage": "com.example.smarthome",
      "includedPackages": ["com.example.smarthome.linkage"],
      "excludedPackages": ["com.example.smarthome.test"],
      "maxDepth": 10  // 调用深度限制，-1 表示无限制
    },
    "statistics": {
      "totalPackages": 12,
      "totalClasses": 156,
      "totalMethods": 1243,
      "totalCallEdges": 3456
    }
  }
}
```

---

#### 2. `packages` - 包信息

```jsonc
{
  "packages": [
    {
      "id": "com.example.smarthome.linkage.scene",
      "name": "scene",
      "fullName": "com.example.smarthome.linkage.scene",
      "parentPackage": "com.example.smarthome.linkage",
      "level": 4,  // 包层级深度
      "classCount": 8,
      "metrics": {
        "fanIn": 25,    // 被其他包的类调用次数
        "fanOut": 18,   // 调用其他包的类次数
        "instability": 0.42  // 不稳定性 = fanOut / (fanIn + fanOut)
      }
    }
  ]
}
```

---

#### 3. `classes` - 类信息

```jsonc
{
  "classes": [
    {
      "id": "com.example.smarthome.linkage.scene.SceneManager",
      "name": "SceneManager",
      "qualifiedName": "com.example.smarthome.linkage.scene.SceneManager",
      "packageId": "com.example.smarthome.linkage.scene",
      "type": "CLASS",  // CLASS | INTERFACE | ABSTRACT_CLASS | ENUM | RECORD
      "modifiers": ["public"],
      "isTest": false,
      "sourceFile": "src/main/java/com/example/smarthome/linkage/scene/SceneManager.java",
      "annotations": [
        "@Service",
        "@Slf4j"
      ],
      "superClass": "com.example.smarthome.linkage.base.BaseManager",
      "interfaces": [
        "com.example.smarthome.linkage.scene.ISceneService"
      ],
      "metrics": {
        "methodCount": 12,
        "fieldCount": 5,
        "linesOfCode": 456,
        "fanIn": 15,     // 被多少其他类调用
        "fanOut": 8,     // 调用了多少其他类
        "coupling": 23,  // 耦合度 = fanIn + fanOut
        "cohesion": 0.75 // 内聚度（LCOM 等指标）
      }
    }
  ]
}
```

---

#### 4. `methods` - 方法信息

这是最核心的部分，需要设计得足够详细：

```jsonc
{
  "methods": [
    {
      "id": "com.example.smarthome.linkage.scene.SceneManager#executeScene(java.lang.String,java.util.Map)",
      "name": "executeScene",
      "className": "SceneManager",
      "classId": "com.example.smarthome.linkage.scene.SceneManager",
      "packageId": "com.example.smarthome.linkage.scene",
      "signature": "executeScene(String, Map<String, Object>)",
      "qualifiedSignature": "com.example.smarthome.linkage.scene.SceneManager#executeScene(java.lang.String,java.util.Map)",
      "modifiers": ["public"],
      "isStatic": false,
      "isConstructor": false,
      "isAbstract": false,
      "annotations": [
        "@Override",
        "@Transactional"
      ],
      
      // 参数信息
      "parameters": [
        {
          "name": "sceneId",
          "type": "java.lang.String",
          "annotations": ["@NotNull"]
        },
        {
          "name": "context",
          "type": "java.util.Map<java.lang.String,java.lang.Object>",
          "annotations": []
        }
      ],
      
      // 返回类型
      "returnType": "com.example.smarthome.linkage.scene.SceneResult",
      
      // 异常声明
      "throwsExceptions": [
        "com.example.smarthome.linkage.exception.SceneExecutionException"
      ],

      // 代码指标
      "metrics": {
        "linesOfCode": 156,
        "cyclomaticComplexity": 23, // 圈复杂度
        "cognitiveComplexity": 34, // 认知复杂度
        "nestingDepth": 6,
        "fanIn": 5,    // 被多少方法调用
        "fanOut": 8,   // 调用了多少其他方法
        "parameterCount": 7,
        "maxCallDepth": 6,  // 最大调用深度
        "localVariableCount": 15,
        "magicNumberCount": 8,
        "longLineCount": 5,
        "returnStatementCount": 7,
        "booleanParameterCount": 3
      },
      
      // 代码坏味道标记
      "codeSmells": [
        {
          "type": "LONG_AND_COMPLEX_METHOD",
          "severity": "HIGH",
          "description": "方法既长（156 行）又复杂（圈复杂度 23）",
          "suggestion": "拆分成多个小方法"
        },
        {
          "type": "DEEP_NESTING",
          "severity": "HIGH",
          "description": "嵌套深度达到 6 层",
          "suggestion": "使用提前返回（early return）或提取方法"
        },
        {
          "type": "TOO_MANY_PARAMETERS",
          "severity": "MEDIUM",
          "description": "参数过多（7 个）",
          "suggestion": "使用参数对象封装"
        },
        {
          "type": "TOO_MANY_MAGIC_NUMBERS",
          "severity": "MEDIUM",
          "description": "魔法数字过多（8 个）",
          "suggestion": "提取为常量"
        },
        {
          "type": "MULTIPLE_RETURNS",
          "severity": "LOW",
          "description": "多个 return 语句（7 个）",
          "suggestion": "考虑合并为单一出口"
        }
      ],
      
      // 综合评分（0-100，越低越健康）
      "complexityScore": 87,  // 高于 70 建议重构
      "refactoringPriority": {
        "level": "P0",  // P0 | P1 | P2 | P3
        "reason": "高复杂度（87分）+ 高影响范围（被23处调用）",
        "riskLevel": "HIGH"  // 重构风险评估
      }
      
      // 位置信息
      "location": {
        "file": "src/main/java/com/example/smarthome/linkage/scene/SceneManager.java",
        "startLine": 123,
        "endLine": 167
      },
      
      // 使用的 POJO（参数、返回值、局部变量、字段访问）
      "usedTypes": [
        "java.lang.String",
        "java.util.Map",
        "com.example.smarthome.linkage.scene.SceneResult",
        "com.example.smarthome.linkage.device.Device",
        "com.example.smarthome.linkage.rule.Rule"
      ],
      
      // 场景标记（可选，后期手动标注或通过 Controller/Scheduled/KafkaListener 等注解识别）
      "tags": {
        "isEntryPoint": true,    // 是否是业务入口
        "isPublicApi": true,     // 是否是对外 API
        "isDeprecated": false,
        "sceneNames": ["Controller", "Scheduled", "KafkaListener"]  // 所属业务场景
      }
    }
  ]
}
```

---

#### 5. `fields` - 字段信息

```jsonc
{
  "fields": [
    {
      "id": "com.example.smarthome.linkage.scene.SceneManager#deviceManager",
      "name": "deviceManager",
      "classId": "com.example.smarthome.linkage.scene.SceneManager",
      "type": "com.example.smarthome.linkage.device.DeviceManager",
      "modifiers": ["private"],
      "isStatic": false,
      "isFinal": false,
      "annotations": ["@Autowired"],
      "initializer": null  // 初始值表达式（如果有）
    }
  ]
}
```

---

#### 6. `pojos` - POJO 使用情况分析

专门记录数据对象的使用情况：

```jsonc
{
  "pojos": [
    {
      "id": "com.example.smarthome.linkage.dto.SceneDTO",
      "name": "SceneDTO",
      "qualifiedName": "com.example.smarthome.linkage.dto.SceneDTO",
      "packageId": "com.example.smarthome.linkage.dto",
      "category": "DTO",  // DTO | ENTITY | VO | DO | DOMAIN | CONFIG
      
      // 使用统计
      "usage": {
        "usedByMethodsCount": 23,
        "usedByClassesCount": 8,
        "usedByPackagesCount": 3,
        "usageTypes": {
          "asParameter": 12,      // 作为方法参数
          "asReturnType": 8,      // 作为返回值
          "asFieldType": 3,       // 作为字段类型
          "asLocalVariable": 15   // 作为局部变量
        }
      },
      
      // 字段列表
      "fields": [
        {
          "name": "sceneId",
          "type": "java.lang.String"
        },
        {
          "name": "deviceIds",
          "type": "java.util.List<java.lang.String>"
        }
      ],
      
      // 跨边界使用分析（用于识别"被滥用的 POJO"）
      "crossBoundaryUsage": [
        {
          "fromPackage": "com.example.smarthome.linkage.scene",
          "toPackage": "com.example.smarthome.linkage.device",
          "usageCount": 5,
          "isExpected": false  // 是否符合预期的依赖方向
        }
      ]
    }
  ]
}
```

---

#### 7. `callGraph` - 调用关系图

这是最关键的部分：

```jsonc
{
  "callGraph": {
    "edges": [
      {
        "id": "edge-1",
        "source": "com.example.smarthome.linkage.scene.SceneManager#executeScene(java.lang.String,java.util.Map)",
        "target": "com.example.smarthome.linkage.device.DeviceManager#controlDevice(java.lang.String,java.lang.String)",
        "type": "METHOD_CALL",  // METHOD_CALL | CONSTRUCTOR_CALL | SUPER_CALL
        
        // 调用上下文
        "callCount": 3,  // 在源方法中调用目标方法的次数
        "callLocations": [
          {
            "line": 145,
            "column": 12,
            "context": "conditional"  // conditional | loop | try-catch | normal
          }
        ],
        
        // 调用层级（从入口方法开始计算）
        "depth": 2,  // 调用深度
        
        // 边的权重（用于布局算法）
        "weight": 3
      }
    ]
  }
}
```

---

#### 8. `sceneDefinitions` - 场景定义

可以后期手动标注，也可以通过注解（如 `@RequestMapping`、`@Scheduled`）自动识别：

```jsonc
{
  "sceneDefinitions": [
    {
      "id": "scene-1",
      "name": "用户手动触发场景",
      "description": "用户在 App 中点击触发某个场景",
      "entryMethods": [
        "com.example.smarthome.linkage.controller.SceneController#triggerScene(java.lang.String)"
      ],
      "category": "USER_TRIGGER",  // USER_TRIGGER | SCHEDULED | EVENT_DRIVEN | API
      "tags": ["核心场景", "高频"],
      
      // 场景覆盖范围（自动计算）
      "coverage": {
        "methodCount": 89,
        "classCount": 23,
        "packageCount": 5,
        "maxDepth": 7
      }
    }
  ]
}
```

---

## PSI 数据采集清单

基于上述 Schema，你在 PSI 分析时需要采集以下数据：

### 对于每个 `PsiClass`：
- ✅ 类名、全限定名、包名
- ✅ 类型（class/interface/enum/abstract）
- ✅ 修饰符（public/private/protected/static/final）
- ✅ 父类、实现的接口
- ✅ 注解列表
- ✅ 方法数、字段数
- ✅ 代码行数（`PsiClass.getTextLength()` / 通过 Document 计算）

### 对于每个 `PsiMethod`：
- ✅ 方法名、签名
- ✅ 所属类、包
- ✅ 修饰符、是否静态/抽象/构造方法
- ✅ 参数列表（名称、类型、注解）
- ✅ 返回类型
- ✅ 抛出的异常
- ✅ 注解列表
- ✅ 代码行数、圈复杂度（可用 IntelliJ 的 `MetricsService` 或自己实现）
- ✅ 方法内调用的其他方法（通过 `PsiMethodCallExpression` 遍历）
- ✅ 使用的类型（参数、返回值、局部变量、字段访问）

### 对于每个 `PsiField`：
- ✅ 字段名、类型
- ✅ 所属类
- ✅ 修饰符、注解
- ✅ 初始值表达式

### 调用关系采集：
- ✅ 遍历每个方法的方法体（`PsiMethod.getBody()`）
- ✅ 找到所有 `PsiMethodCallExpression`
- ✅ 解析调用目标（`PsiMethodCallExpression.resolveMethod()`）
- ✅ 记录调用位置（行号、列号）
- ✅ 统计调用次数

---

## 指标体系计算方法

### 核心指标（PSI 可直接获取或计算）

#### 1. **圈复杂度（Cyclomatic Complexity）** ⭐⭐⭐⭐⭐

**定义：**  
代码中独立路径的数量 = 1 + 分支点数量（if/for/while/case/catch/&&/||）

**阈值建议：**
- ≤ 5：简单，容易理解
- 6-10：中等复杂
- 11-20：复杂，需要关注
- \> 20：非常复杂，**强烈建议重构**

**PSI 实现：**
```java
public int calculateCyclomaticComplexity(PsiMethod method) {
    int complexity = 1; // 基础路径
    PsiCodeBlock body = method.getBody();
    if (body == null) return 1;
    
    body.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            complexity++;
            super.visitIfStatement(statement);
        }
        
        @Override
        public void visitForStatement(PsiForStatement statement) {
            complexity++;
            super.visitForStatement(statement);
        }
        
        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            complexity++;
            super.visitWhileStatement(statement);
        }
        
        @Override
        public void visitSwitchStatement(PsiSwitchStatement statement) {
            PsiCodeBlock block = statement.getBody();
            if (block != null) {
                // 每个 case 增加 1
                complexity += countCaseLabels(block);
            }
            super.visitSwitchStatement(statement);
        }
        
        @Override
        public void visitConditionalExpression(PsiConditionalExpression expr) {
            complexity++; // 三元运算符 ? :
            super.visitConditionalExpression(expr);
        }
        
        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expr) {
            // && 或 || 每个都增加复杂度
            if (expr.getOperationTokenType() == JavaTokenType.ANDAND ||
                expr.getOperationTokenType() == JavaTokenType.OROR) {
                complexity += expr.getOperands().length - 1;
            }
            super.visitPolyadicExpression(expr);
        }
        
        @Override
        public void visitCatchSection(PsiCatchSection section) {
            complexity++; // 每个 catch 块
            super.visitCatchSection(section);
        }
    });
    
    return complexity;
}
```

---

#### 2. **认知复杂度（Cognitive Complexity）** ⭐⭐⭐⭐⭐

**定义：**  
比圈复杂度更准确地衡量"人类理解代码的难度"，由 SonarSource 提出。

**核心思想：**
- 嵌套结构增加认知负担（每层嵌套 +1）
- 顺序结构不增加负担
- 打断线性流程的结构（break/continue/递归）增加负担

**示例对比：**
```java
// 圈复杂度 = 4，认知复杂度 = 1
if (a) return;
if (b) return;
if (c) return;
if (d) return;

// 圈复杂度 = 4，认知复杂度 = 7（嵌套层级深）
if (a) {          // +1
    if (b) {      // +2 (嵌套层级 1)
        if (c) {  // +3 (嵌套层级 2)
            if (d) { // +4 (嵌套层级 3)
                // ...
            }
        }
    }
}
```

**阈值建议：**
- ≤ 10：简单
- 11-15：中等
- 16-25：复杂
- \> 25：**非常难理解，必须重构**

**PSI 实现（简化版）：**
```java
public int calculateCognitiveComplexity(PsiMethod method) {
    AtomicInteger complexity = new AtomicInteger(0);
    AtomicInteger nestingLevel = new AtomicInteger(0);
    
    method.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            complexity.addAndGet(1 + nestingLevel.get());
            nestingLevel.incrementAndGet();
            super.visitIfStatement(statement);
            nestingLevel.decrementAndGet();
        }
        
        @Override
        public void visitForStatement(PsiForStatement statement) {
            complexity.addAndGet(1 + nestingLevel.get());
            nestingLevel.incrementAndGet();
            super.visitForStatement(statement);
            nestingLevel.decrementAndGet();
        }
        
        // 类似处理 while/switch/try-catch...
        
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expr) {
            // 递归调用 +1
            PsiMethod resolved = expr.resolveMethod();
            if (resolved != null && resolved.equals(method)) {
                complexity.incrementAndGet();
            }
            super.visitMethodCallExpression(expr);
        }
        
        @Override
        public void visitBreakStatement(PsiBreakStatement statement) {
            complexity.incrementAndGet(); // break 打断流程
            super.visitBreakStatement(statement);
        }
        
        @Override
        public void visitContinueStatement(PsiContinueStatement statement) {
            complexity.incrementAndGet(); // continue 打断流程
            super.visitContinueStatement(statement);
        }
    });
    
    return complexity.get();
}
```

---

#### 3. **代码行数（LOC）** ⭐⭐⭐⭐

**分类：**
- **物理行数**：包括空行、注释
- **逻辑行数**：只计算有效代码行（更准确）

**阈值建议：**
- ≤ 20 行：简单
- 21-50 行：中等
- 51-100 行：较长，建议拆分
- \> 100 行：**过长，必须拆分**

**PSI 实现：**
```java
public int getLogicalLinesOfCode(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return 0;
    
    Document doc = PsiDocumentManager.getInstance(method.getProject())
                                     .getDocument(method.getContainingFile());
    if (doc == null) return 0;
    
    int startLine = doc.getLineNumber(body.getTextRange().getStartOffset());
    int endLine = doc.getLineNumber(body.getTextRange().getEndOffset());
    
    int logicalLines = 0;
    for (int i = startLine; i <= endLine; i++) {
        int lineStart = doc.getLineStartOffset(i);
        int lineEnd = doc.getLineEndOffset(i);
        String line = doc.getText(new TextRange(lineStart, lineEnd)).trim();
        
        // 排除空行、纯注释行、只有括号的行
        if (!line.isEmpty() && 
            !line.startsWith("//") && 
            !line.startsWith("/*") &&
            !line.equals("{") && 
            !line.equals("}")) {
            logicalLines++;
        }
    }
    
    return logicalLines;
}
```

---

#### 4. **嵌套深度（Nesting Depth）** ⭐⭐⭐⭐

**定义：**  
代码块的最大嵌套层级。

**阈值建议：**
- ≤ 3：正常
- 4-5：开始复杂
- \> 5：**严重嵌套，难以理解**

**PSI 实现：**
```java
public int getMaxNestingDepth(PsiMethod method) {
    AtomicInteger maxDepth = new AtomicInteger(0);
    AtomicInteger currentDepth = new AtomicInteger(0);
    
    method.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            currentDepth.incrementAndGet();
            maxDepth.set(Math.max(maxDepth.get(), currentDepth.get()));
            super.visitIfStatement(statement);
            currentDepth.decrementAndGet();
        }
        
        @Override
        public void visitForStatement(PsiForStatement statement) {
            currentDepth.incrementAndGet();
            maxDepth.set(Math.max(maxDepth.get(), currentDepth.get()));
            super.visitForStatement(statement);
            currentDepth.decrementAndGet();
        }
        
        // 类似处理 while/try-catch/lambda...
    });
    
    return maxDepth.get();
}
```

---

#### 5. **参数个数（Parameter Count）** ⭐⭐⭐

**定义：**  
方法参数的数量。

**阈值建议：**
- ≤ 3：正常
- 4-5：稍多
- \> 5：**过多，建议用对象封装**

**PSI 实现：**
```java
public int getParameterCount(PsiMethod method) {
    return method.getParameterList().getParametersCount();
}
```

---

#### 6. **局部变量数量** ⭐⭐⭐

**定义：**  
方法体内声明的局部变量数量（不含参数）。

**阈值建议：**
- ≤ 5：正常
- 6-10：稍多
- \> 10：**过多，可能职责不清**

**PSI 实现：**
```java
public int getLocalVariableCount(PsiMethod method) {
    AtomicInteger count = new AtomicInteger(0);
    
    method.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
            count.addAndGet(statement.getDeclaredElements().length);
            super.visitDeclarationStatement(statement);
        }
    });
    
    return count.get();
}
```

---

## 三、针对"代码写法让人看错"的指标

这类问题更隐蔽，通常是"代码坏味道"。

### 1. **"长方法 + 高复杂度"组合** ⭐⭐⭐⭐⭐

**识别规则：**
```
LOC > 50 && CyclomaticComplexity > 10
```

这种方法**既长又复杂**，是最容易出错、最难维护的。

---

### 2. **深度嵌套 + 多分支** ⭐⭐⭐⭐

**识别规则：**
```
NestingDepth > 4 && CyclomaticComplexity > 15
```

**典型"坏味道"：**
```java
if (a) {
    if (b) {
        if (c) {
            if (d) {
                if (e) {
                    // 你已经迷失在第 5 层嵌套了
                }
            }
        }
    }
}
```

**容易导致：**
- 看不清哪个 `}` 对应哪个 `{`
- 忘记某个条件分支的前提条件
- 修改时容易改错层级

---

### 3. **"神奇数字/字符串"密度** ⭐⭐⭐

**定义：**  
方法中硬编码的数字/字符串字面量数量。

**PSI 实现：**
```java
public int getMagicNumberCount(PsiMethod method) {
    AtomicInteger count = new AtomicInteger(0);
    
    method.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitLiteralExpression(PsiLiteralExpression expr) {
            Object value = expr.getValue();
            if (value instanceof Number) {
                // 排除常见的 0, 1, -1
                int num = ((Number) value).intValue();
                if (num != 0 && num != 1 && num != -1) {
                    count.incrementAndGet();
                }
            } else if (value instanceof String) {
                String str = (String) value;
                // 排除空字符串
                if (!str.isEmpty()) {
                    count.incrementAndGet();
                }
            }
            super.visitLiteralExpression(expr);
        }
    });
    
    return count.get();
}
```

**阈值建议：**
- \> 5 个魔法数字/字符串 → 建议提取为常量

---

### 4. **"长表达式"（单行代码过长）** ⭐⭐⭐

**定义：**  
单行代码字符数 > 120。

**PSI 实现：**
```java
public List<Integer> getLongLines(PsiMethod method) {
    List<Integer> longLines = new ArrayList<>();
    Document doc = PsiDocumentManager.getInstance(method.getProject())
                                     .getDocument(method.getContainingFile());
    if (doc == null) return longLines;
    
    PsiCodeBlock body = method.getBody();
    if (body == null) return longLines;
    
    int startLine = doc.getLineNumber(body.getTextRange().getStartOffset());
    int endLine = doc.getLineNumber(body.getTextRange().getEndOffset());
    
    for (int i = startLine; i <= endLine; i++) {
        int lineStart = doc.getLineStartOffset(i);
        int lineEnd = doc.getLineEndOffset(i);
        if (lineEnd - lineStart > 120) {
            longLines.add(i);
        }
    }
    
    return longLines;
}
```

**典型"坏味道"：**
```java
result = calculateSomething(param1, param2, getContext().getConfig().getProperty("key"), Utils.convert(data.getField1(), data.getField2()), new Options().setFlag(true).setTimeout(3000));
```

这种单行链式调用**极易看错**。

---

### 5. **"相似代码块"（重复代码）** ⭐⭐⭐⭐

**定义：**  
方法内有多段高度相似的代码（复制粘贴后微调）。

**PSI 实现（简化）：**
```java
public int getDuplicatedBlockCount(PsiMethod method) {
    // 提取方法内所有代码块的 AST 结构
    // 计算它们的相似度（如编辑距离）
    // 这个比较复杂，通常需要专门的算法
    // IntelliJ 内置的 DuplicatesFinder 可以用
    
    // 简化版：统计"长度 > 5 行的重复 if/for 块"
    // 实现略复杂，这里仅给出思路
}
```

**阈值建议：**
- \> 3 处重复 → 建议提取方法

---

### 6. **"布尔参数过多"** ⭐⭐⭐

**识别规则：**
```
参数类型为 boolean 的数量 > 2
```

**典型"坏味道"：**
```java
public void process(boolean flag1, boolean flag2, boolean flag3) {
    // 调用时：process(true, false, true)
    // 看不懂每个 true/false 是什么意思
}
```

**PSI 实现：**
```java
public int getBooleanParameterCount(PsiMethod method) {
    int count = 0;
    for (PsiParameter param : method.getParameterList().getParameters()) {
        if (param.getType().equals(PsiType.BOOLEAN)) {
            count++;
        }
    }
    return count;
}
```

---

### 7. **"多个 return 语句"** ⭐⭐

**定义：**  
方法中 `return` 语句的数量。

**阈值建议：**
- 1 个：最清晰（单一出口）
- 2-3 个：可接受（提前返回简化逻辑）
- \> 5 个：**过多，容易漏掉某个分支**

**PSI 实现：**
```java
public int getReturnStatementCount(PsiMethod method) {
    AtomicInteger count = new AtomicInteger(0);
    
    method.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
            count.incrementAndGet();
            super.visitReturnStatement(statement);
        }
    });
    
    return count.get();
}
```

---

## 四、综合判断："问题方法"识别规则

在 JSON Schema 中，可以为每个方法加一个 `codeSmells` 字段：

```jsonc
{
  "methods": [
    {
      "id": "...",
      "metrics": {
        "linesOfCode": 156,
        "cyclomaticComplexity": 23, // 圈复杂度
        "cognitiveComplexity": 34, // 认知复杂度
        "nestingDepth": 6,
        "fanIn": 5,    // 被多少方法调用
        "fanOut": 8,   // 调用了多少其他方法
        "parameterCount": 7,
        "maxCallDepth": 6,  // 最大调用深度
        "localVariableCount": 15,
        "magicNumberCount": 8,
        "longLineCount": 5,
        "returnStatementCount": 7,
        "booleanParameterCount": 3
      },
      
      // 代码坏味道标记
      "codeSmells": [
        {
          "type": "LONG_AND_COMPLEX_METHOD",
          "severity": "HIGH",
          "description": "方法既长（156 行）又复杂（圈复杂度 23）",
          "suggestion": "拆分成多个小方法"
        },
        {
          "type": "DEEP_NESTING",
          "severity": "HIGH",
          "description": "嵌套深度达到 6 层",
          "suggestion": "使用提前返回（early return）或提取方法"
        },
        {
          "type": "TOO_MANY_PARAMETERS",
          "severity": "MEDIUM",
          "description": "参数过多（7 个）",
          "suggestion": "使用参数对象封装"
        },
        {
          "type": "TOO_MANY_MAGIC_NUMBERS",
          "severity": "MEDIUM",
          "description": "魔法数字过多（8 个）",
          "suggestion": "提取为常量"
        },
        {
          "type": "MULTIPLE_RETURNS",
          "severity": "LOW",
          "description": "多个 return 语句（7 个）",
          "suggestion": "考虑合并为单一出口"
        }
      ],
      
      // 综合评分（0-100，越低越健康）
      "complexityScore": 87  // 高于 70 建议重构
    }
  ]
}
```

---

## 五、在 G6 可视化中如何体现

### 1. **节点颜色/大小映射指标**

```javascript
const data = {
  nodes: graphData.nodes.map(n => {
    const score = n.complexityScore || 0;
    
    // 根据复杂度评分映射颜色
    let color = '#5ad8a6'; // 绿色 = 健康
    if (score > 70) color = '#f4664a'; // 红色 = 严重
    else if (score > 50) color = '#faad14'; // 橙色 = 警告
    else if (score > 30) color = '#5b8ff9'; // 蓝色 = 一般
    
    // 根据代码行数映射节点大小
    const size = 20 + Math.min(n.metrics.linesOfCode / 5, 60);
    
    return {
      id: n.id,
      label: n.name,
      size: size,
      style: { fill: color },
      metrics: n.metrics,
      codeSmells: n.codeSmells
    };
  })
};
```

**效果：**
- 红色大节点 = "又长又复杂的问题方法"
- 橙色节点 = "需要关注的方法"
- 绿色小节点 = "健康的方法"

---

### 2. **点击节点显示详细"坏味道"列表**

```javascript
graph.on('node:click', evt => {
  const model = evt.item.getModel();
  const smells = model.codeSmells || [];
  
  let html = `<div class="label-title">方法：${model.label}</div>`;
  html += `<div>代码行数：${model.metrics.linesOfCode}</div>`;
  html += `<div>圈复杂度：${model.metrics.cyclomaticComplexity}</div>`;
  html += `<div>认知复杂度：${model.metrics.cognitiveComplexity}</div>`;
  html += `<div>嵌套深度：${model.metrics.nestingDepth}</div>`;
  
  if (smells.length > 0) {
    html += `<div style="margin-top:8px;"><strong>检测到的问题：</strong></div>`;
    smells.forEach(smell => {
      const severityColor = {
        HIGH: '#f4664a',
        MEDIUM: '#faad14',
        LOW: '#5b8ff9'
      }[smell.severity];
      
      html += `
        <div style="margin:4px 0;padding:4px;background:#f7f7f7;border-left:3px solid ${severityColor};">
          <div style="font-weight:bold;">${smell.type}</div>
          <div style="font-size:11px;">${smell.description}</div>
          <div style="font-size:11px;color:#666;">建议：${smell.suggestion}</div>
        </div>
      `;
    });
  }
  
  document.getElementById('infoContent').innerHTML = html;
});
```

---

### 3. **提供"问题方法排行榜"视图**

生成一个额外的 HTML 页面：`problem-methods-ranking.html`

```javascript
// 按复杂度评分排序，取 Top 20
const problemMethods = graphData.nodes
  .filter(n => n.complexityScore > 50)
  .sort((a, b) => b.complexityScore - a.complexityScore)
  .slice(0, 20);

// 渲染成表格
const table = `
<table>
  <thead>
    <tr>
      <th>排名</th>
      <th>方法</th>
      <th>类</th>
      <th>代码行数</th>
      <th>圈复杂度</th>
      <th>认知复杂度</th>
      <th>问题数</th>
      <th>评分</th>
    </tr>
  </thead>
  <tbody>
    ${problemMethods.map((m, i) => `
      <tr>
        <td>${i + 1}</td>
        <td>${m.name}</td>
        <td>${m.className}</td>
        <td>${m.metrics.linesOfCode}</td>
        <td>${m.metrics.cyclomaticComplexity}</td>
        <td>${m.metrics.cognitiveComplexity}</td>
        <td>${m.codeSmells.length}</td>
        <td style="color:${m.complexityScore > 70 ? '#f4664a' : '#faad14'}">
          ${m.complexityScore}
        </td>
      </tr>
    `).join('')}
  </tbody>
</table>
`;
```

---

### 4. 方法重构优先级矩阵
```
优先级 = f(问题严重度, 影响范围)

         │ 影响范围大（fan-in 高）  │ 影响范围小
─────────┼─────────────────────────┼──────────────
问题严重  │  P0（立即修复）          │  P1（重点关注）
（复杂度高）│  例：核心服务的上帝类    │  例：复杂但孤立的工具方法
─────────┼─────────────────────────┼──────────────
问题轻微  │  P2（逐步优化）          │  P3（暂缓）
（复杂度中等）│  例：被广泛调用的普通方法 │  例：边缘功能的小方法
```


## 基于全量数据，生成多种"聚合视图"

#### 视图 1：包级依赖图（高层视图）

- 节点：包（package）
- 边：包 A 的方法调用包 B 的方法 → A 依赖 B
- 边的粗细/数字：调用次数

这个图节点少（几十个包），能看清**包级别的依赖乱象**：

- 哪些包被大量依赖（核心包）；
- 哪些包之间有双向依赖（循环依赖，边界不清）；
- 哪些包孤立（可能是独立子模块）。

**输出：** `package-level-deps.html`

---

#### 视图 2：类级依赖图（中层视图）

- 节点：类（Class）
- 边：类 A 的方法调用类 B 的方法
- 可以按包分组/着色

这个图节点稍多（几百个类），但仍然可控。能看到：

- "上帝类"（fan-in / fan-out 都高）；
- 类之间的强耦合簇。

**输出：** `class-level-deps.html`

---

#### 视图 3：核心方法热力图（方法级，但过滤）

- 节点：方法
- **但只展示 fan-in > 阈值（如 5）的方法**（被多个地方调用的"共享方法"）
- 边：谁调用了它们

这个图能直接告诉你：

- 哪些方法是"事实上的公共服务"（被多个业务场景复用）；
- 这些方法通常就是：
    - 候选子模块的对外接口；
    - 或"应该抽象成模块接口但现在没有"的方法。

**输出：** `high-fanin-methods.html`

---

#### 视图 4：场景交叉分析矩阵（回答你的核心问题）

这是专门用来回答：
> **"多个场景的方法链，哪里重复度最高？"**

做法：

1. 你手动/半自动标记 N 个场景的入口方法（如 3～5 个核心场景）。
2. 对每个入口，用 DFS/BFS 收集它的"调用树"（深度可控，如 5 层内）。
3. 统计：
    - 每个方法在多少个场景中出现；
    - 每个类在多少个场景中出现；
    - 每个包在多少个场景中出现。

输出一个**表格 + 热力图**：

| 方法/类/包 | 场景A | 场景B | 场景C | 场景D | 出现次数 | 重叠度 |
|-----------|------|------|------|------|---------|--------|
| DeviceManager#execute() | ✓ | ✓ | ✓ | ✓ | 4 | 100% |
| SceneRuleEngine#match() | ✓ | ✓ | | ✓ | 3 | 75% |
| UserConfigService#load() | ✓ | | | | 1 | 25% |

**输出：**
- `scene-overlap-matrix.html`（表格 + 热力图）
- 可以用 G6 画一个**场景重叠网络图**：
    - 节点：场景入口方法
    - 边：共享了哪些方法/类（边的粗细 = 共享数量）

这样你能一眼看到：

- 哪些方法/类是**跨场景的核心依赖**（候选"公共子模块"）；
- 哪些场景的调用链高度重叠（说明它们属于同一业务域）；
- 哪些场景几乎不重叠（说明是独立的子域）。


## 插件输出结构
```
插件运行后输出到一个文件夹，比如：项目根目录/neko-analysis/nekoama-deps-20251115-1
├── data/
│   ├── full-call-graph.json          # 全量方法调用关系
│   ├── method-metrics.json           # 所有方法的指标
│   └── scene-definitions.json        # 场景定义（可手动编辑）
├── views/
│   ├── 1-package-deps.html           # 包级依赖图
│   ├── 2-class-deps.html             # 类级依赖图
│   ├── 3-high-fanin-methods.html     # 高 fan-in 方法图
│   ├── 4-scene-overlap.html          # 场景交叉分析
│   └── 5-scene-A-detail.html         # 单个场景详细调用图（可选）
└── index.html                         # 导航页，链接到所有视图
```

用户打开 `index.html`，像一个"架构分析仪表盘"，可以：

- 先看包级依赖 → 了解顶层结构；
- 再看场景重叠矩阵 → 找到共享的核心方法/类；
- 最后看高 fan-in 方法图 → 确认"事实上的模块接口"。


# 功能/方案价值

## 一、对四大背景问题的解决能力评估

### 1. **"代码分包不清晰，依赖关系混乱"** → 解决度：**95%** ✅

**能做到的：**
- ✅ 包级依赖图直接暴露：哪些包之间有双向依赖、循环依赖
- ✅ 用数据说话：包 A 调用包 B 多少次，依赖强度可量化
- ✅ 场景交叉分析能告诉你：哪些包是"事实上的公共基础包"，哪些包职责混杂
- ✅ 可视化后，能直观看到"理想的分层结构"和"实际的意大利面条"的差距

**局限性：**
- ❌ 它只能告诉你"现状是什么样"，不能直接告诉你"应该怎么拆"
- ❌ 业务语义的理解还是需要人工（比如"设备层"和"场景层"的概念，工具不懂）

**但这已经是最大的帮助了**：你从"模糊的感觉"变成"有证据的诊断报告"。

---

### 2. **"类职责不明确，功能过于复杂"** → 解决度：**85%** ✅

**能做到的：**
- ✅ 类级依赖图 + fan-in/fan-out 指标，直接暴露"上帝类"
- ✅ 方法数、代码行数、圈复杂度等指标，能量化"过于复杂"
- ✅ 看到一个类被 N 个不同业务场景调用 → 说明它承担了多重职责
- ✅ 通过"类在多个场景中的出现频率"，判断它是"公共服务类"还是"职责不清的大杂烩"

**局限性：**
- ⚠️ 它能告诉你"这个类很复杂、职责很多"，但不能自动告诉你"应该拆成哪几个类"
- ⚠️ 需要你结合业务知识，解读数据（比如一个类 fan-in 很高，可能是合理的工具类，也可能是职责混乱）

**指导意义：**
- 你可以**优先重构 Top 10 最复杂的类**，用数据驱动重构优先级
- 重构前后对比指标变化（如 fan-out 从 20 降到 5），证明重构有效

---

### 3. **"方法调用链过长"** → 解决度：**90%** ✅

**能做到的：**
- ✅ 方法级调用图 + 深度标注（layer），直接可视化"调用链有多长"
- ✅ 统计最大调用深度、平均调用深度
- ✅ 看到某个业务场景需要经过 10 层方法才完成 → 重构目标明确
- ✅ 识别"纯中转方法"（fan-in=1, fan-out=1，只是简单调用另一个方法）

**局限性：**
- ⚠️ 长调用链不一定都是坏事（有些是合理的分层抽象）
- ⚠️ 工具不能自动判断"哪些层是多余的"，需要人工分析

**指导意义：**
- 你可以针对"调用深度 > 7 层"的场景，逐个审查是否有优化空间
- 重构目标可以量化：把平均调用深度从 6 降到 4

---

### 4. **"POJO 使用不当"** → 解决度：**75%** ⚠️

**能做到的：**
- ✅ 统计每个 POJO 被多少方法/类/包使用
- ✅ 识别"跨边界使用"（某个 DTO 在不该出现的包里被使用）
- ✅ 看到某个 POJO 同时作为 Entity、DTO、VO 使用 → 职责混乱

**局限性：**
- ⚠️ PSI 能拿到的是"静态类型信息"，但拿不到：
    - POJO 的字段是否在方法里被修改（可变性分析）
    - POJO 是否包含业务逻辑（需要更深的语义分析）
- ⚠️ "使用不当"的判断标准，需要你事先定义（比如"DTO 不应该出现在 repository 层"）

**改进建议：**
- 在 Schema 中加入 `crossBoundaryUsage` 字段，标注"预期边界"
- 需要你手动配置规则（比如：`dto` 包的类不应该被 `dao` 包使用）

---

## 二、对"目的"的达成度评估

### 目的 1："厘清业务代码调用情况" → 解决度：**95%** ✅

**完全可以做到：**
- ✅ 全量方法调用关系 → 任何方法的"谁调用我、我调用谁"都清清楚楚
- ✅ 场景交叉分析 → 知道每个场景涉及哪些方法/类/包
- ✅ 高频方法识别 → 知道哪些方法是"核心公共服务"

这个目标基本 100% 达成，没有悬念。

---

### 目的 2："厘清业务子模块，拆分边界" → 解决度：**70-80%** ⚠️

**能提供的帮助：**
- ✅ 提供"数据驱动的候选边界"：
    - 场景重叠少的包 → 独立子模块候选
    - 高 fan-in 的类/包 → 公共基础模块候选
    - 循环依赖的包组 → 需要合并或重构的模块
- ✅ 提供"边界验证手段"：
    - 你定义了子模块边界后，可以用工具检查"是否有跨边界调用违规"

**仍需人工的部分：**
- ❌ **业务语义理解**：
    - 工具只能说"这 5 个类调用关系紧密"，但不能说"这是设备管理子模块"
    - "设备层"、"场景层"、"规则引擎"这些概念，需要你结合业务定义
- ❌ **边界决策**：
    - 有时两个包相互依赖，是合并它们，还是抽象出第三个包？工具给不了建议

**但已经是巨大帮助：**
- 以前：靠经验 + 猜测，边界定义模糊、容易有遗漏
- 现在：有客观数据支撑，边界决策有理有据，可以用数字说服团队

---

### 目的 3："准确评估新需求工作量" → 解决度：**80%** ✅

**能提供的帮助：**
- ✅ 快速找到"影响范围"：
    - 新需求涉及的入口方法 → 调用图 → 看到影响多少方法/类/包
- ✅ 量化指标支撑：
    - 方法数、类数、调用深度、耦合度 → 输入评估公式
- ✅ 历史数据校准：
    - 统计过去需求的"影响范围 vs 实际耗时"，拟合系数

**仍需人工的部分：**
- ⚠️ 新需求的"业务复杂度"工具无法评估：
    - 修改一个方法，但涉及复杂的业务逻辑重构 → 工具看不出来
    - 需要新增外部系统对接 → 工具无感知
- ⚠️ 团队能力差异、技术债、测试覆盖率等因素，需要人工调整系数

**但已经大幅提升准确性：**
- 以前：全凭经验，误差 ±50%
- 现在：基于数据 + 公式，误差可降到 ±20-30%

---

## 三、对代码重构的指导意义评估

### 1. **重构优先级排序** → 价值：**非常高** ⭐⭐⭐⭐⭐

你可以用数据驱动重构计划：

| 重构目标 | 识别指标 | 工具支持 |
|---------|---------|---------|
| 优先解决循环依赖 | 包级 DSM 红色块 | ✅ 直接可见 |
| 拆分上帝类 | fan-in > 20, fan-out > 15, 方法数 > 30 | ✅ 排序 Top 10 |
| 简化过长调用链 | 最大调用深度 > 7 的场景 | ✅ 场景分析 |
| 规范 POJO 使用 | 跨边界使用次数 > 10 | ✅ POJO 分析 |

**指导意义：**
- 不再是"感觉这里该重构"，而是"数据显示这 10 个类耦合度最高，优先重构"
- 重构后可以再跑一次分析，对比指标变化，证明重构效果

---

### 2. **重构范围控制** → 价值：**高** ⭐⭐⭐⭐

重构前用工具看一眼"影响范围"：
- 我想重构类 A，它被多少个地方调用？
- 如果我把方法 B 移到另一个类，会影响哪些调用方？

**指导意义：**
- 避免"牵一发动全身"的盲目重构
- 可以选择"影响范围小"的类先试点

---

### 3. **重构验证** → 价值：**高** ⭐⭐⭐⭐

重构前后对比：

| 指标 | 重构前 | 重构后 | 改善 |
|------|-------|-------|------|
| 包级循环依赖 | 3 处 | 0 处 | ✅ |
| 平均调用深度 | 6.2 层 | 4.5 层 | ✅ |
| 上帝类数量（fan-out>15） | 8 个 | 2 个 | ✅ |

**指导意义：**
- 重构不再是"主观感觉好了"，而是"指标改善了 X%"
- 可以写进重构报告，向领导证明价值


---

## 四、综合评估：这套方案的价值定位

### ✅ 它非常擅长的（90%+ 解决）：

1. **可视化现状** → 把"模糊的感觉"变成"客观的数据和图"
2. **识别问题点** → 上帝类、循环依赖、过长调用链、跨边界调用
3. **量化指标** → 为评估、重构提供数字依据
4. **影响范围分析** → 快速回答"改这里会影响哪里"

---

### ⚠️ 它需要人工辅助的（70-80% 解决）：

1. **业务语义理解** → 工具不懂"设备层"、"场景层"是什么
2. **边界决策** → 工具提供候选，但最终决策需要人
3. **重构方案设计** → 工具告诉你"这个类该拆"，但怎么拆需要人设计
4. **复杂度评估** → 业务复杂度、技术债等"软性因素"工具看不到

---

### ❌ 它做不到的（需要其他手段）：

1. **自动重构** → 它只分析，不改代码
2. **业务流程建模** → 它看到的是代码调用，不是业务流程
3. **性能分析** → 它不管方法跑得快不快，只管谁调用谁
4. **测试覆盖率** → 需要结合其他工具（如 JaCoCo）

---

## 五、我的建议：这套方案的价值定位

把它定位为：

> **"架构透视仪 + 重构导航仪"**

- **不是**"自动重构工具"（不替你做决策）
- **不是**"业务建模工具"（不替你理解业务）
- **是**"把隐藏的架构问题变成可见、可量化的事实"
- **是**"为你的决策提供数据支撑，把主观判断变成客观分析"

---

## 六、增强"团队协作"的补充（不只是你一个人用）

### **导出"可分享报告"** ⭐⭐⭐⭐

现在输出的是 HTML，但可以补充：

**格式 A：Markdown 格式**
```markdown
# 架构分析报告

生成时间：2025-11-16
模块：smart-home-linkage

## 一、整体概况
- 总方法数：1243
- 平均复杂度：6.8
- 问题方法数：23（占 1.85%）

## 二、Top 10 问题方法
| 排名 | 方法 | 复杂度 | 行数 | 优先级 |
|-----|------|-------|------|--------|
| 1   | SceneManager#executeScene | 34 | 156 | P0 |
| 2   | DeviceManager#syncState | 28 | 123 | P1 |
...

## 三、循环依赖清单
- com.example.scene ↔ com.example.device
- ...

## 四、重构建议
1. 优先解决 3 个循环依赖
2. 重构 Top 5 复杂方法
3. 规范 POJO 跨层使用
```

**用途：**
- 贴到 Confluence / 飞书文档
- 在评审会上展示
- 向领导汇报

---


