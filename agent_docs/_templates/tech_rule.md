# [Domain Name] Technical Constraints

## 1. Axioms (不可违背的公理)
- **Status**: [Stable/Experimental]
- **Core Principle**: [One sentence summary, e.g., All UI must be EDT safe]

## 2. Mapping Rules (规则映射)
| Category | ❌ Forbidden (Strict Ban) | ✅ Required (Pattern) |
| :--- | :--- | :--- |
| [e.g., Color] | [e.g., `Color.RED`] | [e.g., `UIUtil.getErrorForeground()`] |
| [e.g., IO] | [e.g., `File.read()`] | [e.g., `VirtualFile.getInputStream()`] |

## 3. Critical Snippets (核心代码范式)
```[lang]
// Good Pattern
[Paste only the correct code pattern here]
```

## 4. Verification (如何验证)
* Check: [What to look for in code review]