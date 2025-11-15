# Kotlin + IntelliJ IDEA 插件开发线程最佳实践指南

## 目录
1. [EDT (Event Dispatch Thread) 详细使用](#1-edt-event-dispatch-thread-详细使用)
2. [协程与EDT的协作机制](#2-协程与edt的协作机制)
3. [PSI线程的概念深入解析](#3-psi线程的概念深入解析)
4. [完整的最佳实践代码示例](#4-完整的最佳实践代码示例)
5. [性能优化技巧](#5-性能优化技巧)
6. [常见问题和解决方案](#6-常见问题和解决方案)

---

## 1. EDT (Event Dispatch Thread) 详细使用

### EDT的作用和工作原理

**EDT (Event Dispatch Thread)** 是IntelliJ IDEA的主UI线程，负责：
- 处理所有UI事件（鼠标、键盘等）
- 更新和渲染UI组件
- 管理组件的层次结构和布局

**核心原理**：
- EDT是单线程的，所有UI操作必须在EDT上执行
- 非EDT线程直接访问UI组件会抛出异常
- EDT阻塞会导致整个IDE界面冻结

### 正确的任务提交方式

```kotlin
// 1. ApplicationManager.invokeLater - 异步执行
ApplicationManager.getApplication().invokeLater(Runnable {
    // UI更新代码
    connectionStatusLabel.text = "连接成功"
})

// 2. ApplicationManager.invokeAndWait - 同步等待
ApplicationManager.getApplication().invokeAndWait(Runnable {
    // 必须在EDT上完成的同步操作
    someUiComponent.update()
})

// 3. 使用ModalityState
ApplicationManager.getApplication().invokeLater(Runnable {
    // 操作UI
}, ModalityState.defaultModalityState())
```

### ModalityState的作用和选择

**ModalityState**控制UI操作的模态上下文：

```kotlin
// 常见模态状态选择：
ModalityState.defaultModalityState()           // 默认状态，用于非模态窗口
ModalityState.currentModalityState()           // 当前模态状态
ModalityState.NON_MODAL                        // 非模态状态
ModalityState.any()                            // 任何状态都执行
```

### EDT相关的常见错误和解决方案

**错误1：非EDT线程访问UI**
```kotlin
// ❌ 错误：后台线程直接更新UI
fun updateInWrongThread() {
    thread {
        connectionStatusLabel.text = "更新" // 抛出异常
    }
}

// ✅ 正确：切换到EDT
fun updateCorrectly() {
    thread {
        // 后台计算
        val result = computeResult()
        // 切换到EDT更新UI
        ApplicationManager.getApplication().invokeLater {
            connectionStatusLabel.text = result
        }
    }
}
```

**错误2：EDT上执行耗时操作**
```kotlin
// ❌ 错误：EDT上执行网络请求
fun fetchWrong() {
    val response = httpClient.get(url) // 阻塞EDT
    updateUI(response)
}

// ✅ 正确：后台执行，EDT更新
fun fetchCorrect() {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "获取数据", true) {
        override fun run(indicator: ProgressIndicator) {
            try {
                val response = httpClient.get(url) // 后台线程
                ApplicationManager.getApplication().invokeLater {
                    updateUI(response) // EDT更新
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    showError(e.message) // EDT显示错误
                }
            }
        }
    })
}
```

**错误3："Slow operations are prohibited on EDT"**
```kotlin
// ❌ 错误：在EDT上执行文件I/O
fun saveDataInEDT() {
    Files.write(path, data) // 抛出SlowOperationsProhibitedException
}

// ✅ 正确：在后台线程执行I/O
fun saveDataCorrectly() {
    ApplicationManager.getApplication().executeOnPooledThread {
        try {
            Files.write(path, data)
            ApplicationManager.getApplication().invokeLater {
                showSuccessMessage()
            }
        } catch (e: Exception) {
            ApplicationManager.getApplication().invokeLater {
                showErrorMessage(e.message)
            }
        }
    }
}
```

---

## 2. 协程与EDT的协作机制

### Dispatchers.Main在IntelliJ插件中的角色

在IntelliJ插件开发中，`Dispatchers.Main` **不等于**EDT，需要特殊的配置：

```kotlin
// ✅ 正确：使用EDT调度器
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// ✅ 正确：确保协程在EDT上执行
scope.launch {
    // 这已经在EDT上
    updateUI()
}

// ✅ 正确：切换到后台线程执行
scope.launch {
    val result = withContext(Dispatchers.IO) {
        // 后台操作
        networkCall()
    }
    // 自动切回EDT
    updateUI(result)
}
```

### 协程安全与EDT交互的最佳实践

```kotlin
class CoroutineEDTExample {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun performAsyncOperation() {
        scope.launch {
            try {
                // 1. EDT上显示加载状态
                showLoading(true)

                // 2. 切换到后台线程执行耗时操作
                val result = withContext(Dispatchers.IO) {
                    heavyComputation()
                }

                // 3. 自动切回EDT更新UI
                showLoading(false)
                updateUI(result)

            } catch (e: Exception) {
                // 4. 异常处理也在EDT上
                showError(e.message)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        // 确保在EDT上执行
        if (ApplicationManager.getApplication().isDispatchThread) {
            loadingIndicator.isVisible = show
        }
    }
}
```

### 协程调度器切换的最佳实践

```kotlin
// 完整的协程使用模式
class AsyncService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun completeWorkflow() {
        scope.launch {
            // 阶段1：EDT - 显示进度
            updateProgress("开始处理...")

            try {
                // 阶段2：IO线程 - 数据获取
                val data = withContext(Dispatchers.IO) {
                    fetchData()
                }

                // 阶段3：后台线程 - CPU密集型计算
                val processedData = withContext(Dispatchers.Default) {
                    processHeavyData(data)
                }

                // 阶段4：EDT - 更新UI
                updateProgress("处理完成")
                displayResults(processedData)

            } catch (e: Exception) {
                // 阶段5：EDT - 错误处理
                updateProgress("处理失败")
                showError(e.message)
            }
        }
    }

    fun cleanup() {
        scope.cancel() // 取消所有协程
    }
}
```

### 协程与EDT交互的注意事项

**1. 避免在EDT上使用runBlocking**
```kotlin
// ❌ 危险：在EDT上阻塞
fun badPracticeInEDT() {
    runBlocking {  // 阻塞EDT
        val result = networkCall()
        updateUI(result)
    }
}

// ✅ 正确：使用协程
fun goodPracticeInEDT() {
    scope.launch {
        val result = networkCall()  // 不阻塞EDT
        updateUI(result)
    }
}
```

**2. 正确处理协程取消**
```kotlin
scope.launch {
    try {
        // 长时间操作需要定期检查取消状态
        for (item in largeList) {
            ensureActive()  // 检查协程是否被取消
            processItem(item)
        }
    } catch (e: CancellationException) {
        // 协程被取消，清理资源
        cleanup()
        throw e
    }
}
```

---

## 3. PSI线程的概念深入解析

### PSI线程的本质

**PSI (Program Structure Interface)** 不是独立的线程，而是一套**线程安全的访问机制**：

- PSI访问通过**读锁**和**写锁**来保证线程安全
- ReadAction/WriteAction是PSI访问的核心机制
- PSI操作可以在任何线程上执行，但需要正确的锁管理

### ReadAction/WriteAction的线程机制

```kotlin
// 1. ReadAction - 读取PSI数据
val selectionText = ReadAction.compute<String?, Throwable> {
    editor.selectionModel.selectedText
}

// 2. 复杂PSI分析
val codeContext = ReadAction.compute<CodeContext?, Throwable> {
    val analyzer = UniversalCodeAnalyzer(project)
    val language = analyzer.detectLanguage(element)
    val projectInfo = analyzer.getProjectInfo()
    // 更多PSI操作...
    buildContextResult()
}

// 3. WriteAction - 修改PSI（较少使用）
WriteAction.run<Throwable> {
    // 修改代码结构
    psiElement.replace(newElement)
}
```

### PSI访问的线程安全规则

**规则1：所有PSI访问都需要在ReadAction或WriteAction中**
```kotlin
// ❌ 错误：直接访问PSI
fun analyzeWrong(psiFile: PsiFile) {
    val elements = psiFile.children // 可能抛出异常
}

// ✅ 正确：在ReadAction中访问
fun analyzeCorrect(psiFile: PsiFile) {
    val elements = ReadAction.compute<List<PsiElement>, Throwable> {
        psiFile.children.toList()
    }
}
```

**规则2：PSI操作应该在后台线程执行**
```kotlin
// ✅ 正确：后台线程 + ReadAction
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "分析代码", true) {
    override fun run(indicator: ProgressIndicator) {
        val analysis = ReadAction.compute<AnalysisResult, Throwable> {
            analyzePsiData(psiFile)
        }

        ApplicationManager.getApplication().invokeLater {
            displayAnalysis(analysis)
        }
    }
})
```

### PSI锁的工作原理

**读锁 (ReadAction)**：
- 多个线程可以同时获取读锁
- 读锁持有期间不能获取写锁
- 适合只读操作

**写锁 (WriteAction)**：
- 独占锁，只能被一个线程获取
- 获取写锁时所有读锁必须释放
- 适合修改PSI结构

**锁的使用示例**：
```kotlin
// ✅ 正确的PSI访问模式
class PSIAccessExample {

    fun analyzeAndModify(psiFile: PsiFile) {
        // 1. 读取分析（可以在任何线程）
        val analysis = ReadAction.compute<AnalysisResult, Throwable> {
            // 多个PSI读取操作
            val classes = PsiTreeUtil.getChildrenOfType(psiFile, PsiClass::class.java)
            val methods = classes.flatMap { it.methods.toList() }
            AnalysisResult(classes, methods)
        }

        // 2. 修改PSI（需要WriteAction）
        WriteCommandAction.runWriteCommandAction(project) {
            // PSI修改操作
            analysis.methods.forEach { method ->
                addCommentToMethod(method)
            }
        }
    }

    private fun addCommentToMethod(method: PsiMethod) {
        // 添加注释到方法
    }
}
```

---

## 4. 完整的最佳实践代码示例

### 网络请求 + UI更新 + PSI操作的完整流程

```kotlin
class CompleteWorkflowExample {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun executeCompleteWorkflow(project: Project, editor: Editor) {
        scope.launch {
            try {
                // 第1步：EDT - 显示进度和获取基础数据
                showProgress("准备分析...")

                // 安全获取选中文本（ReadAction在EDT中也是安全的）
                val selectionText = ReadAction.compute<String?, Throwable> {
                    editor.selectionModel.selectedText
                } ?: ""

                // 第2步：IO线程 - 网络请求
                showProgress("正在获取AI建议...")
                val aiResponse = withContext(Dispatchers.IO) {
                    makeNetworkCall(selectionText)
                }

                // 第3步：后台线程 - PSI分析和处理
                showProgress("分析代码结构...")
                val analysisResult = withContext(Dispatchers.Default) {
                    performPSIAnalysis(project, aiResponse)
                }

                // 第4步：EDT - 更新UI
                showProgress(false)
                updateUIWithResults(analysisResult)

            } catch (e: CancellationException) {
                // 协程被取消，清理资源
                cleanup()
            } catch (e: Exception) {
                // 第5步：EDT - 错误处理
                showProgress(false)
                showError("操作失败: ${e.message}")
                NekoamaLogger.error("Workflow", "Failed to complete workflow", e)
            }
        }
    }

    private suspend fun makeNetworkCall(input: String): String {
        // 使用ProgressManager的取消机制
        return suspendCancellableCoroutine { continuation ->
            val task = object : Task.Backgroundable(null, "网络请求", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        // 检查取消状态
                        indicator.checkCanceled()

                        // 执行网络请求
                        val response = httpClient.post(url) {
                            setBody(input)
                            timeout { requestTimeoutMillis = 30000 }
                        }

                        if (continuation.isActive) {
                            continuation.resume(response.body.toString())
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }

            ProgressManager.getInstance().run(task)

            // 协程取消时取消任务
            continuation.invokeOnCancellation {
                task.cancel()
            }
        }
    }

    private suspend fun performPSIAnalysis(project: Project, aiResponse: String): AnalysisResult {
        return withContext(Dispatchers.IO) {
            // 在后台线程执行PSI分析
            ReadAction.compute<AnalysisResult, Throwable> {
                // PSI访问
                val psiManager = PsiManager.getInstance(project)
                val analysis = analyzeWithPsi(aiResponse)
                analysis
            }
        }
    }

    private fun showProgress(message: String) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            progressBar.isVisible = true
            progressLabel.text = message
        }
    }

    private fun showProgress(show: Boolean) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            progressBar.isVisible = show
            if (!show) progressLabel.text = ""
        }
    }

    private fun cleanup() {
        // 清理资源
        scope.cancel()
    }
}
```

### 统一的异步Action基类

```kotlin
abstract class AsyncBaseAction : BaseAction() {

    protected fun executeAsync(
        project: Project,
        title: String = "处理中",
        cancellable: Boolean = true,
        task: suspend (indicator: ProgressIndicator) -> Unit
    ) {
        val backgroundTask = object : Task.Backgroundable(project, title, cancellable) {
            override fun run(indicator: ProgressIndicator) {
                runBlocking {
                    try {
                        task(indicator)
                    } catch (e: CancellationException) {
                        // 任务被取消，不需要特别处理
                        throw e
                    } catch (e: Exception) {
                        NekoamaLogger.error(title, "Background task failed", e)
                        ApplicationManager.getApplication().invokeLater {
                            showErrorNotification("操作失败: ${e.message}")
                        }
                    }
                }
            }
        }
        ProgressManager.getInstance().run(backgroundTask)
    }

    protected fun <T> executeWithResult(
        project: Project,
        title: String = "处理中",
        cancellable: Boolean = true,
        task: suspend (indicator: ProgressIndicator) -> T,
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit = { showErrorNotification(it.message ?: "未知错误") }
    ) {
        val backgroundTask = object : Task.Backgroundable(project, title, cancellable) {
            var result: T? = null
            var error: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                runBlocking {
                    try {
                        result = task(indicator)
                    } catch (e: Exception) {
                        error = e
                    }
                }
            }

            override fun onSuccess() {
                error?.let { onError(it) } ?: result?.let { onSuccess(it) }
            }
        }
        ProgressManager.getInstance().run(backgroundTask)
    }
}
```

### 使用示例：自定义生成Action

```kotlin
class CustomGenerateAction : AsyncBaseAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        executeAsync(project, "生成中", true) { indicator ->
            indicator.isIndeterminate = true
            indicator.text = "正在分析代码..."

            // 1. 获取选中文本
            val selectionText = ReadAction.compute<String?, Throwable> {
                editor.selectionModel.selectedText
            } ?: ""

            if (selectionText.isBlank()) {
                throw IllegalArgumentException("请先选择要生成的代码")
            }

            // 2. 提取代码上下文
            indicator.text = "提取代码上下文..."
            val context = ReadAction.compute<CodeContext?, Throwable> {
                val element = editor.getCaretModel().offset.let {
                    editor.document.charsSequence.let { chars ->
                        PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                            ?.findElementAt(it)
                    }
                }
                element?.let { extractContext(it) }
            } ?: throw IllegalArgumentException("无法提取代码上下文")

            // 3. AI生成
            indicator.text = "正在生成代码..."
            val result = withContext(Dispatchers.IO) {
                val provider = createAIProvider()
                provider.generate(context, selectionText)
            }

            // 4. 插入代码
            indicator.text = "插入代码..."
            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val offset = editor.selectionModel.selectionEnd
                document.insertString(offset, "\n$result\n")
            }

            // 5. 显示成功通知
            ApplicationManager.getApplication().invokeLater {
                showSuccessNotification("代码生成成功")
            }
        }
    }
}
```

---

## 5. 性能优化技巧

### 1. 防抖机制 - 避免频繁刷新

```kotlin
class DebounceExample {
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 2000L

    fun refreshWithDebounce() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefreshTime > refreshDebounceMs) {
            lastRefreshTime = currentTime
            performRefresh()
        }
    }

    // 更高级的防抖实现
    private val debounceHandler = Handler(DispatcherThread.getUiThreadDispatcher())
    private var debounceRunnable: Runnable? = null

    fun debouncedAction(delayMs: Long = 2000, action: () -> Unit) {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }

        debounceRunnable = Runnable { action() }
        debounceHandler.postDelayed(debounceRunnable!!, delayMs)
    }
}
```

### 2. 批量PSI访问 - 减少锁竞争

```kotlin
class BatchPSIAccess {

    fun analyzeFileEfficiently(psiFile: PsiFile) {
        // ✅ 在单个ReadAction中完成多个PSI操作
        val analysisResult = ReadAction.compute<AnalysisResult, Throwable> {
            // 一次性获取所有需要的PSI数据
            val classes = PsiTreeUtil.getChildrenOfType(psiFile, PsiClass::class.java) ?: emptyArray()
            val methods = classes.flatMap { it.methods.toList() }
            val fields = classes.flatMap { it.fields.toList() }
            val imports = psiFile.importList?.importStatements ?: emptyList()

            // 构建分析结果
            AnalysisResult(
                classes = classes.toList(),
                methods = methods,
                fields = fields,
                imports = imports
            )
        }

        // 后台处理分析结果
        processAnalysisResult(analysisResult)
    }

    // ❌ 错误：多次PSI访问
    fun analyzeFileInefficiently(psiFile: PsiFile) {
        // 每次都获取读锁，效率低下
        val classes = ReadAction.compute { psiFile.classes }
        val methods = ReadAction.compute { psiFile.methods }
        val fields = ReadAction.compute { psiFile.fields }
        // ...
    }
}
```

### 3. 缓存机制 - 避免重复计算

```kotlin
class CacheManager {

    // 简单的内存缓存
    private val analysisCache = mutableMapOf<String, AnalysisResult>()
    private val cacheLock = ReentrantReadWriteLock()

    fun getCachedAnalysis(key: String, compute: () -> AnalysisResult): AnalysisResult {
        // 读锁检查缓存
        cacheLock.readLock().lock()
        val cached = analysisCache[key]
        cacheLock.readLock().unlock()

        if (cached != null) {
            return cached
        }

        // 写锁更新缓存
        cacheLock.writeLock().lock()
        try {
            // 双重检查锁定模式
            return analysisCache.getOrPut(key) {
                compute()
            }
        } finally {
            cacheLock.writeLock().unlock()
        }
    }

    // 基于文件修改时间的智能缓存
    private val fileAnalysisCache = mutableMapOf<String, CachedAnalysis>()

    data class CachedAnalysis(
        val result: AnalysisResult,
        val fileTimestamp: Long,
        val fileSize: Long
    )

    fun getAnalysisWithFileCache(psiFile: PsiFile, compute: () -> AnalysisResult): AnalysisResult {
        val filePath = psiFile.virtualFile?.path ?: return compute()
        val currentTimestamp = psiFile.virtualFile.timeStamp
        val currentSize = psiFile.virtualFile.length

        val cached = fileAnalysisCache[filePath]
        if (cached != null &&
            cached.fileTimestamp == currentTimestamp &&
            cached.fileSize == currentSize) {
            return cached.result
        }

        val result = compute()
        fileAnalysisCache[filePath] = CachedAnalysis(result, currentTimestamp, currentSize)
        return result
    }
}
```

### 4. 分批处理 - 大量数据处理

```kotlin
class BatchProcessor {

    suspend fun processLargeDataset(items: List<Item>) {
        val batchSize = 50
        val totalBatches = (items.size + batchSize - 1) / batchSize

        for (i in items.chunked(batchSize).withIndex()) {
            val batch = i.value
            val batchNumber = i.index + 1

            // 在后台线程处理批次
            withContext(Dispatchers.IO) {
                processBatch(batch, batchNumber, totalBatches)
            }

            // 更新进度
            withContext(Dispatchers.Main) {
                updateProgress(batchNumber, totalBatches)
            }

            // 给UI线程更新机会
            yield()
        }
    }

    private fun processBatch(batch: List<Item>, batchNumber: Int, totalBatches: Int) {
        // 处理单个批次
        batch.forEach { item ->
            processItem(item)
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        // 更新UI进度
    }
}
```

### 5. 异步图片加载

```kotlin
class AsyncImageLoader {
    private val imageCache = mutableMapOf<String, Icon>()
    private val loadingJobs = mutableMapOf<String, Job>()

    fun loadImageAsync(url: String, onLoaded: (Icon) -> Unit, onError: (Exception) -> Unit) {
        // 检查缓存
        imageCache[url]?.let { cached ->
            onLoaded(cached)
            return
        }

        // 取消之前的加载任务
        loadingJobs[url]?.cancel()

        // 启动新的加载任务
        loadingJobs[url] = CoroutineScope(Dispatchers.IO).launch {
            try {
                val icon = loadImageFromUrl(url)

                withContext(Dispatchers.Main) {
                    imageCache[url] = icon
                    onLoaded(icon)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            } finally {
                loadingJobs.remove(url)
            }
        }
    }

    private fun loadImageFromUrl(url: String): Icon {
        // 实际的图片加载逻辑
        return IconLoader.getIcon("/icons/default.png")
    }
}
```

---

## 6. 常见问题和解决方案

### 问题1：EDT阻塞导致IDE无响应

**症状**：IDE界面卡死，用户无法操作

**原因**：在EDT上执行耗时操作

**解决方案**：
```kotlin
// ❌ 错误代码
fun badBlockingOperation() {
    val data = heavyComputation() // 阻塞EDT
    updateUI(data)
}

// ✅ 正确代码
fun goodAsyncOperation() {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "处理中", true) {
        override fun run(indicator: ProgressIndicator) {
            val data = heavyComputation() // 后台线程
            ApplicationManager.getApplication().invokeLater {
                updateUI(data) // EDT更新
            }
        }
    })
}
```

### 问题2：PSI访问异常

**症状**：`AssertionError: Read access is allowed inside read-action only`

**原因**：直接访问PSI而没有使用ReadAction/WriteAction

**解决方案**：
```kotlin
// ❌ 错误代码
fun wrongPSIAccess(psiFile: PsiFile) {
    val classes = psiFile.classes // 可能抛出异常
}

// ✅ 正确代码
fun correctPSIAccess(psiFile: PsiFile) {
    val classes = ReadAction.compute<Array<PsiClass>, Throwable> {
        psiFile.classes
    }
}
```

### 问题3：协程内存泄漏

**症状**：插件卸载后仍有后台任务运行

**原因**：协程作用域没有正确取消

**解决方案**：
```kotlin
// ❌ 错误代码
class LeakyComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startTask() {
        scope.launch {
            // 长期运行的任务
        }
    }

    // 缺少dispose方法
}

// ✅ 正确代码
class ProperComponent : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startTask() {
        scope.launch {
            // 长期运行的任务
        }
    }

    override fun dispose() {
        scope.cancel() // 取消所有协程
    }
}
```

### 问题4：数据竞争和线程安全

**症状**：数据不一致，偶发性错误

**原因**：多线程同时访问共享数据

**解决方案**：
```kotlin
// ❌ 错误代码
class UnsafeDataManager {
    private val data = mutableListOf<String>()

    fun addData(item: String) {
        data.add(item) // 非线程安全
    }

    fun getData(): List<String> {
        return data.toList() // 非线程安全
    }
}

// ✅ 正确代码
class SafeDataManager {
    private val data = CopyOnWriteArrayList<String>()

    fun addData(item: String) {
        data.add(item) // 线程安全
    }

    fun getData(): List<String> {
        return data.toList() // 线程安全
    }
}
```

### 问题5：过度使用EDT

**症状**：UI响应慢，即使在后台操作中

**原因**：在EDT上执行了太多轻量级操作

**解决方案**：
```kotlin
// ❌ 错误代码：在EDT上执行过多操作
fun overuseEDT() {
    ApplicationManager.getApplication().invokeLater {
        for (i in 1..1000) {
            updateProgress(i) // 1000次UI更新
            Thread.sleep(1) // 即使很短的延迟也是危险的
        }
    }
}

// ✅ 正确代码：批量更新
fun efficientUIUpdate() {
    scope.launch {
        for (i in 1..1000) {
            // 后台处理
            processData(i)

            // 每100次更新一次UI
            if (i % 100 == 0) {
                withContext(Dispatchers.Main) {
                    updateProgress(i)
                }
            }
        }
    }
}
```

---

## 总结

基于项目的实际经验，IntelliJ IDEA插件开发的线程最佳实践可以总结为：

1. **EDT原则**：UI操作必须在EDT上执行，耗时操作必须在后台线程
2. **协程使用**：合理使用协程调度器，确保正确的线程切换
3. **PSI访问**：所有PSI操作都在ReadAction/WriteAction中进行
4. **异步模式**：使用Task.Backgroundable或协程处理耗时操作
5. **性能优化**：防抖、缓存、批量处理等技术提升性能
6. **错误处理**：统一的异常处理和资源清理机制
7. **内存管理**：正确管理协程生命周期，避免内存泄漏

遵循这些实践可以开发出高性能、稳定、用户体验良好的IntelliJ IDEA插件。