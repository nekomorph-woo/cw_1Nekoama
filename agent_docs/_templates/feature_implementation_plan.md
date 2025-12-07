<!-- 
TEMPLATE INSTRUCTIONS FOR AI AGENT:
1. USE this template to structure the output of Mode 0 (Inception Phase).
2. DO NOT hallucinate paths or stacks. If you don't know the existing project structure, ASK the user to provide `tree` or specific folder names.
3. IF any section below cannot be filled due to missing info, PAUSE and ASK the user (e.g., "What is the preferred UI library?").
4. DELETE these instruction comments in the final output.
-->

# [Feature Name] Implementation Plan

## 1. Overview
**Goal:** [Concise description of what this feature achieves]
**Business Value:** [Why are we building this?]
**Scope:**
- [x] In Scope: [Core functionalities]
- [ ] Out of Scope: [What we are NOT doing right now]

## 2. Tech Stack
*(Agent: Verify these against `CLAUDE.md` or existing project config)*
[Adaptive Tech Stack]
- **Backend/Core:** [e.g., Kotlin + PSI / Java / Python / Node.js]
- **Frontend/UI:** [e.g., HTML/JS / React / Swing / Jetpack Compose]
- **Data/Storage:** [e.g., JSON / SQLite / Memory]
- **Testing:** [e.g., JUnit 5 / Jest / MockK]

## 3. Architecture Design
*(Agent: Map logical layers to ACTUAL directory paths. Do not invent non-existent folders unless proposing a refactor or a new feature that has never appeared in this project.)*

### 3.1 Data Model Layer
*Path: `project root/.../model/`*
- `[ModelName].[ext]`: [Description of the data structure]
- `[EnumName].[ext]`: [Description]

### 3.2 Core Logic Layer
*Path: `project root/.../core/`*
- `[ServiceName].[ext]`: [Business logic implementation]
- `[HelperName].[ext]`: [Utils]

### 3.3 UI / Presentation Layer
*Path: `project root/.../ui/` or `resources/...`*
- `[ActionName].[ext]`: [Entry point]
- `[TemplateName].[ext]`: [View template]

## 4. Implementation Steps (Phasing)
*(Agent: Break down into testable increments. Phase 1 must be the MVP.)*

### Phase 1: Core Logic & Data Structure (Priority: High)
1. **Define Models:** Implement Data Classes/Interfaces.
2. **Core Algorithm:** Implement the main logic (TDD approach).
    - *Test Focus:* Verify algorithm input/output without UI.

### Phase 2: Service Orchestration (Priority: High)
1. **Service Layer:** Connect core logic to the application lifecycle.
2. **Async/Threading:** Handle background execution if necessary.

### Phase 3: UI & Visualization (Priority: Medium)
1. **Mock Data:** Generate static data for frontend development.
2. **Visual Implementation:** Build the view (VDD approach).
3. **Integration:** Connect Backend Service to Frontend View.

### Phase 4: Polish & Advanced Features (Priority: Low)
1. **Edge Cases:** Handle errors, empty states, or large datasets.
2. **Performance:** Optimization.

## 5. Technical Constraints
*(Agent: Infer these from `agent_docs/tech_guidance/` or ask user)*
1. **Concurrency:** [e.g., Background Task / Main Thread]
2. **Performance:** [e.g., Timeout limits / Memory constraints]
3. **Compatibility:** [e.g., JDK version / JS third-party dependency / Current Project tools or service / Browser version]
4. **Security:** [e.g., No raw SQL / Input sanitization]

## 6. Testing Strategy
1. **Unit Tests:** [Target classes to test]
2. **Integration Tests:** [Flows to verify]
3. **Manual/UI Verification:** [How to verify visual elements]

## 7. Key File Checklist
*(Agent: List the exact file paths that will be created or modified. This serves as the "Contract" for coding.)*

- [ ] `path/to/FileA.ext`
- [ ] `path/to/FileB.ext`
- [ ] `path/to/FileC.ext`

## 8. Definition of Done (Delivery Standards)
1. **Functionality:** [Feature X works as expected]
2. **Quality:** 100% Test pass rate. No new lint errors.
3. **Documentation:** [e.g., KDoc/JSDoc/Python Doc/JDoc] added. `active_context.md` updated.