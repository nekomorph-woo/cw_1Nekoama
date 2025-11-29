/**
 * AntV G6 可视化组件
 *
 * 提供代码依赖关系的交互式可视化功能，包括：
 * - 包级依赖图
 * - 类级关系图
 * - 方法调用关系图和调用链分析
 * - 业务场景分析图
 */

class DependencyVisualizer {
    constructor() {
        this.graphs = {};
        this.currentData = null;
        this.currentLayout = 'dagre';

        // 初始化
        this.init();
    }

    /**
     * 初始化可视化组件
     */
    init() {
        console.log('初始化依赖分析可视化组件...');

        // 加载分析数据
        this.loadAnalysisData();

        // 绑定事件监听器
        this.bindEventListeners();

        // 初始化标签页
        this.initTabs();

        // 初始化图表
        this.initGraphs();

        // 生成统计信息
        this.generateStatistics();

        // 隐藏加载遮罩
        this.hideLoading();
    }

    /**
     * 加载分析数据
     */
    loadAnalysisData() {
        try {
            const dataElement = document.getElementById('analysis-data');
            if (dataElement) {
                this.currentData = JSON.parse(dataElement.textContent);
                console.log('分析数据加载成功:', this.currentData);

                // 验证数据结构
                if (!this.validateAnalysisData(this.currentData)) {
                    console.warn('分析数据结构不完整，使用默认值');
                    this.currentData = this.normalizeAnalysisData(this.currentData);
                }

                // 更新项目名称
                const projectNameElement = document.getElementById('project-name');
                if (projectNameElement && this.currentData.metadata) {
                    projectNameElement.textContent = this.currentData.metadata.projectName || 'Nekoama分析报告';
                }
            } else {
                console.error('找不到分析数据元素');
                this.showDataError('找不到分析数据元素');
            }
        } catch (error) {
            console.error('解析分析数据失败:', error);
            this.showDataError('解析分析数据失败: ' + error.message);
        }
    }

    /**
     * 验证分析数据结构
     */
    validateAnalysisData(data) {
        // 基本结构检查
        if (!data || typeof data !== 'object') return false;
        if (!data.metadata) return false;

        // 检查是否有足够的数据进行可视化
        const hasPackages = data.packages && Array.isArray(data.packages) && data.packages.length > 0;
        const hasClasses = data.classes && Array.isArray(data.classes) && data.classes.length > 0;

        console.log('数据验证结果:', {
            hasPackages,
            hasClasses,
            packageCount: data.packages?.length || 0,
            classCount: data.classes?.length || 0
        });

        return hasPackages || hasClasses;
    }

    /**
     * 规范化分析数据
     */
    normalizeAnalysisData(data) {
        const normalized = {
            metadata: data.metadata || { projectName: 'Nekoama分析报告' },
            packages: [],
            classes: [],
            packageDependencies: [],
            classDependencies: [],
            businessEntryPoints: [],
            codeSmells: []
        };

        // 处理包数据
        if (data.packages && Array.isArray(data.packages)) {
            normalized.packages = data.packages.map(pkg => ({
                id: pkg.id || pkg.name || 'unknown',
                name: pkg.name || 'Unknown Package',
                classCount: pkg.classCount || 0,
                metrics: pkg.metrics || { fanOut: 0, fanIn: 0, instability: 0 }
            }));
        }

        // 处理类数据
        if (data.classes && Array.isArray(data.classes)) {
            normalized.classes = data.classes.map(cls => ({
                id: cls.id || cls.qualifiedName || 'unknown',
                name: cls.name || 'Unknown Class',
                type: cls.type || 'CLASS',
                metrics: cls.metrics || { complexityScore: 0, methodCount: 0 }
            }));
        }

        // 处理依赖关系
        if (data.packageDependencies && Array.isArray(data.packageDependencies)) {
            normalized.packageDependencies = data.packageDependencies;
        }

        if (data.classDependencies && Array.isArray(data.classDependencies)) {
            normalized.classDependencies = data.classDependencies;
        }

        return normalized;
    }

    /**
     * 显示数据错误信息
     */
    showDataError(message) {
        const errorHtml = `
            <div style="padding: 20px; text-align: center; color: #666;">
                <h3>数据加载失败</h3>
                <p>${message}</p>
                <p>请尝试重新生成报告或检查分析结果。</p>
            </div>
        `;

        // 在所有图表容器中显示错误信息
        ['package-graph', 'class-graph', 'method-call-graph', 'call-chain-graph', 'scene-graph'].forEach(id => {
            const container = document.getElementById(id);
            if (container) {
                container.innerHTML = errorHtml;
            }
        });
    }

    /**
     * 绑定事件监听器
     */
    bindEventListeners() {
        // 标签页切换
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                this.switchTab(e.target.dataset.tab);
            });
        });

        // 问题分析标签页
        document.querySelectorAll('.issues-tab-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                this.switchIssuesTab(e.target.dataset.issues);
            });
        });

        // 布局切换
        const layoutSelect = document.getElementById('layout-select');
        if (layoutSelect) {
            layoutSelect.addEventListener('change', (e) => {
                this.currentLayout = e.target.value;
                this.updateCurrentGraphLayout();
            });
        }

        // 缩放控制
        document.getElementById('zoom-in-btn')?.addEventListener('click', () => {
            this.zoomCurrentGraph(1.2);
        });

        document.getElementById('zoom-out-btn')?.addEventListener('click', () => {
            this.zoomCurrentGraph(0.8);
        });

        document.getElementById('fit-view-btn')?.addEventListener('click', () => {
            this.fitCurrentGraphView();
        });

        // 刷新按钮
        document.getElementById('refresh-btn')?.addEventListener('click', () => {
            this.refreshData();
        });

        // 导出按钮
        document.getElementById('export-btn')?.addEventListener('click', () => {
            this.exportReport();
        });

        // 打印按钮
        document.getElementById('print-btn')?.addEventListener('click', () => {
            window.print();
        });

        // 全屏按钮
        document.getElementById('fullscreen-btn')?.addEventListener('click', () => {
            this.toggleFullscreen();
        });

        
        // 场景选择
        const sceneSelect = document.getElementById('scene-select');
        if (sceneSelect) {
            sceneSelect.addEventListener('change', (e) => {
                this.updateSceneGraph(e.target.value);
            });
        }

        // 节点筛选器
        this.setupNodeFilters();
    }

    /**
     * 设置节点筛选器
     */
    setupNodeFilters() {
        const filterCheckboxes = [
            'filter-high-complexity',
            'filter-cycles',
            'filter-controllers',
            'filter-services',
            'filter-pojos',
            'filter-interfaces'
        ];

        filterCheckboxes.forEach(id => {
            const checkbox = document.getElementById(id);
            if (checkbox) {
                checkbox.addEventListener('change', () => {
                    this.updateCurrentGraphFiltering();
                });
            }
        });

        const minDependenciesInput = document.getElementById('min-dependencies');
        if (minDependenciesInput) {
            minDependenciesInput.addEventListener('input', () => {
                this.updateCurrentGraphFiltering();
            });
        }
    }

    /**
     * 初始化标签页
     */
    initTabs() {
        // 默认显示包级视图
        this.switchTab('package-view');
        this.switchIssuesTab('critical');
    }

    /**
     * 切换标签页
     */
    switchTab(tabId) {
        // 更新按钮状态
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        document.querySelector(`[data-tab="${tabId}"]`)?.classList.add('active');

        // 更新内容显示
        document.querySelectorAll('.tab-pane').forEach(pane => {
            pane.classList.remove('active');
        });
        document.getElementById(tabId)?.classList.add('active');

        // 根据标签页类型更新图表
        switch (tabId) {
            case 'package-view':
                this.updatePackageGraph();
                break;
            case 'class-view':
                this.updateClassGraph();
                break;
            case 'method-view':
                this.updateMethodAnalysis();
                break;
            case 'scene-view':
                this.updateSceneSelector();
                break;
        }
    }

    /**
     * 切换问题分析标签页
     */
    switchIssuesTab(severity) {
        // 更新按钮状态
        document.querySelectorAll('.issues-tab-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        document.querySelector(`[data-issues="${severity}"]`)?.classList.add('active');

        // 更新问题列表
        this.updateIssuesList(severity);
    }

    /**
     * 初始化图表
     */
    initGraphs() {
        // 包级依赖图
        this.initPackageGraph();

        // 类级关系图
        this.initClassGraph();

        // 场景分析图
        this.initSceneGraph();
    }

    /**
     * 初始化包级依赖图
     */
    initPackageGraph() {
        const container = document.getElementById('package-graph');
        if (!container) return;

        this.graphs.package = new G6.Graph({
            container: 'package-graph',
            width: container.clientWidth,
            height: container.clientHeight,
            modes: {
                default: [
                    'drag-canvas',
                    'zoom-canvas',
                    'drag-node',
                    {
                        type: 'tooltip',
                        formatText(model) {
                            return `包名: ${model.id}\\n依赖数: ${model.dependencyCount || 0}\\n类数: ${model.classCount || 0}`;
                        }
                    }
                ]
            },
            defaultNode: {
                size: 60,
                style: {
                    fill: '#C6E5FF',
                    stroke: '#5B8FF9',
                    lineWidth: 2
                },
                labelCfg: {
                    style: {
                        fill: '#000',
                        fontSize: 12
                    },
                    position: 'center'
                }
            },
            defaultEdge: {
                style: {
                    stroke: '#e2e2e2',
                    lineWidth: 2
                },
                labelCfg: {
                    autoRotate: true,
                    style: {
                        fill: '#666',
                        fontSize: 10
                    }
                }
            },
            layout: {
                type: 'dagre',
                rankdir: 'LR',
                nodesep: 20,
                ranksep: 50
            }
        });

        // 绑定节点点击事件
        this.graphs.package.on('node:click', (e) => {
            this.showPackageDetails(e.item.getModel());
        });
    }

    /**
     * 初始化类级关系图
     */
    initClassGraph() {
        const container = document.getElementById('class-graph');
        if (!container) return;

        this.graphs.class = new G6.Graph({
            container: 'class-graph',
            width: container.clientWidth,
            height: container.clientHeight,
            modes: {
                default: [
                    'drag-canvas',
                    'zoom-canvas',
                    'drag-node',
                    {
                        type: 'tooltip',
                        formatText(model) {
                            const details = [];
                            details.push(`类名: ${model.label || model.id}`);
                            if (model.type) details.push(`类型: ${model.type}`);
                            if (model.complexity) details.push(`复杂度: ${model.complexity}`);
                            if (model.methodCount) details.push(`方法数: ${model.methodCount}`);
                            return details.join('\\n');
                        }
                    }
                ]
            },
            defaultNode: {
                size: 40,
                style: {
                    fill: '#C6E5FF',
                    stroke: '#5B8FF9',
                    lineWidth: 2
                },
                labelCfg: {
                    style: {
                        fill: '#000',
                        fontSize: 10
                    },
                    position: 'bottom'
                }
            },
            defaultEdge: {
                style: {
                    stroke: '#e2e2e2',
                    lineWidth: 1,
                    endArrow: true
                }
            },
            layout: {
                type: 'force',
                preventOverlap: true,
                nodeSize: 40
            }
        });

        // 绑定节点点击事件
        this.graphs.class.on('node:click', (e) => {
            this.showClassDetails(e.item.getModel());
        });
    }

    /**
     * 初始化场景分析图
     */
    initSceneGraph() {
        const container = document.getElementById('scene-graph');
        if (!container) return;

        this.graphs.scene = new G6.Graph({
            container: 'scene-graph',
            width: container.clientWidth,
            height: container.clientHeight,
            modes: {
                default: [
                    'drag-canvas',
                    'zoom-canvas',
                    'drag-node',
                    'tooltip'
                ]
            },
            defaultNode: {
                size: 50,
                style: {
                    fill: '#FFE7BA',
                    stroke: '#FA8C16',
                    lineWidth: 2
                }
            },
            defaultEdge: {
                style: {
                    stroke: '#e2e2e2',
                    lineWidth: 2,
                    endArrow: true
                }
            },
            layout: {
                type: 'dagre',
                rankdir: 'TB'
            }
        });
    }

    /**
     * 更新包级依赖图
     */
    updatePackageGraph() {
        if (!this.currentData || !this.graphs.package) return;

        const data = this.convertToPackageGraphData(this.currentData);
        this.graphs.package.data(data);
        this.graphs.package.render();
    }

    /**
     * 更新类级关系图
     */
    updateClassGraph() {
        if (!this.currentData || !this.graphs.class) return;

        const data = this.convertToClassGraphData(this.currentData);
        this.graphs.class.data(data);
        this.graphs.class.render();
    }

    /**
     * 转换数据为包级图格式
     */
    convertToPackageGraphData(analysisData) {
        const nodes = [];
        const edges = [];

        console.log('转换包级图数据，包数量:', analysisData.packages?.length || 0);

        // 添加包节点
        if (analysisData.packages && Array.isArray(analysisData.packages)) {
            analysisData.packages.forEach(pkg => {
                const complexity = this.calculatePackageComplexity(pkg, analysisData);
                nodes.push({
                    id: pkg.id || pkg.name || 'unknown',
                    label: pkg.name || 'Unknown Package',
                    classCount: pkg.classCount || 0,
                    dependencyCount: pkg.metrics?.fanOut || 0,
                    complexity: complexity,
                    style: {
                        fill: this.getComplexityColor(complexity)
                    }
                });
            });
        }

        // 添加依赖边
        if (analysisData.packageDependencies && Array.isArray(analysisData.packageDependencies)) {
            analysisData.packageDependencies.forEach(dep => {
                if (dep.dependencies && Array.isArray(dep.dependencies)) {
                    dep.dependencies.forEach(targetPkg => {
                        edges.push({
                            source: dep.packageName,
                            target: typeof targetPkg === 'string' ? targetPkg : targetPkg.className,
                            label: `${dep.dependencyCount || '依赖'}`
                        });
                    });
                }
            });
        }

        // 如果没有数据，创建一个占位节点
        if (nodes.length === 0) {
            nodes.push({
                id: 'empty',
                label: '暂无包数据',
                classCount: 0,
                dependencyCount: 0,
                complexity: 0,
                style: {
                    fill: '#f0f0f0',
                    stroke: '#ccc'
                }
            });
        }

        console.log('包级图数据转换完成:', { nodeCount: nodes.length, edgeCount: edges.length });
        return { nodes, edges };
    }

    /**
     * 转换数据为类级图格式
     */
    convertToClassGraphData(analysisData) {
        const nodes = [];
        const edges = [];

        console.log('转换类级图数据，类数量:', analysisData.classes?.length || 0);

        // 添加类节点
        if (analysisData.classes && Array.isArray(analysisData.classes)) {
            analysisData.classes.forEach(cls => {
                const complexity = cls.metrics?.complexityScore || 0;
                nodes.push({
                    id: cls.id || cls.qualifiedName || 'unknown',
                    label: cls.name || 'Unknown Class',
                    type: cls.type?.toString() || 'CLASS',
                    complexity: complexity,
                    methodCount: cls.metrics?.methodCount || 0,
                    style: {
                        fill: this.getClassTypeColor(cls),
                        stroke: this.getComplexityBorderColor(complexity)
                    }
                });
            });
        }

        // 添加依赖边
        if (analysisData.classDependencies && Array.isArray(analysisData.classDependencies)) {
            analysisData.classDependencies.forEach(dep => {
                if (dep.dependencies && Array.isArray(dep.dependencies)) {
                    dep.dependencies.forEach(ref => {
                        edges.push({
                            source: dep.className || dep.classId,
                            target: ref.className || ref.qualifiedName || ref.classId,
                            label: ref.referenceType?.toString() || '依赖'
                        });
                    });
                }
            });
        }

        // 如果没有数据，创建一个占位节点
        if (nodes.length === 0) {
            nodes.push({
                id: 'empty',
                label: '暂无类数据',
                type: 'CLASS',
                complexity: 0,
                methodCount: 0,
                style: {
                    fill: '#f0f0f0',
                    stroke: '#ccc'
                }
            });
        }

        console.log('类级图数据转换完成:', { nodeCount: nodes.length, edgeCount: edges.length });
        return { nodes, edges };
    }

    /**
     * 计算包复杂度
     */
    calculatePackageComplexity(pkg, analysisData) {
        let totalComplexity = 0;
        let classCount = 0;

        analysisData.classes?.forEach(cls => {
            if (cls.packageId === pkg.id) {
                totalComplexity += cls.metrics?.complexityScore || 0;
                classCount++;
            }
        });

        return classCount > 0 ? Math.round(totalComplexity / classCount) : 0;
    }

    /**
     * 根据复杂度获取颜色
     */
    getComplexityColor(complexity) {
        if (complexity > 50) return '#FF4D4F'; // 高复杂度 - 红色
        if (complexity > 30) return '#FA8C16'; // 中高复杂度 - 橙色
        if (complexity > 15) return '#FAAD14'; // 中等复杂度 - 黄色
        return '#52C41A'; // 低复杂度 - 绿色
    }

    /**
     * 根据类类型获取颜色
     */
    getClassTypeColor(cls) {
        if (cls.isController) return '#FF7875';
        if (cls.isService) return '#69C0FF';
        if (cls.isRepository) return '#95DE64';
        if (cls.isPojo) return '#FFD666';
        return '#C6E5FF';
    }

    /**
     * 根据复杂度获取边框颜色
     */
    getComplexityBorderColor(complexity) {
        if (complexity > 70) return '#FF4D4F';
        if (complexity > 40) return '#FA8C16';
        return '#5B8FF9';
    }

    /**
     * 显示包详情
     */
    showPackageDetails(packageModel) {
        const detailsContainer = document.getElementById('package-details');
        if (!detailsContainer || !this.currentData) return;

        const pkgData = this.currentData.packages?.find(p => p.id === packageModel.id);
        if (!pkgData) return;

        const html = `
            <h5>${pkgData.name}</h5>
            <div class="detail-item">
                <strong>类数量:</strong> ${pkgData.classCount}
            </div>
            <div class="detail-item">
                <strong>层级:</strong> ${pkgData.level}
            </div>
            <div class="detail-item">
                <strong>依赖指标:</strong>
                <ul>
                    <li>传入依赖: ${pkgData.metrics?.fanIn || 0}</li>
                    <li>传出依赖: ${pkgData.metrics?.fanOut || 0}</li>
                    <li>不稳定性: ${(pkgData.metrics?.instability || 0).toFixed(2)}</li>
                </ul>
            </div>
        `;

        detailsContainer.innerHTML = html;
    }

    /**
     * 显示类详情
     */
    showClassDetails(classModel) {
        const detailsContainer = document.getElementById('class-details');
        if (!detailsContainer || !this.currentData) return;

        const classData = this.currentData.classes?.find(c => c.id === classModel.id);
        if (!classData) return;

        const metrics = classData.metrics;
        const html = `
            <h5>${classData.name}</h5>
            <div class="detail-item">
                <strong>类型:</strong> ${this.getClassTypeLabel(classData)}
            </div>
            <div class="detail-item">
                <strong>复杂度评分:</strong> ${metrics?.complexityScore || 0}
            </div>
            <div class="detail-item">
                <strong>基本信息:</strong>
                <ul>
                    <li>方法数: ${metrics?.methodCount || 0}</li>
                    <li>字段数: ${metrics?.fieldCount || 0}</li>
                    <li>代码行数: ${metrics?.linesOfCode || 0}</li>
                </ul>
            </div>
            <div class="detail-item">
                <strong>重构优先级:</strong>
                <span class="priority-${metrics?.refactoringPriority?.level || 'low'}">
                    ${metrics?.refactoringPriority?.level || 'LOW'}
                </span>
            </div>
        `;

        detailsContainer.innerHTML = html;
    }

    /**
     * 获取类类型标签
     */
    getClassTypeLabel(classData) {
        if (classData.isController) return 'Controller';
        if (classData.isService) return 'Service';
        if (classData.isRepository) return 'Repository';
        if (classData.isPojo) return 'POJO';
        return classData.type || 'Unknown';
    }

    /**
     * 更新方法分析视图（替换热力图）
     */
    updateMethodAnalysis() {
        if (!this.currentData) return;

        // 初始化方法分析控件
        this.setupMethodAnalysisControls();

        // 默认启动调用关系图模式
        this.initCallGraphMode();

        // 填充入口方法选择器
        this.populateEntryMethods();
    }

    /**
     * 设置方法分析控件
     */
    setupMethodAnalysisControls() {
        // 模式切换
        this.setupMethodAnalysisModeToggle();

        // 筛选控件
        this.setupMethodFilters();

        // 操作按钮
        this.setupMethodActions();
    }

    /**
     * 设置方法分析模式切换
     */
    setupMethodAnalysisModeToggle() {
        const callgraphMode = document.getElementById('callgraph-mode');
        const callchainMode = document.getElementById('callchain-mode');
        const callgraphContainer = document.getElementById('callgraph-container');
        const callchainContainer = document.getElementById('callchain-container');

        if (!callgraphMode || !callchainMode) return;

        callgraphMode.addEventListener('click', () => {
            callgraphMode.classList.add('active');
            callchainMode.classList.remove('active');
            if (callgraphContainer) callgraphContainer.style.display = 'block';
            if (callchainContainer) callchainContainer.style.display = 'none';
            this.initCallGraphMode();
        });

        callchainMode.addEventListener('click', () => {
            callchainMode.classList.add('active');
            callgraphMode.classList.remove('active');
            if (callgraphContainer) callgraphContainer.style.display = 'none';
            if (callchainContainer) callchainContainer.style.display = 'block';
            this.initCallChainMode();
        });
    }

    /**
     * 设置方法筛选器
     */
    setupMethodFilters() {
        const filters = ['call-type-filter', 'complexity-filter', 'call-count-filter'];
        filters.forEach(id => {
            const element = document.getElementById(id);
            if (element) {
                element.addEventListener('change', () => {
                    if (this.currentMethodMode === 'callgraph') {
                        this.refreshCallGraph();
                    }
                });
            }
        });
    }

    /**
     * 设置方法操作按钮
     */
    setupMethodActions() {
        // 导出图片
        const exportBtn = document.getElementById('export-graph');
        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                this.exportMethodGraph();
            });
        }

        // 重置视图
        const resetBtn = document.getElementById('reset-view');
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                this.resetMethodGraphView();
            });
        }

        // 方法详情面板关闭按钮
        const closeBtn = document.getElementById('close-details');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                this.hideMethodDetails();
            });
        }

        // 自动检测入口方法
        const autoDetectBtn = document.getElementById('auto-detect-entries');
        if (autoDetectBtn) {
            autoDetectBtn.addEventListener('click', () => {
                this.autoDetectEntryMethods();
            });
        }
    }

    /**
     * 更新场景选择器
     */
    updateSceneSelector() {
        const selector = document.getElementById('scene-select');
        if (!selector || !this.currentData) return;

        // 清空现有选项
        selector.innerHTML = '<option value="">选择业务场景...</option>';

        // 添加场景选项
        this.currentData.sceneDefinitions?.forEach(scene => {
            const option = document.createElement('option');
            option.value = scene.id;
            option.textContent = `${scene.name} (${scene.entryMethods?.length || 0} 入口)`;
            selector.appendChild(option);
        });
    }

    /**
     * 更新场景图
     */
    updateSceneGraph(sceneId) {
        if (!sceneId || !this.currentData || !this.graphs.scene) return;

        const scene = this.currentData.sceneDefinitions?.find(s => s.id === sceneId);
        if (!scene) return;

        const data = this.convertSceneToGraphData(scene);
        this.graphs.scene.data(data);
        this.graphs.scene.render();

        // 更新场景信息
        this.updateSceneInfo(scene);
    }

    /**
     * 转换场景为图数据
     */
    convertSceneToGraphData(scene) {
        const nodes = [];
        const edges = [];

        // 添加入口方法节点
        scene.entryMethods?.forEach((methodId, index) => {
            nodes.push({
                id: methodId,
                label: `入口 ${index + 1}`,
                type: 'entry',
                style: {
                    fill: '#52C41A',
                    stroke: '#389E0D'
                }
            });
        });

        // 这里应该根据实际的调用关系添加节点和边
        // 简化实现

        return { nodes, edges };
    }

    /**
     * 更新场景信息
     */
    updateSceneInfo(scene) {
        const infoContainer = document.getElementById('scene-info');
        if (!infoContainer) return;

        const html = `
            <h5>${scene.name}</h5>
            <div class="detail-item">
                <strong>描述:</strong> ${scene.description || '无描述'}
            </div>
            <div class="detail-item">
                <strong>类别:</strong> ${scene.category || 'Unknown'}
            </div>
            <div class="detail-item">
                <strong>覆盖范围:</strong>
                <ul>
                    <li>方法数: ${scene.coverage?.methodCount || 0}</li>
                    <li>类数: ${scene.coverage?.classCount || 0}</li>
                    <li>包数: ${scene.coverage?.packageCount || 0}</li>
                    <li>最大深度: ${scene.coverage?.maxDepth || 0}</li>
                </ul>
            </div>
        `;

        infoContainer.innerHTML = html;
    }

    /**
     * 生成统计信息
     */
    generateStatistics() {
        if (!this.currentData) return;

        const statsGrid = document.getElementById('stats-grid');
        if (!statsGrid) return;

        const stats = [
            {
                label: '总包数',
                value: this.currentData.metadata?.statistics?.totalPackages || 0,
                icon: 'fa-folder'
            },
            {
                label: '总类数',
                value: this.currentData.metadata?.statistics?.totalClasses || 0,
                icon: 'fa-cube'
            },
            {
                label: '总方法数',
                value: this.currentData.metadata?.statistics?.totalMethods || 0,
                icon: 'fa-code'
            },
            {
                label: '代码问题',
                value: this.currentData.codeSmells?.length || 0,
                icon: 'fa-exclamation-triangle'
            },
            {
                label: '高复杂度类',
                value: this.countHighComplexityClasses(),
                icon: 'fa-fire'
            },
            {
                label: '业务场景',
                value: this.currentData.sceneDefinitions?.length || 0,
                icon: 'fa-route'
            }
        ];

        let html = '';
        stats.forEach(stat => {
            html += `
                <div class="stat-card">
                    <div class="stat-value">${stat.value}</div>
                    <div class="stat-label">
                        <i class="fas ${stat.icon}"></i>
                        ${stat.label}
                    </div>
                </div>
            `;
        });

        statsGrid.innerHTML = html;

        // 生成POJO统计
        this.generatePojoStatistics();

        // 生成问题列表
        this.updateIssuesList('critical');
    }

    /**
     * 统计高复杂度类
     */
    countHighComplexityClasses() {
        if (!this.currentData.classes) return 0;

        return this.currentData.classes.filter(cls =>
            (cls.metrics?.complexityScore || 0) > 50
        ).length;
    }

    /**
     * 生成POJO统计
     */
    generatePojoStatistics() {
        const pojoStats = document.getElementById('pojo-stats');
        if (!pojoStats || !this.currentData.pojos) return;

        const stats = [
            {
                label: '总POJO数',
                value: this.currentData.pojos.length,
                icon: 'fa-database'
            },
            {
                label: 'DTO数量',
                value: this.currentData.pojos.filter(p => p.category === 'DTO').length,
                icon: 'fa-exchange-alt'
            },
            {
                label: 'Entity数量',
                value: this.currentData.pojos.filter(p => p.category === 'ENTITY').length,
                icon: 'fa-table'
            },
            {
                label: '跨边界使用',
                value: this.currentData.pojos.filter(p =>
                    p.crossBoundaryUsage?.some(usage => !usage.isExpected)
                ).length,
                icon: 'fa-exclamation-triangle'
            }
        ];

        let html = '';
        stats.forEach(stat => {
            html += `
                <div class="pojo-stat-card">
                    <div class="pojo-stat-value">${stat.value}</div>
                    <div class="pojo-stat-label">
                        <i class="fas ${stat.icon}"></i>
                        ${stat.label}
                    </div>
                </div>
            `;
        });

        pojoStats.innerHTML = html;

        // 生成POJO表格
        this.generatePojoTable();
    }

    /**
     * 生成POJO表格
     */
    generatePojoTable() {
        const tbody = document.getElementById('pojo-tbody');
        if (!tbody || !this.currentData.pojos) return;

        let html = '';
        this.currentData.pojos.forEach(pojo => {
            const hasCrossBoundaryIssues = pojo.crossBoundaryUsage?.some(usage => !usage.isExpected);
            const recommendation = hasCrossBoundaryIssues ? 'warning' : 'good';
            const recommendationText = hasCrossBoundaryIssues ? '需要重构' : '使用良好';

            html += `
                <tr>
                    <td>${pojo.name}</td>
                    <td><span class="pojo-type-badge">${pojo.category}</span></td>
                    <td>${pojo.usage?.usedByClassesCount || 0}</td>
                    <td>${hasCrossBoundaryIssues ? '是' : '否'}</td>
                    <td>
                        <span class="recommendation-badge recommendation-${recommendation}">
                            ${recommendationText}
                        </span>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
    }

    /**
     * 更新问题列表
     */
    updateIssuesList(severity) {
        const container = document.getElementById('issues-content');
        if (!container || !this.currentData.codeSmells) return;

        let filteredSmells = this.currentData.codeSmells;

        if (severity !== 'all') {
            filteredSmells = filteredSmells.filter(smell => {
                if (severity === 'critical') return smell.severity === 'CRITICAL' || smell.severity === 'HIGH';
                return smell.severity === severity.toUpperCase();
            });
        }

        // 按严重程度排序
        filteredSmells.sort((a, b) => {
            const severityOrder = { 'CRITICAL': 5, 'HIGH': 4, 'MEDIUM': 3, 'LOW': 2, 'INFO': 1 };
            return (severityOrder[b.severity] || 0) - (severityOrder[a.severity] || 0);
        });

        let html = '';
        if (filteredSmells.length === 0) {
            html = '<div class="issue-item"><p>没有发现该级别的问题。</p></div>';
        } else {
            filteredSmells.forEach(smell => {
                html += `
                    <div class="issue-item">
                        <div class="issue-header">
                            <span class="issue-type">${smell.type?.replace(/_/g, ' ')}</span>
                            <span class="issue-severity severity-${smell.severity?.toLowerCase()}">
                                ${smell.severity}
                            </span>
                        </div>
                        <div class="issue-location">
                            ${smell.className}${smell.methodName ? `.${smell.methodName}` : ''}
                            (${smell.location?.filePath || 'unknown'}:${smell.location?.lineNumber || 0})
                        </div>
                        <div class="issue-description">
                            ${smell.description}
                        </div>
                    </div>
                `;
            });
        }

        container.innerHTML = html;
    }

    /**
     * 更新当前图表布局
     */
    updateCurrentGraphLayout() {
        const activeTab = document.querySelector('.tab-pane.active');
        if (!activeTab) return;

        const tabId = activeTab.id;
        const graph = this.graphs[tabId?.replace('-view', '')];

        if (graph) {
            graph.updateLayout({
                type: this.currentLayout,
                preventOverlap: true,
                nodeSize: 40
            });
        }
    }

    /**
     * 更新当前图表筛选
     */
    updateCurrentGraphFiltering() {
        // 简化实现，应该根据筛选条件重新渲染图表
        this.updateCurrentGraphLayout();
    }

    /**
     * 缩放当前图表
     */
    zoomCurrentGraph(ratio) {
        const activeTab = document.querySelector('.tab-pane.active');
        if (!activeTab) return;

        const tabId = activeTab.id;
        const graph = this.graphs[tabId?.replace('-view', '')];

        if (graph) {
            const zoom = graph.getZoom();
            graph.zoomTo(zoom * ratio, graph.getGraphCenterPoint());
        }
    }

    /**
     * 适应当前图表视图
     */
    fitCurrentGraphView() {
        const activeTab = document.querySelector('.tab-pane.active');
        if (!activeTab) return;

        const tabId = activeTab.id;
        const graph = this.graphs[tabId?.replace('-view', '')];

        if (graph) {
            graph.fitView(20);
        }
    }

    /**
     * 刷新数据
     */
    refreshData() {
        // 显示加载遮罩
        this.showLoading();

        // 重新初始化
        setTimeout(() => {
            this.init();
        }, 100);
    }

    /**
     * 导出报告
     */
    exportReport() {
        if (!this.currentData) {
            alert('没有可导出的数据');
            return;
        }

        const dataStr = JSON.stringify(this.currentData, null, 2);
        const dataBlob = new Blob([dataStr], { type: 'application/json' });
        const url = URL.createObjectURL(dataBlob);

        const link = document.createElement('a');
        link.href = url;
        link.download = `dependency-analysis-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    /**
     * 切换全屏
     */
    toggleFullscreen() {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen();
        } else {
            document.exitFullscreen();
        }
    }

    /**
     * 显示加载遮罩
     */
    showLoading() {
        const overlay = document.getElementById('loading-overlay');
        if (overlay) {
            overlay.style.display = 'flex';
        }
    }

    /**
     * 隐藏加载遮罩
     */
    hideLoading() {
        const overlay = document.getElementById('loading-overlay');
        if (overlay) {
            overlay.style.display = 'none';
        }
    }

    // ========================= 方法调用分析功能 =========================

    /**
     * 转换方法调用图数据
     */
    convertToMethodCallGraphData() {
        if (!this.currentData.callGraph || !this.currentData.callGraph.edges) {
            return { nodes: [], edges: [] };
        }

        // 获取筛选条件
        const callCountThreshold = parseInt(document.getElementById('call-count-filter')?.value || '1');
        const complexityFilter = document.getElementById('complexity-filter')?.value;
        const callTypeFilter = document.getElementById('call-type-filter')?.value;

        // 聚合和筛选调用关系
        const nodeMap = new Map();
        const edges = [];
        const edgeIdMap = new Map(); // 用于跟踪重复的边ID
        let edgeIndex = 0; // 边的索引计数器

        this.currentData.callGraph.edges.forEach((edge, index) => {
            // 应用筛选条件
            if (edge.callContext?.callCount < callCountThreshold) return;
            if (callTypeFilter !== 'all' && !this.matchesCallType(edge.type, callTypeFilter)) return;

            // 构建节点和边
            this.buildMethodNode(nodeMap, edge.source, this.currentData);
            this.buildMethodNode(nodeMap, edge.target, this.currentData);

            // 生成唯一的边ID
            let edgeId = `${edge.source}-${edge.target}`;
            if (edgeIdMap.has(edgeId)) {
                // 如果已存在相同的边，添加索引确保唯一性
                const existingCount = edgeIdMap.get(edgeId);
                edgeId = `${edge.source}-${edge.target}-${existingCount}`;
                edgeIdMap.set(`${edge.source}-${edge.target}`, existingCount + 1);
            } else {
                edgeIdMap.set(edgeId, 1);
            }

            edges.push({
                id: edgeId,
                source: edge.source,
                target: edge.target,
                label: `${edge.callContext?.callCount || 0}次`,
                data: edge,
                style: {
                    stroke: this.getCallTypeColor(edge.type),
                    lineWidth: Math.min((edge.weight || 1) / 2, 8)
                }
            });

            edgeIndex++;
        });

        // 应用复杂度筛选
        const filteredNodes = Array.from(nodeMap.values()).filter(node => {
            if (complexityFilter === 'all') return true;
            return this.matchesComplexity(node.complexity || 0, complexityFilter);
        });

        // 构建最终的节点数据
        const finalNodes = filteredNodes.map(node => this.createMethodNode(node));

        return { nodes: finalNodes, edges };
    }

    /**
     * 构建方法节点数据
     */
    buildMethodNode(nodeMap, methodId, currentData) {
        if (nodeMap.has(methodId)) return;

        const method = currentData.methods?.find(m => m.id === methodId);
        const callCount = currentData.callGraph?.edges?.filter(e => e.source === methodId).length || 0;
        const calledByCount = currentData.callGraph?.edges?.filter(e => e.target === methodId).length || 0;

        const node = {
            id: methodId,
            name: method?.name || methodId,
            className: method?.className || '',
            complexity: method?.metrics?.complexityScore || 0,
            linesOfCode: method?.metrics?.linesOfCode || 0,
            parameterCount: method?.metrics?.parameterCount || 0,
            callCount: callCount,
            calledByCount: calledByCount
        };

        nodeMap.set(methodId, node);
    }

    /**
     * 创建方法节点
     */
    createMethodNode(node) {
        return {
            id: node.id,
            label: `${node.className}.${node.name}`,
            data: node,
            style: {
                fill: this.getNodeColorByComplexity(node.complexity),
                size: this.calculateNodeSize(node.callCount, node.complexity)
            }
        };
    }

    /**
     * 初始化调用关系图模式
     */
    initCallGraphMode() {
        this.currentMethodMode = 'callgraph';
        const container = document.getElementById('method-call-graph');
        if (!container) return;

        const graphData = this.convertToMethodCallGraphData();
        this.updateGraphStats(graphData);

        // 清理旧的图表实例
        if (this.methodCallGraph) {
            this.methodCallGraph.destroy();
        }

        this.methodCallGraph = new G6.Graph({
            container: container,
            modes: {
                default: ['drag-node', 'drag-canvas', 'zoom-canvas', 'click-select']
            },
            layout: {
                type: 'dagre',
                rankdir: 'TB',
                nodesep: 50,
                ranksep: 100
            },
            defaultNode: {
                type: 'circle',
                size: [60, 60],
                style: {
                    fill: '#1890ff',
                    stroke: '#096dd9',
                    lineWidth: 2
                },
                labelCfg: {
                    position: 'center',
                    style: {
                        fill: '#fff',
                        fontSize: 10
                    }
                }
            },
            defaultEdge: {
                type: 'polyline',
                style: {
                    stroke: '#e6e6e6',
                    lineWidth: 2,
                    endArrow: {
                        path: 'M 0,0 L 8,4 L 0,8',
                        fill: '#e6e6e6'
                    }
                }
            }
        });

        this.methodCallGraph.data(graphData);
        this.methodCallGraph.render();
        this.setupCallGraphEvents();
    }

    /**
     * 初始化调用链分析模式
     */
    initCallChainMode() {
        this.currentMethodMode = 'callchain';
        const container = document.getElementById('call-chain-graph');
        const entryMethod = document.getElementById('entry-method-select')?.value;

        if (!entryMethod) {
            this.showMessage('请选择入口方法查看调用链');
            return;
        }

        const chainData = this.buildCallChainData(entryMethod);
        this.updateChainStats(chainData);

        // 清理旧的图表实例
        if (this.callChainGraph) {
            this.callChainGraph.destroy();
        }

        this.callChainGraph = new G6.Graph({
            container: container,
            modes: {
                default: ['drag-canvas', 'zoom-canvas']
            },
            layout: {
                type: 'dagre',
                rankdir: 'TB'
            }
        });

        this.callChainGraph.data(chainData);
        this.callChainGraph.render();
        this.setupCallChainEvents(entryMethod);
        this.displayChainDetails(chainData);
    }

    /**
     * 构建调用链数据
     */
    buildCallChainData(entryMethodId) {
        const visited = new Set();
        const nodes = new Map();
        const edges = [];

        // 深度优先搜索构建调用链
        this.buildCallChainDFS(entryMethodId, visited, nodes, edges, 0, 10);

        // 转换为G6格式
        const g6Nodes = Array.from(nodes.values()).map(node => ({
            id: node.id,
            label: `${node.className}.${node.name}`,
            data: node,
            style: {
                fill: this.getNodeColorByDepth(node.depth),
                size: Math.max(30, Math.min(80, 30 + node.callCount * 5))
            }
        }));

        return { nodes: g6Nodes, edges };
    }

    /**
     * 构建调用链（深度优先搜索）
     */
    buildCallChainDFS(methodId, visited, nodes, edges, depth, maxDepth) {
        if (depth > maxDepth || visited.has(methodId)) return;

        visited.add(methodId);
        const method = this.currentData.methods?.find(m => m.id === methodId);
        if (!method) return;

        // 添加节点
        const callCount = this.currentData.callGraph?.edges?.filter(e => e.source === methodId).length || 0;
        const node = {
            id: methodId,
            name: method.name,
            className: method.className,
            complexity: method.metrics?.complexityScore || 0,
            callCount: callCount,
            depth: depth
        };
        nodes.set(methodId, node);

        // 处理调用关系
        this.currentData.callGraph?.edges?.forEach(edge => {
            if (edge.source === methodId && !visited.has(edge.target)) {
                edges.push({
                    id: `${edge.source}-${edge.target}`,
                    source: edge.source,
                    target: edge.target,
                    label: `${edge.callContext?.callCount || 1}次`,
                    style: {
                        stroke: '#1890ff',
                        lineWidth: Math.min((edge.callContext?.callCount || 1), 5)
                    }
                });

                // 递归处理被调用方法
                this.buildCallChainDFS(edge.target, visited, nodes, edges, depth + 1, maxDepth);
            }
        });
    }

    /**
     * 填充入口方法选择器
     */
    populateEntryMethods() {
        const selector = document.getElementById('entry-method-select');
        if (!selector) return;

        // 清空现有选项
        selector.innerHTML = '<option value="">选择入口方法</option>';

        // 添加业务入口方法
        const entryMethods = this.currentData.methods?.filter(m => m.isEntryPoint) || [];

        // 日志记录：记录找到的入口方法数量
        console.log(`Nekoama: 找到 ${entryMethods.length} 个入口方法`);

        entryMethods.forEach(method => {
            const option = document.createElement('option');
            option.value = method.id;
            option.textContent = `${method.className}.${method.name}`;
            selector.appendChild(option);
            console.log(`Nekoama: 入口方法 - ${method.className}.${method.name}`);
        });

        // 改进的降级策略：如果没有明确的入口方法
        if (entryMethods.length === 0) {
            console.warn('Nekoama: 未检测到明确的入口方法，启用降级策略');

            // 显示警告提示
            this.showFallbackWarning();

            // 使用更严格的过滤条件选择备选方法
            const fallbackMethods = this.selectFallbackMethods();

            if (fallbackMethods.length > 0) {
                console.log(`Nekoama: 降级策略选择了 ${fallbackMethods.length} 个备选方法`);

                // 添加分隔标记
                const separatorOption = document.createElement('option');
                separatorOption.value = "";
                separatorOption.textContent = "--- 备选方法（可能不准确）---";
                separatorOption.disabled = true;
                selector.appendChild(separatorOption);

                fallbackMethods.forEach(method => {
                    const option = document.createElement('option');
                    option.value = method.id;
                    option.textContent = `${method.className}.${method.name} (备选)`;
                    option.style.color = "#666";
                    selector.appendChild(option);
                });
            } else {
                // 如果连备选方法都没有，显示提示
                const noMethodsOption = document.createElement('option');
                noMethodsOption.value = "";
                noMethodsOption.textContent = "未找到合适的入口方法";
                noMethodsOption.disabled = true;
                selector.appendChild(noMethodsOption);
            }
        }
    }

    /**
     * 选择备选方法（使用更严格的条件）
     */
    selectFallbackMethods() {
        const allMethods = this.currentData.methods || [];

        // 过滤条件：优先选择可能是真正入口点的方法
        const potentialEntryMethods = allMethods.filter(method => {
            const className = method.className.toLowerCase();
            const methodName = method.name.toLowerCase();
            const isPublic = method.modifiers.includes('public');

            // 必须是public方法
            if (!isPublic) return false;

            // 排除明显不是入口点的方法
            if (methodName.startsWith('get') &&
                (methodName.includes('by') || methodName.includes('from'))) return false;
            if (methodName.startsWith('set')) return false;
            if (methodName.startsWith('is') && methodName.length > 2) return false;
            if (methodName.startsWith('has') && methodName.length > 3) return false;
            if (methodName.startsWith('find') && methodName.includes('by')) return false;

            // 优先选择包含以下特征的方法
            const score = [
                className.includes('controller') ? 3 : 0,
                className.includes('api') ? 3 : 0,
                className.includes('resource') ? 2 : 0,
                className.includes('handler') ? 2 : 0,
                className.includes('endpoint') ? 2 : 0,
                methodName.includes('request') ? 2 : 0,
                methodName.includes('response') ? 2 : 0,
                methodName.startsWith('handle') ? 2 : 0,
                method.parameters.length > 0 ? 1 : 0  // 有参数的方法更可能是入口点
            ].reduce((sum, val) => sum + val, 0);

            return score >= 2;
        });

        // 按分数排序并返回前10个
        return potentialEntryMethods
            .sort((a, b) => (b.metrics?.complexityScore || 0) - (a.metrics?.complexityScore || 0))
            .slice(0, 10);
    }

    /**
     * 显示降级策略警告提示
     */
    showFallbackWarning() {
        // 创建警告提示元素
        const warningDiv = document.createElement('div');
        warningDiv.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #ff9800;
            color: white;
            padding: 12px 16px;
            border-radius: 4px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.2);
            z-index: 10000;
            max-width: 300px;
            font-size: 14px;
            line-height: 1.4;
        `;

        warningDiv.innerHTML = `
            <strong>⚠️ 检测异常</strong><br>
            未找到有效的入口方法，显示备选方法。建议检查项目中的Controller注解或入口点配置。
            <button style="margin-top: 8px; padding: 4px 8px; background: white; color: #ff9800; border: none; border-radius: 3px; cursor: pointer;">知道了</button>
        `;

        // 添加关闭事件
        const button = warningDiv.querySelector('button');
        button.addEventListener('click', () => {
            document.body.removeChild(warningDiv);
        });

        // 自动关闭
        setTimeout(() => {
            if (document.body.contains(warningDiv)) {
                document.body.removeChild(warningDiv);
            }
        }, 10000);

        document.body.appendChild(warningDiv);
    }

    /**
     * 设置调用图事件
     */
    setupCallGraphEvents() {
        if (!this.methodCallGraph) return;

        // 节点点击事件 - 显示方法详情
        this.methodCallGraph.on('node:click', (evt) => {
            const node = evt.item;
            this.showMethodDetails(node.getModel());
        });

        // 边点击事件 - 高亮调用路径
        this.methodCallGraph.on('edge:click', (evt) => {
            const edge = evt.item;
            this.highlightCallPath(edge.getModel());
        });
    }

    /**
     * 设置调用链事件
     */
    setupCallChainEvents(entryMethodId) {
        if (!this.callChainGraph) return;

        // 节点点击事件 - 显示方法详情
        this.callChainGraph.on('node:click', (evt) => {
            const node = evt.item;
            this.showMethodDetails(node.getModel());
        });
    }

    /**
     * 显示方法详情
     */
    showMethodDetails(nodeModel) {
        const panel = document.getElementById('method-details-panel');
        const info = document.getElementById('method-info');
        const calls = document.getElementById('calls-list');
        const calledBy = document.getElementById('called-by-list');

        if (!panel || !info || !calls || !calledBy) return;

        const methodData = nodeModel.data;

        // 更新基本信息
        info.innerHTML = `
            <div class="info-item">
                <span class="info-label">方法名:</span>
                <span class="info-value">${methodData.className}.${methodData.name}</span>
            </div>
            <div class="info-item">
                <span class="info-label">复杂度:</span>
                <span class="info-value">${methodData.complexity}</span>
            </div>
            <div class="info-item">
                <span class="info-label">代码行数:</span>
                <span class="info-value">${methodData.linesOfCode}</span>
            </div>
            <div class="info-item">
                <span class="info-label">参数个数:</span>
                <span class="info-value">${methodData.parameterCount}</span>
            </div>
        `;

        // 更新调用关系
        const methodCalls = this.currentData.callGraph?.edges?.filter(e => e.source === methodData.id) || [];
        calls.innerHTML = methodCalls.length > 0 ? methodCalls.map(call => {
            const targetMethod = this.currentData.methods?.find(m => m.id === call.target);
            return `
                <div class="call-item" onclick="dependencyVisualizer.focusMethod('${call.target}')">
                    调用 ${targetMethod?.className || 'Unknown'}.${targetMethod?.name || 'Unknown'}
                    (${call.callContext?.callCount || 1}次)
                </div>
            `;
        }).join('') : '<div class="no-data">无调用关系</div>';

        // 更新被调用关系
        const methodCalledBy = this.currentData.callGraph?.edges?.filter(e => e.target === methodData.id) || [];
        calledBy.innerHTML = methodCalledBy.length > 0 ? methodCalledBy.map(call => {
            const sourceMethod = this.currentData.methods?.find(m => m.id === call.source);
            return `
                <div class="called-by-item" onclick="dependencyVisualizer.focusMethod('${call.source}')">
                    被 ${sourceMethod?.className || 'Unknown'}.${sourceMethod?.name || 'Unknown'}
                    调用 (${call.callContext?.callCount || 1}次)
                </div>
            `;
        }).join('') : '<div class="no-data">无被调用关系</div>';

        // 显示面板
        panel.classList.add('show');
    }

    /**
     * 隐藏方法详情
     */
    hideMethodDetails() {
        const panel = document.getElementById('method-details-panel');
        if (panel) {
            panel.classList.remove('show');
        }
    }

    /**
     * 自动检测入口方法
     */
    autoDetectEntryMethods() {
        // 基于调用次数和复杂度自动检测潜在的入口方法
        const methods = this.currentData.methods || [];
        const entryMethods = methods.filter(method => {
            const callCount = this.currentData.callGraph?.edges?.filter(e => e.source === method.id).length || 0;
            const calledByCount = this.currentData.callGraph?.edges?.filter(e => e.target === method.id).length || 0;
            const complexity = method.metrics?.complexityScore || 0;

            // 入口方法通常：被调用少、调用其他方法多、复杂度较高
            return calledByCount <= 2 && callCount >= 3 && complexity >= 10;
        });

        // 更新选择器
        const selector = document.getElementById('entry-method-select');
        if (selector && entryMethods.length > 0) {
            selector.innerHTML = '<option value="">选择入口方法</option>';
            entryMethods.forEach(method => {
                const option = document.createElement('option');
                option.value = method.id;
                option.textContent = `${method.className}.${method.name}`;
                selector.appendChild(option);
            });
        }
    }

    /**
     * 更新图表统计信息
     */
    updateGraphStats(graphData) {
        const nodeCount = document.getElementById('node-count');
        const edgeCount = document.getElementById('edge-count');
        const maxDepth = document.getElementById('max-depth');
        const complexMethods = document.getElementById('complex-methods-count');

        if (nodeCount) nodeCount.textContent = graphData.nodes.length;
        if (edgeCount) edgeCount.textContent = graphData.edges.length;
        if (maxDepth) maxDepth.textContent = this.calculateMaxDepth();
        if (complexMethods) {
            const count = graphData.nodes.filter(node =>
                (node.data?.complexity || 0) > 30
            ).length;
            complexMethods.textContent = count;
        }
    }

    /**
     * 更新调用链统计信息
     */
    updateChainStats(chainData) {
        const chainLength = document.getElementById('chain-length');
        const chainComplexity = document.getElementById('chain-complexity');
        const chainDescription = document.getElementById('chain-description');

        if (chainLength) chainLength.textContent = chainData.nodes.length;
        if (chainComplexity) {
            const totalComplexity = chainData.nodes.reduce((sum, node) =>
                sum + (node.data?.complexity || 0), 0
            );
            chainComplexity.textContent = totalComplexity;
        }
        if (chainDescription) {
            chainDescription.textContent = `显示了包含 ${chainData.nodes.length} 个方法的调用链`;
        }
    }

    /**
     * 显示调用链详情
     */
    displayChainDetails(chainData) {
        const steps = document.getElementById('chain-steps');
        if (!steps) return;

        const sortedNodes = chainData.nodes.sort((a, b) =>
            (a.data?.depth || 0) - (b.data?.depth || 0)
        );

        steps.innerHTML = sortedNodes.map((node, index) => `
            <div class="chain-step">
                <div class="chain-step-number">${index + 1}</div>
                <div class="chain-step-content">
                    <div class="chain-method">${node.data?.className}.${node.data?.name}</div>
                    <div class="chain-metrics">
                        复杂度: ${node.data?.complexity || 0} |
                        调用次数: ${node.data?.callCount || 0}
                    </div>
                </div>
            </div>
        `).join('');
    }

    // ========================= 辅助方法 =========================

    /**
     * 匹配调用类型
     */
    matchesCallType(edgeType, filterType) {
        const typeMap = {
            'METHOD_CALL': 'method_call',
            'CONSTRUCTOR_CALL': 'constructor_call',
            'SUPER_CALL': 'super_call'
        };
        return typeMap[edgeType] === filterType;
    }

    /**
     * 匹配复杂度
     */
    matchesComplexity(complexity, filterType) {
        switch (filterType) {
            case 'high': return complexity > 30;
            case 'medium': return complexity >= 15 && complexity <= 30;
            case 'low': return complexity < 15;
            default: return true;
        }
    }

    /**
     * 获取调用类型颜色
     */
    getCallTypeColor(callType) {
        const colorMap = {
            'METHOD_CALL': '#1890FF',      // 蓝色
            'CONSTRUCTOR_CALL': '#52C41A',  // 绿色
            'SUPER_CALL': '#FA8C16'         // 橙色
        };
        return colorMap[callType] || '#1890FF';
    }

    /**
     * 根据复杂度获取节点颜色
     */
    getNodeColorByComplexity(complexity) {
        if (complexity > 50) return '#FF4D4F';    // 红色
        if (complexity > 30) return '#FA8C16';    // 橙色
        if (complexity > 15) return '#FAAD14';    // 黄色
        if (complexity > 5) return '#52C41A';     // 绿色
        return '#1890FF';                         // 蓝色
    }

    /**
     * 根据深度获取节点颜色
     */
    getNodeColorByDepth(depth) {
        const colors = ['#1890FF', '#52C41A', '#FAAD14', '#FA8C16', '#FF4D4F'];
        return colors[Math.min(depth, colors.length - 1)];
    }

    /**
     * 计算节点大小
     */
    calculateNodeSize(callCount, complexity) {
        const baseSize = 30;
        const callBonus = Math.min(callCount, 50) / 2;
        const complexityBonus = Math.min(complexity / 20, 15);
        return baseSize + callBonus + complexityBonus;
    }

    /**
     * 计算最大深度
     */
    calculateMaxDepth() {
        let maxDepth = 0;
        this.currentData.callGraph?.edges?.forEach(edge => {
            maxDepth = Math.max(maxDepth, edge.depth || 0);
        });
        return maxDepth;
    }

    /**
     * 刷新调用图
     */
    refreshCallGraph() {
        if (this.currentMethodMode === 'callgraph') {
            this.initCallGraphMode();
        }
    }

    /**
     * 重置方法图视图
     */
    resetMethodGraphView() {
        if (this.currentMethodMode === 'callgraph' && this.methodCallGraph) {
            this.methodCallGraph.changeFitView();
        } else if (this.currentMethodMode === 'callchain' && this.callChainGraph) {
            this.callChainGraph.changeFitView();
        }
    }

    /**
     * 导出方法图
     */
    exportMethodGraph() {
        const graph = this.currentMethodMode === 'callgraph' ? this.methodCallGraph : this.callChainGraph;
        if (graph) {
            graph.downloadFullImage('method-graph', 'image/png');
        }
    }

    /**
     * 聚焦方法
     */
    focusMethod(methodId) {
        const graph = this.currentMethodMode === 'callgraph' ? this.methodCallGraph : this.callChainGraph;
        if (graph) {
            const node = graph.findById(methodId);
            if (node) {
                graph.focusItem(node);
                this.showMethodDetails(node.getModel());
            }
        }
    }

    /**
     * 高亮调用路径
     */
    highlightCallPath(edgeModel) {
        // 实现调用路径高亮逻辑
        // 这里可以添加更复杂的路径高亮算法
    }

    /**
     * 显示消息
     */
    showMessage(message) {
        // 简单的消息显示实现
        alert(message);
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    window.dependencyVisualizer = new DependencyVisualizer();
});

// 窗口大小变化时重新调整图表大小
window.addEventListener('resize', () => {
    if (window.dependencyVisualizer) {
        Object.values(window.dependencyVisualizer.graphs).forEach(graph => {
            if (graph) {
                const container = graph.getContainer();
                graph.changeSize(container.clientWidth, container.clientHeight);
            }
        });
    }
});