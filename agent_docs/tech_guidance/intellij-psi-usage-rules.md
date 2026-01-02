# PSI Technical Constraints

## 1. Axioms (不可违背的公理)
- **Status**: Stable
- **Core Principle**: All PSI access must be protected by ReadAction/WriteAction and executed on appropriate threads

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| PSI Access | `psiFile.children` (direct) | `ReadAction.compute { psiFile.children }` |
| UI Operations | `updateUI()` (background) | `ApplicationManager.invokeLater { updateUI() }` |
| Long Operations | `heavyTask()` (on EDT) | `Task.Backgroundable { heavyTask() }` |
| File I/O | `Files.read()` (on EDT) | `withContext(Dispatchers.IO) { Files.read() }` |
| PSI Modification | `element.replace()` (direct) | `WriteCommandAction.runWriteCommandAction { element.replace() }` |

## 3. Critical Snippets (核心代码范式)
```kotlin
// Good Pattern - PSI Analysis
val analysisResult = ReadAction.compute<AnalysisResult, Throwable> {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val classes = PsiTreeUtil.getChildrenOfType(psiFile, PsiClass::class.java)
    val methods = classes?.flatMap { it.methods.toList() } ?: emptyList()
    AnalysisResult(classes, methods)
}

// Good Pattern - Async Operation
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Analysis", true) {
    override fun run(indicator: ProgressIndicator) {
        val result = ReadAction.compute { analyzePSI() }
        ApplicationManager.getApplication().invokeLater {
            updateUI(result)
        }
    }
})
```

## 4. Verification (如何验证)
* Check: All PSI access is wrapped in ReadAction/WriteAction
* Check: UI operations are executed on EDT via invokeLater
* Check: Background operations use Task.Backgroundable or coroutines with Dispatchers.IO
* Check: File operations are performed on non-EDT threads
* Check: PSI modifications use WriteCommandAction.runWriteCommandAction