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
        this.currentMethodMode = 'callgraph'; // 默认调用关系图模式

        // 搜索相关属性
        this.currentSearchResults = [];
        this.currentSearchResultsIndex = -1;

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
            console.log('=== 开始加载分析数据 ===');
            const dataElement = document.getElementById('analysis-data');
            if (dataElement) {
                const rawData = JSON.parse(dataElement.textContent);
                console.log('原始JSON数据结构键值:', Object.keys(rawData));
                console.log('classGraph存在:', !!rawData.classGraph);
                console.log('methodCallGraph存在:', !!rawData.methodCallGraph);
                console.log('projectInfo存在:', !!rawData.projectInfo);

                // 兼容新旧数据结构，转换为标准格式
                this.currentData = this.normalizeDataStructure(rawData);
                console.log('标准化后数据结构键值:', Object.keys(this.currentData));
                console.log('最终数据 - classGraph节点数量:', this.currentData.classGraph?.nodes?.length || 0);
                console.log('最终数据 - methodCallGraph节点数量:', this.currentData.methodCallGraph?.nodes?.length || 0);

                // 验证数据完整性
                this.validateDataIntegrity();

                // 更新项目名称和路径
                const projectNameElement = document.getElementById('project-name');
                const projectPathElement = document.getElementById('project-path');

                if (projectNameElement && this.currentData.projectInfo) {
                    projectNameElement.textContent = this.currentData.projectInfo.name || 'Nekoama分析报告';
                }

                if (projectPathElement && this.currentData.projectInfo) {
                    projectPathElement.textContent = this.currentData.projectInfo.location || 'Unknown Location';
                }

                console.log('=== 分析数据加载完成 ===');
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
     * 标准化数据结构，兼容新旧版本
     */
    normalizeDataStructure(data) {
        // 如果已经是新版本格式，直接返回
        if (data.projectInfo && data.methodCallGraph) {
            return data;
        }

        // 兼容旧版本格式，转换为新版本格式
        console.log('检测到旧版本数据格式，进行转换...');

        const normalizedData = {
            projectInfo: data.metadata ? {
                name: data.metadata.projectName || 'Unknown Project',
                location: data.metadata.scope?.rootPackage || 'Unknown Location',
                totalFiles: 0,
                totalClasses: data.classes?.length || 0,
                totalMethods: data.methods?.length || 0,
                analysisTime: data.timestamp || Date.now()
            } : data.projectInfo || {},

            classGraph: {
                nodes: data.classes?.map(cls => ({
                    id: cls.qualifiedName || cls.id,
                    name: cls.name,
                    packagePath: cls.packageId || cls.packageName || '',
                    complexity: cls.metrics?.complexityScore || 0,
                    isController: cls.type === 'CLASS' && (cls.annotations?.some(ann =>
                        ann.includes('Controller') || ann.includes('RestController')
                    ) || false),
                    isService: cls.type === 'CLASS' && (cls.annotations?.some(ann =>
                        ann.includes('Service')
                    ) || false)
                })) || [],
                edges: data.classDependencies?.map(dep => ({
                    source: dep.className,
                    target: dep.dependencies?.[0]?.className || '',
                    type: 'ASSOCIATION',
                    strength: 1.0
                })) || []
            },

            methodCallGraph: {
                nodes: data.methods?.map(method => ({
                    id: `${method.className}.${method.name}`,
                    name: method.name,
                    className: method.className,
                    complexity: method.metrics?.cyclomaticComplexity || 0,
                    fanIn: method.metrics?.fanIn || 0,
                    fanOut: method.metrics?.fanOut || 0
                })) || [],
                edges: data.methodCalls?.map(call => ({
                    source: `${call.callerClass}.${call.callerMethod}`,
                    target: `${call.calleeClass}.${call.calleeMethod}`,
                    type: 'method_call',
                    callType: call.callType?.toString()?.toLowerCase() || 'direct'
                })) || []
            },

            entryPoints: data.businessEntryPoints || [],
            codeSmells: data.codeSmells || [],
            complexityMetrics: data.complexityMetrics || {},
            statistics: data.statistics || {}
        };

        // 为methodCallGraph添加methodCalls和methodCallTargets字段
        if (normalizedData.methodCallGraph.edges.length > 0) {
            normalizedData.methodCallGraph.methodCalls = normalizedData.methodCallGraph.edges
                .groupBy(edge => edge.source)
                .mapValues(edges => edges.map(edge => ({
                    toMethod: edge.target,
                    callType: edge.callType,
                    line: 0
                })));

            normalizedData.methodCallGraph.methodCallTargets = normalizedData.methodCallGraph.edges
                .groupBy(edge => edge.target)
                .mapValues(edges => edges.length);
        } else {
            normalizedData.methodCallGraph.methodCalls = {};
            normalizedData.methodCallGraph.methodCallTargets = {};
        }

        return normalizedData;
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
     * 数据完整性验证
     */
    validateDataIntegrity() {
        console.log('=== 开始数据完整性验证 ===');

        const requiredFields = ['projectInfo', 'classGraph', 'methodCallGraph'];
        const warnings = [];
        const errors = [];

        // 检查必需字段
        for (const field of requiredFields) {
            if (!this.currentData[field]) {
                errors.push(`缺少必要字段: ${field}`);
            } else {
                console.log(`✓ ${field} 字段存在`);
            }
        }

        // 检查图数据结构
        if (this.currentData.classGraph) {
            if (!this.currentData.classGraph.nodes || !Array.isArray(this.currentData.classGraph.nodes)) {
                errors.push('classGraph.nodes 字段缺失或格式错误');
            } else {
                console.log(`✓ classGraph 包含 ${this.currentData.classGraph.nodes.length} 个节点`);

                // 检查节点数据完整性
                const invalidNodes = this.currentData.classGraph.nodes.filter(node => !node.id || !node.name);
                if (invalidNodes.length > 0) {
                    warnings.push(`发现 ${invalidNodes.length} 个无效的类节点`);
                }
            }

            if (!this.currentData.classGraph.edges || !Array.isArray(this.currentData.classGraph.edges)) {
                warnings.push('classGraph.edges 字段缺失或格式错误');
            } else {
                console.log(`✓ classGraph 包含 ${this.currentData.classGraph.edges.length} 条边`);

                // 检查边数据完整性
                const invalidEdges = this.currentData.classGraph.edges.filter(edge => !edge.source || !edge.target);
                if (invalidEdges.length > 0) {
                    warnings.push(`发现 ${invalidEdges.length} 条无效的类边`);
                }
            }
        }

        if (this.currentData.methodCallGraph) {
            if (!this.currentData.methodCallGraph.nodes || !Array.isArray(this.currentData.methodCallGraph.nodes)) {
                errors.push('methodCallGraph.nodes 字段缺失或格式错误');
            } else {
                console.log(`✓ methodCallGraph 包含 ${this.currentData.methodCallGraph.nodes.length} 个节点`);
            }

            if (!this.currentData.methodCallGraph.edges || !Array.isArray(this.currentData.methodCallGraph.edges)) {
                warnings.push('methodCallGraph.edges 字段缺失或格式错误');
            } else {
                console.log(`✓ methodCallGraph 包含 ${this.currentData.methodCallGraph.edges.length} 条边`);
            }
        }

        // 输出验证结果
        if (errors.length > 0) {
            console.error('❌ 数据完整性验证失败:', errors);
            this.showDataError('数据完整性验证失败: ' + errors.join(', '));
        } else {
            console.log('✅ 数据完整性验证通过');
        }

        if (warnings.length > 0) {
            console.warn('⚠️ 数据完整性警告:', warnings);
        }

        console.log('=== 数据完整性验证完成 ===');

        return errors.length === 0;
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

        // 初始化搜索功能
        this.initSearchFunctionality();

        // 方法模式切换
        this.setupMethodModeSwitching();

        // 清空搜索按钮
        document.getElementById('clear-search')?.addEventListener('click', () => {
            this.clearSearch();
        });

        // 分析调用链按钮
        document.getElementById('analyze-callchain')?.addEventListener('click', () => {
            const entryMethodSelect = document.getElementById('entry-method-select');
            const searchInput = document.getElementById('method-search-input');

            let selectedMethod = null;

            // 优先使用搜索框输入的方法
            if (searchInput && searchInput.value.trim()) {
                const searchResults = this.searchMethods(searchInput.value.trim());
                if (searchResults.length > 0) {
                    selectedMethod = searchResults[0];
                }
            }

            // 如果搜索框没有选择，使用下拉框选择的方法
            if (!selectedMethod && entryMethodSelect && entryMethodSelect.value) {
                selectedMethod = { id: entryMethodSelect.value };
            }

            if (selectedMethod) {
                this.analyzeCallChain(selectedMethod.id);
            } else {
                this.showMessage('请选择入口方法或输入搜索关键词');
            }
        });

        // 方法详情面板关闭按钮
        const closeBtn = document.getElementById('close-details');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                this.hideMethodDetails();
            });
        }
    }

    /**
     * 设置方法模式切换
     */
    setupMethodModeSwitching() {
        const callgraphModeBtn = document.getElementById('callgraph-mode');
        const callchainModeBtn = document.getElementById('callchain-mode');

        if (callgraphModeBtn && callchainModeBtn) {
            callgraphModeBtn.addEventListener('click', () => {
                this.switchMethodMode('callgraph');
            });

            callchainModeBtn.addEventListener('click', () => {
                this.switchMethodMode('callchain');
            });
        }
    }

    /**
     * 切换方法分析模式
     */
    switchMethodMode(mode) {
        const callgraphModeBtn = document.getElementById('callgraph-mode');
        const callchainModeBtn = document.getElementById('callchain-mode');
        const callgraphControls = document.getElementById('callgraph-controls');
        const methodControls = document.getElementById('method-controls');
        const callgraphContainer = document.getElementById('callgraph-container');
        const callchainContainer = document.getElementById('callchain-container');

        // 更新按钮状态
        if (callgraphModeBtn && callchainModeBtn) {
            if (mode === 'callgraph') {
                callgraphModeBtn.classList.add('active', 'btn-primary');
                callgraphModeBtn.classList.remove('btn-secondary');
                callchainModeBtn.classList.remove('active', 'btn-primary');
                callchainModeBtn.classList.add('btn-secondary');
            } else {
                callchainModeBtn.classList.add('active', 'btn-primary');
                callchainModeBtn.classList.remove('btn-secondary');
                callgraphModeBtn.classList.remove('active', 'btn-primary');
                callgraphModeBtn.classList.add('btn-secondary');
            }
        }

        // 方法分析控件在两种模式下都显示
        if (methodControls) {
            methodControls.style.display = 'flex';
        }

        // 切换图表容器显示
        if (callgraphContainer && callchainContainer) {
            if (mode === 'callgraph') {
                callgraphContainer.style.display = 'block';
                callchainContainer.style.display = 'none';
            } else {
                callgraphContainer.style.display = 'none';
                callchainContainer.style.display = 'block';
            }
        }

        // 切换统计信息显示
        document.querySelectorAll('.callgraph-mode-controls').forEach(el => {
            el.style.display = mode === 'callgraph' ? 'block' : 'none';
        });
        document.querySelectorAll('.callchain-mode-controls').forEach(el => {
            el.style.display = mode === 'callchain' ? 'none' : 'block';
        });

        // 更新当前模式
        this.currentMethodMode = mode;

        // 初始化对应的图表
        if (mode === 'callgraph') {
            this.initCallGraphMode();
        } else {
            this.initCallChainMode();
        }
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
                type: 'dagre',
                rankdir: 'TB',  // 从上到下的布局
                nodesep: 50,    // 节点间水平间距
                ranksep: 100,   // 层级间垂直间距
                controlPoints: true
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

        const renderStartTime = performance.now();
        console.log('=== 开始更新包级依赖图 ===', `[${renderStartTime.toFixed(2)}ms]`);

        const dataStartTime = performance.now();
        const data = this.convertToPackageGraphData(this.currentData);
        const dataEndTime = performance.now();
        console.log(`包级图数据转换完成: ${data.nodes.length} 个节点, ${data.edges.length} 条边`,
                    `[${(dataEndTime - dataStartTime).toFixed(2)}ms]`);

        if (data.nodes.length === 0) {
            console.warn('包级图没有节点数据，图形将显示为空');
        }

        const renderStartTime2 = performance.now();
        this.graphs.package.data(data);
        this.graphs.package.render();
        const renderEndTime = performance.now();

        const totalRenderTime = renderEndTime - renderStartTime;
        const graphRenderTime = renderEndTime - renderStartTime2;

        console.log(`📊 包级图渲染完成:`,
                   `- 数据转换: ${(dataEndTime - dataStartTime).toFixed(2)}ms`,
                   `- 图形渲染: ${graphRenderTime.toFixed(2)}ms`,
                   `- 总耗时: ${totalRenderTime.toFixed(2)}ms`);
        console.log('=== 包级依赖图更新完成 ===');
    }

    /**
     * 更新类级关系图
     */
    updateClassGraph() {
        if (!this.currentData || !this.graphs.class) return;

        const renderStartTime = performance.now();
        console.log('=== 开始更新类级关系图 ===', `[${renderStartTime.toFixed(2)}ms]`);

        const dataStartTime = performance.now();
        const data = this.convertToClassGraphData(this.currentData);
        const dataEndTime = performance.now();
        console.log(`类级图数据转换完成: ${data.nodes.length} 个节点, ${data.edges.length} 条边`,
                    `[${(dataEndTime - dataStartTime).toFixed(2)}ms]`);

        if (data.nodes.length === 0) {
            console.warn('类级图没有节点数据，图形将显示为空');
        }

        const renderStartTime2 = performance.now();
        this.graphs.class.data(data);
        this.graphs.class.render();
        const renderEndTime = performance.now();

        const totalRenderTime = renderEndTime - renderStartTime;
        const graphRenderTime = renderEndTime - renderStartTime2;

        console.log(`📊 类级图渲染完成:`,
                   `- 数据转换: ${(dataEndTime - dataStartTime).toFixed(2)}ms`,
                   `- 图形渲染: ${graphRenderTime.toFixed(2)}ms`,
                   `- 总耗时: ${totalRenderTime.toFixed(2)}ms`);
        console.log('=== 类级关系图更新完成 ===');
    }

    /**
     * 转换数据为包级图格式
     */
    convertToPackageGraphData(analysisData) {
        const nodes = [];
        const edges = [];

        console.log('转换包级图数据，类数量:', analysisData.classGraph?.nodes?.length || 0);

        // 从类节点聚合包级别数据
        if (analysisData.classGraph && analysisData.classGraph.nodes) {
            const packageMap = new Map();

            // 聚合包数据
            analysisData.classGraph.nodes.forEach(cls => {
                const packageName = cls.packagePath || 'default';
                if (!packageMap.has(packageName)) {
                    packageMap.set(packageName, {
                        id: packageName,
                        name: packageName === 'default' ? '默认包' : packageName,
                        classes: [],
                        totalComplexity: 0,
                        classCount: 0
                    });
                }

                const pkg = packageMap.get(packageName);
                pkg.classes.push(cls);
                pkg.totalComplexity += cls.complexity || 0;
                pkg.classCount++;
            });

            // 创建包节点
            packageMap.forEach(pkg => {
                nodes.push({
                    id: pkg.id,
                    label: pkg.name,
                    classCount: pkg.classCount,
                    dependencyCount: 0, // 将在后面计算
                    complexity: pkg.totalComplexity,
                    style: {
                        fill: this.getPackageColor(pkg),
                        stroke: '#666'
                    }
                });
            });
        }

        // 创建包间依赖关系
        if (analysisData.classGraph && analysisData.classGraph.edges) {
            const packageEdges = new Set();
            analysisData.classGraph.edges.forEach(edge => {
                const sourceClass = analysisData.classGraph.nodes.find(cls => cls.id === edge.source);
                const targetClass = analysisData.classGraph.nodes.find(cls => cls.id === edge.target);

                if (sourceClass && targetClass) {
                    const sourcePackage = sourceClass.packagePath || 'default';
                    const targetPackage = targetClass.packagePath || 'default';

                    if (sourcePackage !== targetPackage) {
                        const edgeKey = `${sourcePackage}-${targetPackage}`;
                        if (!packageEdges.has(edgeKey)) {
                            packageEdges.add(edgeKey);
                            edges.push({
                                source: sourcePackage,
                                target: targetPackage,
                                type: 'PACKAGE_DEPENDENCY',
                                label: '包依赖'
                            });
                        }
                    }
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

        console.log('转换类级图数据，类数量:', analysisData.classGraph?.nodes?.length || 0);

        // 修复：查找正确的数据路径
        if (analysisData.classGraph && analysisData.classGraph.nodes) {
            analysisData.classGraph.nodes.forEach(cls => {
                const complexity = cls.complexity || 0;
                nodes.push({
                    id: cls.id,
                    label: cls.name,
                    type: cls.type || 'CLASS',
                    complexity: complexity,
                    methodCount: 0, // 当前数据结构中没有方法数量信息
                    packagePath: cls.packagePath,
                    isController: cls.isController || false,
                    isService: cls.isService || false,
                    style: {
                        fill: this.getClassTypeColor(cls),
                        stroke: this.getComplexityBorderColor(complexity)
                    }
                });
            });
        }

        // 修复：查找正确的边数据路径，并进行数据验证
        if (analysisData.classGraph && analysisData.classGraph.edges) {
            console.log('开始处理类级边数据，原始边数量:', analysisData.classGraph.edges.length);

            // 创建节点ID集合用于验证
            const nodeIds = new Set(nodes.map(node => node.id));
            let validEdges = 0;
            let invalidEdges = 0;

            analysisData.classGraph.edges.forEach((dep, index) => {
                // 验证边的source和target是否存在
                if (!dep.source || !dep.target) {
                    console.warn(`边数据缺少source或target [索引${index}]:`, dep);
                    invalidEdges++;
                    return;
                }

                // 检查source节点是否存在
                if (!nodeIds.has(dep.source)) {
                    console.warn(`边的source节点不存在 [索引${index}]: ${dep.source}`, dep);
                    invalidEdges++;
                    return;
                }

                // 检查target节点是否存在
                if (!nodeIds.has(dep.target)) {
                    console.warn(`边的target节点不存在 [索引${index}]: ${dep.target}`, dep);
                    invalidEdges++;
                    return;
                }

                // 边数据有效，添加到结果中
                edges.push({
                    source: dep.source,
                    target: dep.target,
                    type: dep.type || 'ASSOCIATION',
                    label: dep.type || '依赖'
                });
                validEdges++;
            });

            console.log(`类级边数据处理完成: 有效边 ${validEdges} 条, 无效边 ${invalidEdges} 条`);
        }

        // 最终数据完整性验证
        console.log(`类级图数据转换完成:`, {
            nodes: nodes.length,
            edges: edges.length,
            nodeIds: nodes.map(n => n.id).slice(0, 5), // 显示前5个节点ID用于调试
            edgeSamples: edges.slice(0, 3) // 显示前3条边用于调试
        });

        // 确保边都指向存在的节点
        const finalNodeIds = new Set(nodes.map(n => n.id));
        const finalValidEdges = edges.filter(edge => {
            if (!finalNodeIds.has(edge.source) || !finalNodeIds.has(edge.target)) {
                console.warn('最终验证发现无效边:', edge);
                return false;
            }
            return true;
        });

        console.log(`最终验证结果: 有效边 ${finalValidEdges.length} 条，总边 ${edges.length} 条`);

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

        console.log('类级图数据转换完成:', { nodeCount: nodes.length, edgeCount: finalValidEdges.length });
        return { nodes, edges: finalValidEdges };
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
     * 根据包特征获取包颜色
     */
    getPackageColor(pkg) {
        // 根据包的复杂度和类型返回不同颜色
        const complexity = pkg.totalComplexity || 0;
        const classCount = pkg.classCount || 0;

        // 高复杂度包使用红色系
        if (complexity > 100 || classCount > 20) {
            return '#FF7875';
        }
        // 中等复杂度包使用橙色系
        if (complexity > 50 || classCount > 10) {
            return '#FFA940';
        }
        // 低复杂度包使用蓝色系
        if (complexity > 20 || classCount > 5) {
            return '#69C0FF';
        }
        // 简单包使用绿色系
        return '#95DE64';
    }

    /**
     * 根据方法复杂度获取方法颜色
     */
    getMethodComplexityColor(complexity) {
        if (complexity > 30) return '#FF4D4F'; // 高复杂度 - 红色
        if (complexity > 20) return '#FA8C16'; // 中高复杂度 - 橙色
        if (complexity > 10) return '#FAAD14'; // 中等复杂度 - 金色
        if (complexity > 5) return '#52C41A';  // 低复杂度 - 绿色
        return '#1890FF'; // 很低复杂度 - 蓝色
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
        console.log('=== 显示类详情 ===', classModel);
        const detailsContainer = document.getElementById('class-details');
        if (!detailsContainer || !this.currentData) {
            console.error('类详情容器或数据未找到');
            return;
        }

        // 修复：从正确的数据路径查找类数据
        const classData = this.currentData.classGraph?.nodes?.find(c => c.id === classModel.id);
        if (!classData) {
            console.error('未找到类数据:', classModel.id);
            return;
        }

        console.log('找到类数据:', classData);

        // 使用新的数据结构
        const html = `
            <h5>${classData.name}</h5>
            <div class="detail-item">
                <strong>全限定名:</strong> ${classData.id}
            </div>
            <div class="detail-item">
                <strong>包路径:</strong> ${classData.packagePath || 'default'}
            </div>
            <div class="detail-item">
                <strong>复杂度:</strong>
                <span class="complexity-${classData.complexity > 30 ? 'high' : classData.complexity > 15 ? 'medium' : 'low'}">
                    ${classData.complexity || 0}
                </span>
            </div>
            <div class="detail-item">
                <strong>类型:</strong> ${this.getClassTypeLabel(classData)}
            </div>
            <div class="detail-item">
                <strong>基本信息:</strong>
                <ul>
                    <li>方法数: ${classData.methods?.length || 0}</li>
                    <li>是否为Controller: ${classData.isController ? '是' : '否'}</li>
                    <li>是否为Service: ${classData.isService ? '是' : '否'}</li>
                </ul>
            </div>
        `;

        detailsContainer.innerHTML = html;
        console.log('类详情已更新到容器');
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

        // 确保方法分析控件始终显示
        const methodControls = document.getElementById('method-controls');
        if (methodControls) {
            methodControls.style.display = 'flex';
        }

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
                label: '高复杂度',
                value: this.currentData.metadata?.statistics?.highComplexityMethods || 0,
                icon: 'fa-exclamation-triangle'
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
                // 解析element字段获取类名和方法名
                const element = smell.element || 'Unknown Element';
                const parts = element.split('.');
                const className = parts.length > 1 ? parts[parts.length - 2] : parts[0] || 'Unknown';
                const methodName = parts.length > 1 ? parts[parts.length - 1] : '';

                html += `
                    <div class="issue-item">
                        <div class="issue-header">
                            <span class="issue-type">${smell.type?.replace(/_/g, ' ')}</span>
                            <span class="issue-severity severity-${smell.severity?.toLowerCase()}">
                                ${smell.severity}
                            </span>
                        </div>
                        <div class="issue-location">
                            ${className}${methodName ? `.${methodName}` : ''}
                            (unknown:0)
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
        console.log('转换方法调用图数据，方法数量:', this.currentData.methodCallGraph?.nodes?.length || 0);

        // 修复：查找正确的数据路径
        if (!this.currentData.methodCallGraph || !this.currentData.methodCallGraph.edges) {
            console.warn('缺少方法调用图数据');
            return { nodes: [], edges: [] };
        }

        // 获取筛选条件
        const callCountThreshold = parseInt(document.getElementById('call-count-filter')?.value || '1');
        const complexityFilter = document.getElementById('complexity-filter')?.value;
        const callTypeFilter = document.getElementById('call-type-filter')?.value;

        // 直接从methodCallGraph获取数据
        const nodes = [];
        const edges = [];

        // 添加方法节点
        if (this.currentData.methodCallGraph.nodes) {
            this.currentData.methodCallGraph.nodes.forEach(method => {
                nodes.push({
                    id: method.id,
                    label: method.name,
                    type: 'METHOD',
                    className: method.className,
                    complexity: method.complexity || 0,
                    fanIn: method.fanIn || 0,
                    fanOut: method.fanOut || 0,
                    style: {
                        fill: this.getMethodComplexityColor(method.complexity),
                        stroke: '#666'
                    }
                });
            });
        }

        // 添加方法调用边，并进行数据验证
        if (this.currentData.methodCallGraph.edges) {
            console.log('开始处理方法调用边数据，原始边数量:', this.currentData.methodCallGraph.edges.length);

            const nodeIds = new Set(nodes.map(node => node.id));
            let validEdges = 0;
            let invalidEdges = 0;

            this.currentData.methodCallGraph.edges.forEach((call, index) => {
                // 验证边的source和target
                if (!call.source || !call.target) {
                    console.warn(`方法调用边数据缺少source或target [索引${index}]:`, call);
                    invalidEdges++;
                    return;
                }

                // 检查source节点是否存在
                if (!nodeIds.has(call.source)) {
                    console.warn(`方法调用边的source节点不存在 [索引${index}]: ${call.source}`, call);
                    invalidEdges++;
                    return;
                }

                // 检查target节点是否存在
                if (!nodeIds.has(call.target)) {
                    console.warn(`方法调用边的target节点不存在 [索引${index}]: ${call.target}`, call);
                    invalidEdges++;
                    return;
                }

                // 边数据有效
                edges.push({
                    source: call.source,
                    target: call.target,
                    type: call.type || 'method_call',
                    label: '调用'
                });
                validEdges++;
            });

            console.log(`方法调用边数据处理完成: 有效边 ${validEdges} 条, 无效边 ${invalidEdges} 条`);
        }

        console.log('方法调用图数据转换完成:', { nodeCount: nodes.length, edgeCount: edges.length });
        return { nodes, edges };
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
        const renderStartTime = performance.now();
        console.log('=== 开始初始化调用关系图模式 ===', `[${renderStartTime.toFixed(2)}ms]`);
        this.currentMethodMode = 'callgraph';
        const container = document.getElementById('method-call-graph');
        if (!container) return;

        const dataStartTime = performance.now();
        const graphData = this.convertToMethodCallGraphData();
        const dataEndTime = performance.now();
        console.log(`方法调用图数据转换完成: ${graphData.nodes.length} 个节点, ${graphData.edges.length} 条边`,
                    `[${(dataEndTime - dataStartTime).toFixed(2)}ms]`);

        if (graphData.nodes.length === 0) {
            console.warn('方法调用图没有节点数据，图形将显示为空');
        }

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

        const renderStartTime2 = performance.now();
        this.methodCallGraph.data(graphData);
        this.methodCallGraph.render();
        this.setupCallGraphEvents();
        const renderEndTime = performance.now();

        const totalRenderTime = renderEndTime - renderStartTime;
        const graphRenderTime = renderEndTime - renderStartTime2;

        console.log(`📊 方法调用关系图渲染完成:`,
                   `- 数据转换: ${(dataEndTime - dataStartTime).toFixed(2)}ms`,
                   `- 图形渲染: ${graphRenderTime.toFixed(2)}ms`,
                   `- 总耗时: ${totalRenderTime.toFixed(2)}ms`);
        console.log('=== 调用关系图模式初始化完成 ===');
    }

    /**
     * 初始化调用链分析模式
     */
    initCallChainMode() {
        const renderStartTime = performance.now();
        console.log('=== 开始初始化调用链分析模式 ===', `[${renderStartTime.toFixed(2)}ms]`);
        this.currentMethodMode = 'callchain';
        const container = document.getElementById('call-chain-graph');
        const entryMethod = document.getElementById('entry-method-select')?.value;

        console.log('调用链容器:', !!container);
        console.log('选择的入口方法:', entryMethod);

        if (!entryMethod) {
            this.showMessage('请选择入口方法查看调用链');
            return;
        }

        const dataStartTime = performance.now();
        const chainData = this.buildCallChainData(entryMethod);
        const dataEndTime = performance.now();
        console.log(`调用链数据构建完成: ${chainData.nodes.length} 个节点, ${chainData.edges.length} 条边`,
                    `[${(dataEndTime - dataStartTime).toFixed(2)}ms]`);
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
            },
            defaultNode: {
                type: 'circle',
                size: [60, 60],
                style: {
                    fill: '#1890ff',
                    stroke: '#1890ff',
                    lineWidth: 2
                },
                labelCfg: {
                    style: {
                        fill: '#fff',
                        fontSize: 12
                    }
                }
            },
            defaultEdge: {
                type: 'polyline',
                style: {
                    stroke: '#1890ff',
                    lineWidth: 2,
                    endArrow: {
                        path: G6.Arrow.triangle(10, 12, 25),
                        d: 25,
                        fill: '#1890ff'
                    }
                },
                labelCfg: {
                    style: {
                        fill: '#666',
                        fontSize: 10,
                        background: {
                            fill: '#fff',
                            stroke: '#999',
                            padding: [2, 4, 2, 4],
                            radius: 4
                        }
                    }
                }
            }
        });

        const renderStartTime2 = performance.now();
        this.callChainGraph.data(chainData);
        this.callChainGraph.render();
        this.setupCallChainEvents(entryMethod);
        const renderEndTime = performance.now();

        const totalRenderTime = renderEndTime - renderStartTime;
        const graphRenderTime = renderEndTime - renderStartTime2;

        console.log(`📊 调用链分析图渲染完成:`,
                   `- 数据构建: ${(dataEndTime - dataStartTime).toFixed(2)}ms`,
                   `- 图形渲染: ${graphRenderTime.toFixed(2)}ms`,
                   `- 总耗时: ${totalRenderTime.toFixed(2)}ms`);
        console.log('=== 调用链分析模式初始化完成 ===');
    }

    /**
     * 构建调用链数据
     */
    buildCallChainData(entryMethodId) {
        console.log('=== 开始构建调用链数据 ===');
        console.log('入口方法ID:', entryMethodId);
        console.log('methodCallGraph数据结构检查:');
        console.log('- 节点数量:', this.currentData.methodCallGraph?.nodes?.length || 0);
        console.log('- 边数量:', this.currentData.methodCallGraph?.edges?.length || 0);

        if (!this.currentData.methodCallGraph?.nodes?.length || !this.currentData.methodCallGraph?.edges?.length) {
            console.error('methodCallGraph数据为空或格式错误');
            return { nodes: [], edges: [] };
        }

        // 检查入口方法是否存在
        const entryMethod = this.currentData.methodCallGraph.nodes.find(m => m.id === entryMethodId);
        if (!entryMethod) {
            console.error('入口方法不存在:', entryMethodId);
            console.log('可用方法ID前10个:', this.currentData.methodCallGraph.nodes.slice(0, 10).map(n => n.id));
            return { nodes: [], edges: [] };
        }

        console.log('找到入口方法:', entryMethod.className, entryMethod.name);

        const visited = new Set();
        const nodes = new Map();
        const edges = [];

        // 深度优先搜索构建调用链
        console.log('开始深度优先搜索...');
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
        console.log(`[${depth}] DFS调用: ${methodId}, 已访问: ${Array.from(visited).join(', ')}`);

        if (depth > maxDepth) {
            console.log(`[${depth}] 达到最大深度限制: ${maxDepth}`);
            return;
        }

        if (visited.has(methodId)) {
            console.log(`[${depth}] 方法已访问，跳过循环依赖: ${methodId}`);
            return;
        }

        visited.add(methodId);

        // 修复：从methodCallGraph.nodes中查找方法
        const method = this.currentData.methodCallGraph?.nodes?.find(m => m.id === methodId);
        if (!method) {
            console.error(`[${depth}] 未找到方法节点: ${methodId}`);
            console.error(`[${depth}] 可用的节点ID前5个:`, this.currentData.methodCallGraph?.nodes?.slice(0, 5).map(n => n.id));
            return;
        }

        console.log(`[${depth}] 找到方法: ${method.className}.${method.name}`);

        // 添加节点 - 修复数据路径
        const callCount = this.currentData.methodCallGraph?.edges?.filter(e => e.source === methodId).length || 0;
        const node = {
            id: methodId,
            name: method.name,
            className: method.className,
            complexity: method.complexity || 0,
            callCount: callCount,
            depth: depth
        };
        nodes.set(methodId, node);

        // 处理调用关系 - 修复数据路径
        const outgoingCalls = this.currentData.methodCallGraph?.edges?.filter(e => e.source === methodId) || [];
        console.log(`[${depth}] 找到 ${outgoingCalls.length} 个出边:`, outgoingCalls.map(e => ({from: e.source, to: e.target})));

        outgoingCalls.forEach((edge, index) => {
            if (!visited.has(edge.target)) {
                console.log(`[${depth}] 添加边: ${edge.source} → ${edge.target}`);
                edges.push({
                    id: `${edge.source}-${edge.target}`,
                    source: edge.source,
                    target: edge.target,
                    label: `调用`,
                    style: {
                        stroke: '#1890ff',
                        lineWidth: 2
                    }
                });

                // 递归处理被调用方法
                console.log(`[${depth}] 递归调用目标方法: ${edge.target}`);
                this.buildCallChainDFS(edge.target, visited, nodes, edges, depth + 1, maxDepth);
            } else {
                console.log(`[${depth}] 目标方法已访问，跳过: ${edge.target}`);
            }
        });

        console.log(`[${depth}] ${methodId} 处理完成`);
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
        console.log('=== 显示方法详情 ===', nodeModel);

        const panel = document.getElementById('method-details-panel');
        const info = document.getElementById('method-info');
        const calls = document.getElementById('calls-list');
        const calledBy = document.getElementById('called-by-list');

        if (!panel || !info || !calls || !calledBy) {
            console.error('方法详情面板元素未找到');
            return;
        }

        // 修复：从新数据结构获取方法信息
        const methodId = nodeModel.id;
        const methodData = this.currentData.methodCallGraph?.nodes?.find(m => m.id === methodId);

        if (!methodData) {
            console.error('未找到方法数据:', methodId);
            return;
        }

        console.log('找到方法数据:', methodData);

        // 更新基本信息
        info.innerHTML = `
            <div class="info-item">
                <span class="info-label">方法名:</span>
                <span class="info-value">${methodData.className}.${methodData.name}</span>
            </div>
            <div class="info-item">
                <span class="info-label">复杂度:</span>
                <span class="info-value complexity-${methodData.complexity > 20 ? 'high' : methodData.complexity > 10 ? 'medium' : 'low'}">${methodData.complexity || 0}</span>
            </div>
            <div class="info-item">
                <span class="info-label">扇入:</span>
                <span class="info-value">${methodData.fanIn || 0}</span>
            </div>
            <div class="info-item">
                <span class="info-label">扇出:</span>
                <span class="info-value">${methodData.fanOut || 0}</span>
            </div>
            <div class="info-item">
                <span class="info-label">类名:</span>
                <span class="info-value">${methodData.className}</span>
            </div>
        `;

        // 修复：从新数据结构获取调用关系
        const methodCalls = this.currentData.methodCallGraph?.edges?.filter(e => e.source === methodId) || [];
        console.log('找到调用关系:', methodCalls.length);

        calls.innerHTML = methodCalls.length > 0 ? methodCalls.map(call => {
            const targetMethod = this.currentData.methodCallGraph?.nodes?.find(m => m.id === call.target);
            const methodName = targetMethod ? `${targetMethod.className}.${targetMethod.name}` : 'Unknown';
            return `
                <div class="call-item" onclick="dependencyVisualizer.focusMethod('${call.target}')" title="点击跳转到: ${methodName}">
                    <span>
                        <span style="color: var(--accent-color); margin-right: 4px;">▶</span>
                        调用 ${methodName}
                    </span>
                    <span class="call-count">1</span>
                </div>
            `;
        }).join('') : '<div class="no-data">无调用关系</div>';

        // 更新被调用关系
        const methodCalledBy = this.currentData.methodCallGraph?.edges?.filter(e => e.target === methodId) || [];
        console.log('找到被调用关系:', methodCalledBy.length);

        calledBy.innerHTML = methodCalledBy.length > 0 ? methodCalledBy.map(call => {
            const sourceMethod = this.currentData.methodCallGraph?.nodes?.find(m => m.id === call.source);
            const methodName = sourceMethod ? `${sourceMethod.className}.${sourceMethod.name}` : 'Unknown';
            return `
                <div class="called-by-item" onclick="dependencyVisualizer.focusMethod('${call.source}')" title="点击跳转到: ${methodName}">
                    <span>
                        <span style="color: var(--accent-color); margin-right: 4px;">◀</span>
                        被 ${methodName} 调用
                    </span>
                    <span class="call-count">1</span>
                </div>
            `;
        }).join('') : '<div class="no-data">无被调用关系</div>';

        // 显示面板
        panel.classList.add('show');
        console.log('方法详情面板已显示');
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
            // 显示导出选项对话框
            this.showExportDialog(graph);
        } else {
            this.showMessage('没有可导出的图表');
        }
    }

    /**
     * 显示导出选项对话框
     */
    showExportDialog(graph) {
        const dialog = document.createElement('div');
        dialog.className = 'export-dialog-overlay';
        dialog.innerHTML = `
            <div class="export-dialog">
                <div class="export-dialog-header">
                    <h3>导出图表</h3>
                    <button class="export-dialog-close">&times;</button>
                </div>
                <div class="export-dialog-content">
                    <div class="export-option">
                        <label>
                            <input type="radio" name="exportFormat" value="png" checked>
                            PNG 格式（推荐）
                        </label>
                    </div>
                    <div class="export-option">
                        <label>
                            <input type="radio" name="exportFormat" value="jpg">
                            JPG 格式
                        </label>
                    </div>
                    <div class="export-option">
                        <label>
                            <input type="radio" name="exportFormat" value="svg">
                            SVG 格式（矢量图）
                        </label>
                    </div>
                    <div class="export-option">
                        <label>
                            <input type="checkbox" id="include-background" checked>
                            包含背景色
                        </label>
                    </div>
                    <div class="export-option">
                        <label>
                            <input type="number" id="export-scale" value="2" min="1" max="5" step="0.5">
                            导出缩放比例 (倍数)
                        </label>
                    </div>
                </div>
                <div class="export-dialog-footer">
                    <button class="btn btn-secondary" id="export-cancel">取消</button>
                    <button class="btn btn-primary" id="export-confirm">导出</button>
                </div>
            </div>
        `;

        document.body.appendChild(dialog);

        // 绑定事件
        const closeBtn = dialog.querySelector('.export-dialog-close');
        const cancelBtn = dialog.querySelector('#export-cancel');
        const confirmBtn = dialog.querySelector('#export-confirm');

        const closeDialog = () => {
            document.body.removeChild(dialog);
        };

        closeBtn.addEventListener('click', closeDialog);
        cancelBtn.addEventListener('click', closeDialog);

        confirmBtn.addEventListener('click', () => {
            const format = dialog.querySelector('input[name="exportFormat"]:checked').value;
            const includeBackground = dialog.querySelector('#include-background').checked;
            const scale = parseFloat(dialog.querySelector('#export-scale').value);

            this.performExport(graph, format, includeBackground, scale);
            closeDialog();
        });

        // 点击背景关闭
        dialog.addEventListener('click', (e) => {
            if (e.target === dialog) {
                closeDialog();
            }
        });
    }

    /**
     * 执行导出操作
     */
    performExport(graph, format, includeBackground, scale) {
        try {
            const timestamp = new Date().toISOString().slice(0, 19).replace(/[:-]/g, '');
            const mode = this.currentMethodMode === 'callgraph' ? 'callgraph' : 'callchain';
            const filename = `nekoama-${mode}-${timestamp}`;

            switch (format) {
                case 'png':
                    this.exportAsPNG(graph, filename, includeBackground, scale);
                    break;
                case 'jpg':
                    this.exportAsJPG(graph, filename, includeBackground, scale);
                    break;
                case 'svg':
                    this.exportAsSVG(graph, filename);
                    break;
                default:
                    this.showMessage('不支持的导出格式');
            }
        } catch (error) {
            console.error('导出图表失败:', error);
            this.showMessage('导出失败: ' + error.message);
        }
    }

    /**
     * 导出为PNG格式
     */
    exportAsPNG(graph, filename, includeBackground, scale) {
        if (graph.downloadFullImage) {
            // 使用G6内置的下载方法
            graph.downloadFullImage(filename, 'image/png', {
                backgroundColor: includeBackground ? '#ffffff' : 'transparent',
                padding: 30,
                quality: 1.0,
                ratio: scale
            });
            this.showMessage('图表已导出为PNG格式');
        } else {
            this.fallbackExport(graph, filename, 'png', includeBackground, scale);
        }
    }

    /**
     * 导出为JPG格式
     */
    exportAsJPG(graph, filename, includeBackground, scale) {
        if (graph.downloadFullImage) {
            // 使用G6内置的下载方法
            graph.downloadFullImage(filename, 'image/jpeg', {
                backgroundColor: includeBackground ? '#ffffff' : '#ffffff',
                padding: 30,
                quality: 0.9,
                ratio: scale
            });
            this.showMessage('图表已导出为JPG格式');
        } else {
            this.fallbackExport(graph, filename, 'jpeg', includeBackground, scale);
        }
    }

    /**
     * 导出为SVG格式
     */
    exportAsSVG(graph, filename) {
        try {
            const svgContainer = graph.getContainer();
            const svgElements = svgContainer.querySelectorAll('svg');

            if (svgElements.length > 0) {
                const svgElement = svgElements[0];
                const svgData = new XMLSerializer().serializeToString(svgElement);
                const svgBlob = new Blob([svgData], { type: 'image/svg+xml;charset=utf-8' });

                const link = document.createElement('a');
                link.href = URL.createObjectURL(svgBlob);
                link.download = `${filename}.svg`;
                link.click();

                URL.revokeObjectURL(link.href);
                this.showMessage('图表已导出为SVG格式');
            } else {
                this.showMessage('无法获取SVG数据');
            }
        } catch (error) {
            console.error('SVG导出失败:', error);
            this.showMessage('SVG导出失败: ' + error.message);
        }
    }

    /**
     * 备用导出方法（使用canvas截图）
     */
    fallbackExport(graph, filename, format, includeBackground, scale) {
        try {
            const container = graph.getContainer();

            // 使用html2canvas库（如果可用）或者原生canvas方法
            if (window.html2canvas) {
                window.html2canvas(container, {
                    backgroundColor: includeBackground ? '#ffffff' : null,
                    scale: scale
                }).then(canvas => {
                    canvas.toBlob((blob) => {
                        const link = document.createElement('a');
                        link.href = URL.createObjectURL(blob);
                        link.download = `${filename}.${format}`;
                        link.click();
                        URL.revokeObjectURL(link.href);
                        this.showMessage(`图表已导出为${format.toUpperCase()}格式`);
                    }, `image/${format}`);
                });
            } else {
                // 简单的canvas导出
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                const rect = container.getBoundingClientRect();

                canvas.width = rect.width * scale;
                canvas.height = rect.height * scale;

                if (includeBackground) {
                    ctx.fillStyle = '#ffffff';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                }

                // 这里可以实现更复杂的截图逻辑
                // 但为了简化，我们提示用户使用其他方法
                this.showMessage('浏览器不支持直接导出，请使用截图工具');
            }
        } catch (error) {
            console.error('备用导出方法失败:', error);
            this.showMessage('导出失败，请尝试截图或使用其他浏览器');
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

    // ===== 模糊匹配功能 =====

    /**
     * Levenshtein距离算法实现
     * 计算两个字符串之间的编辑距离
     */
    levenshteinDistance(str1, str2) {
        const m = str1.length;
        const n = str2.length;

        // 如果其中一个字符串为空，返回另一个字符串的长度
        if (m === 0) return n;
        if (n === 0) return m;

        // 创建二维数组存储距离
        const dp = Array(m + 1).fill(null).map(() => Array(n + 1).fill(null));

        // 初始化边界值
        for (let i = 0; i <= m; i++) dp[i][0] = i;
        for (let j = 0; j <= n; j++) dp[0][j] = j;

        // 填充距离矩阵
        for (let i = 1; i <= m; i++) {
            for (let j = 1; j <= n; j++) {
                const cost = str1[i - 1] === str2[j - 1] ? 0 : 1;
                dp[i][j] = Math.min(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                );
            }
        }

        return dp[m][n];
    }

    /**
     * 计算字符串相似度 (0-1之间，1表示完全相同)
     */
    calculateSimilarity(str1, str2) {
        const distance = this.levenshteinDistance(
            str1.toLowerCase(),
            str2.toLowerCase()
        );
        const maxLen = Math.max(str1.length, str2.length);
        return maxLen === 0 ? 1 : (maxLen - distance) / maxLen;
    }

    /**
     * 搜索方法名
     * @param {string} query 搜索查询
     * @param {number} maxResults 最大结果数量
     * @returns {Array} 匹配的方法列表
     */
    searchMethods(query, maxResults = 20) {
        if (!query || query.trim().length === 0) {
            return [];
        }

        const results = [];
        const queryLower = query.toLowerCase().trim();

        // 获取所有方法数据
        const allMethods = this.getAllMethods();

        allMethods.forEach(method => {
            const methodName = method.name || '';
            const className = method.className || '';
            const fullMethodPath = `${className}.${methodName}`;

            // 计算不同层面的相似度
            const nameSimilarity = this.calculateSimilarity(methodName, query);
            const pathSimilarity = this.calculateSimilarity(fullMethodPath, query);
            const keywordMatch = this.calculateKeywordSimilarity(fullMethodPath, query);

            // 综合相似度评分
            const combinedScore = Math.max(nameSimilarity * 1.5, pathSimilarity, keywordMatch);

            if (combinedScore > 0.3) { // 相似度阈值
                results.push({
                    ...method,
                    similarity: combinedScore,
                    matchType: nameSimilarity > 0.7 ? 'exact' :
                              pathSimilarity > 0.7 ? 'path' : 'keyword'
                });
            }
        });

        // 按相似度排序并限制结果数量
        return results
            .sort((a, b) => b.similarity - a.similarity)
            .slice(0, maxResults);
    }

    /**
     * 计算关键词相似度
     * 检查查询是否是方法名或类名的一部分
     */
    calculateKeywordSimilarity(str, query) {
        const strLower = str.toLowerCase();
        const queryLower = query.toLowerCase();

        // 精确匹配
        if (strLower.includes(queryLower)) {
            return 0.9;
        }

        // 查询的每个词是否都存在
        const queryWords = queryLower.split(/\s+/);
        const strWords = strLower.split(/[\.\-_]/);

        let matchedWords = 0;
        queryWords.forEach(qWord => {
            if (strWords.some(sWord => sWord.includes(qWord))) {
                matchedWords++;
            }
        });

        return matchedWords / queryWords.length * 0.8;
    }

    /**
     * 获取所有方法数据
     */
    getAllMethods() {
        const methods = [];

        if (this.currentData?.methodCallGraph?.nodes) {
            this.currentData.methodCallGraph.nodes.forEach(node => {
                methods.push({
                    id: node.id,
                    name: node.name,
                    className: node.className,
                    complexity: node.complexity || 0,
                    fanIn: node.fanIn || 0,
                    fanOut: node.fanOut || 0
                });
            });
        }

        // 如果没有方法数据，尝试从其他数据源获取
        if (methods.length === 0 && this.currentData?.classGraph?.nodes) {
            this.currentData.classGraph.nodes.forEach(node => {
                // 为类创建一个虚拟的方法节点
                methods.push({
                    id: node.id,
                    name: node.name,
                    className: node.packagePath || node.name,
                    complexity: node.complexity || 0,
                    fanIn: 0,
                    fanOut: 0,
                    isClassNode: true
                });
            });
        }

        return methods;
    }

    /**
     * 初始化搜索功能
     */
    initSearchFunctionality() {
        const searchInput = document.getElementById('method-search-input');
        const searchResults = document.getElementById('search-results');

        if (!searchInput || !searchResults) return;

        let searchTimeout;
        let selectedResultIndex = -1;
        let currentResults = [];

        // 输入事件监听 - 使用防抖
        searchInput.addEventListener('input', (e) => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                this.performSearch(e.target.value);
                selectedResultIndex = -1;
            }, 300); // 300ms防抖
        });

        // 键盘导航支持
        searchInput.addEventListener('keydown', (e) => {
            if (currentResults.length === 0) return;

            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    selectedResultIndex = Math.min(selectedResultIndex + 1, currentResults.length - 1);
                    this.updateSearchResultSelection();
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    selectedResultIndex = Math.max(selectedResultIndex - 1, -1);
                    this.updateSearchResultSelection();
                    break;
                case 'Enter':
                    e.preventDefault();
                    if (selectedResultIndex >= 0 && currentResults[selectedResultIndex]) {
                        this.selectSearchResult(currentResults[selectedResultIndex]);
                    }
                    break;
                case 'Escape':
                    this.clearSearch();
                    break;
            }
        });

        // 点击其他地方关闭搜索结果
        document.addEventListener('click', (e) => {
            if (!searchInput.contains(e.target) && !searchResults.contains(e.target)) {
                this.clearSearch();
            }
        });
    }

    /**
     * 执行搜索
     */
    performSearch(query) {
        const searchResults = document.getElementById('search-results');
        if (!searchResults) return;

        if (!query || query.trim().length === 0) {
            searchResults.style.display = 'none';
            return;
        }

        const results = this.searchMethods(query);
        this.currentSearchResults = results;

        if (results.length === 0) {
            searchResults.innerHTML = '<div class="search-no-results">未找到匹配的方法</div>';
        } else {
            searchResults.innerHTML = results.map((result, index) => `
                <div class="search-result-item" data-index="${index}">
                    <div class="search-result-method">${result.name}</div>
                    <div class="search-result-class">${result.className}</div>
                </div>
            `).join('');

            // 添加点击事件
            searchResults.querySelectorAll('.search-result-item').forEach(item => {
                item.addEventListener('click', () => {
                    const index = parseInt(item.dataset.index);
                    this.selectSearchResult(results[index]);
                });
            });
        }

        searchResults.style.display = 'block';
    }

    /**
     * 更新搜索结果选择状态
     */
    updateSearchResultSelection() {
        const items = document.querySelectorAll('.search-result-item');
        items.forEach((item, index) => {
            if (index === this.currentSearchResultsIndex) {
                item.classList.add('selected');
                item.scrollIntoView({ block: 'nearest' });
            } else {
                item.classList.remove('selected');
            }
        });
    }

    /**
     * 选择搜索结果
     */
    selectSearchResult(result) {
        const searchInput = document.getElementById('method-search-input');
        const searchResults = document.getElementById('search-results');

        if (searchInput) {
            searchInput.value = `${result.className}.${result.name}`;
        }

        this.clearSearch();

        // 如果在调用链分析模式，直接分析这个方法的调用链
        if (this.currentMethodMode === 'callchain') {
            this.analyzeCallChain(result.id);
        } else {
            // 如果在调用关系图模式，聚焦到这个方法
            this.focusMethod(result.id);
        }
    }

    /**
     * 清空搜索
     */
    clearSearch() {
        const searchInput = document.getElementById('method-search-input');
        const searchResults = document.getElementById('search-results');

        if (searchInput) {
            searchInput.value = '';
        }

        if (searchResults) {
            searchResults.style.display = 'none';
            searchResults.innerHTML = '';
        }

        this.currentSearchResults = [];
        this.currentSearchResultsIndex = -1;
    }

    // ===== 完整调用链筛选逻辑 =====

    /**
     * 分析指定方法的完整调用链
     * @param {string} methodId 方法ID
     */
    analyzeCallChain(methodId) {
        if (!this.currentData?.methodCallGraph?.nodes) {
            this.showMessage('没有可用的方法调用数据');
            return;
        }

        // 递归构建完整调用链
        const callChain = this.buildCompleteCallChain(methodId);

        if (callChain.nodes.length === 0) {
            this.showMessage('未找到指定方法的调用链');
            return;
        }

        // 更新调用链统计信息
        this.updateCallChainStats(callChain);

        // 渲染调用链图表
        this.renderCallChainGraph(callChain);

        // 切换到调用链模式
        if (this.currentMethodMode !== 'callchain') {
            this.switchMethodMode('callchain');
        }
    }

    /**
     * 递归构建完整调用链
     * @param {string} startMethodId 起始方法ID
     * @param {Set} visited 已访问方法ID集合（防止循环调用）
     * @returns {Object} 包含节点和边的调用链数据
     */
    buildCompleteCallChain(startMethodId, visited = new Set()) {
        if (visited.has(startMethodId)) {
            // 检测到循环调用，停止递归
            return { nodes: [], edges: [] };
        }

        visited.add(startMethodId);

        const allNodes = new Map();
        const allEdges = [];
        const nodesToProcess = [startMethodId];

        // 查找起始方法节点
        const startNode = this.findMethodNode(startMethodId);
        if (startNode) {
            allNodes.set(startMethodId, startNode);
        }

        while (nodesToProcess.length > 0) {
            const currentMethodId = nodesToProcess.pop();

            // 获取当前方法的所有直接调用
            const outgoingCalls = this.getOutgoingCalls(currentMethodId);

            outgoingCalls.forEach(call => {
                const targetMethodId = call.toMethod;

                // 先检查目标节点是否存在
                const targetNode = this.findMethodNode(targetMethodId);
                if (targetNode) {
                    // 只有当目标节点存在时才添加边
                    if (!allEdges.some(edge =>
                        edge.source === currentMethodId && edge.target === targetMethodId)) {
                        allEdges.push({
                            source: currentMethodId,
                            target: targetMethodId,
                            callType: call.callType || 'method_call',
                            line: call.line || 0
                        });
                    }

                    // 如果目标方法未被处理过，则加入处理队列
                    if (!visited.has(targetMethodId)) {
                        allNodes.set(targetMethodId, targetNode);
                        nodesToProcess.push(targetMethodId);
                        visited.add(targetMethodId);
                    }
                } else {
                    // 目标节点不存在（可能是外部方法），记录但不创建边
                    console.debug(`G6 Visualization: 跳过外部方法调用 - 目标节点不存在: ${targetMethodId}`);
                }
            });
        }

        return {
            nodes: Array.from(allNodes.values()),
            edges: allEdges,
            startMethod: startMethodId
        };
    }

    /**
     * 查找方法节点
     * @param {string} methodId 方法ID
     * @returns {Object|null} 方法节点数据
     */
    findMethodNode(methodId) {
        const nodes = this.currentData.methodCallGraph?.nodes || [];
        return nodes.find(node => node.id === methodId) || null;
    }

    /**
     * 获取方法的直接调用（被调用的方法）
     * @param {string} methodId 方法ID
     * @returns {Array} 调用关系数组
     */
    getOutgoingCalls(methodId) {
        const methodCalls = this.currentData.methodCallGraph?.methodCalls || {};
        return methodCalls[methodId] || [];
    }

    /**
     * 获取调用该方法的方法（调用者）
     * @param {string} methodId 方法ID
     * @returns {Array} 调用者关系数组
     */
    getIncomingCalls(methodId) {
        const methodCalls = this.currentData.methodCallGraph?.methodCalls || {};
        const incomingCalls = [];

        Object.entries(methodCalls).forEach(([callerId, calls]) => {
            calls.forEach(call => {
                if (call.toMethod === methodId) {
                    incomingCalls.push({
                        fromMethod: callerId,
                        toMethod: methodId,
                        callType: call.callType || 'method_call',
                        line: call.line || 0
                    });
                }
            });
        });

        return incomingCalls;
    }

    /**
     * 更新调用链统计信息
     * @param {Object} callChain 调用链数据
     */
    updateCallChainStats(callChain) {
        const nodeCountEl = document.getElementById('node-count');
        const edgeCountEl = document.getElementById('edge-count');
        const chainLengthEl = document.getElementById('chain-length');
        const chainComplexityEl = document.getElementById('chain-complexity');

        if (nodeCountEl) nodeCountEl.textContent = callChain.nodes.length;
        if (edgeCountEl) edgeCountEl.textContent = callChain.edges.length;
        if (chainLengthEl) chainLengthEl.textContent = callChain.nodes.length;

        // 计算复杂度总和
        const totalComplexity = callChain.nodes.reduce((sum, node) =>
            sum + (node.complexity || 0), 0);
        if (chainComplexityEl) chainComplexityEl.textContent = totalComplexity;
    }

    /**
     * 渲染调用链图表
     * @param {Object} callChain 调用链数据
     */
    renderCallChainGraph(callChain) {
        const container = document.getElementById('call-chain-graph');
        if (!container) return;

        // 销毁现有图表
        if (this.callChainGraph) {
            this.callChainGraph.destroy();
        }

        // 转换数据格式
        const g6Data = this.convertCallChainToG6Data(callChain);

        // 创建G6图表实例
        this.callChainGraph = new G6.Graph({
            container: container,
            width: container.clientWidth,
            height: 500,
            layout: {
                type: 'force', // 固定使用力导向布局
                preventOverlap: true,
                nodeSize: 20,
                linkDistance: 100,
                nodeStrength: -50,
                edgeStrength: 0.1,
            },
            modes: {
                default: [
                    'drag-canvas',
                    'zoom-canvas',
                    'drag-node',
                    {
                        type: 'tooltip',
                        formatText(model) {
                            return `${model.name || model.id}\n复杂度: ${model.complexity || 0}`;
                        }
                    }
                ]
            },
            defaultNode: {
                size: 30,
                style: {
                    fill: '#1890FF',
                    stroke: '#fff',
                    lineWidth: 2,
                },
                labelCfg: {
                    position: 'bottom',
                    style: {
                        fill: '#000',
                        fontSize: 12,
                    }
                }
            },
            defaultEdge: {
                style: {
                    stroke: '#e2e2e2',
                    lineWidth: 2,
                    endArrow: {
                        path: 'M 0,0 L 8,4 L 8,-4 Z',
                        fill: '#e2e2e2',
                    },
                }
            }
        });

        // 数据处理 - 根据复杂度设置节点颜色和大小
        g6Data.nodes = g6Data.nodes.map(node => {
            const complexity = node.complexity || 0;
            let color = '#52c41a'; // 低复杂度 - 绿色
            let size = 30;

            if (complexity > 30) {
                color = '#ff4d4f'; // 高复杂度 - 红色
                size = 40;
            } else if (complexity > 15) {
                color = '#faad14'; // 中等复杂度 - 橙色
                size = 35;
            }

            return {
                ...node,
                style: {
                    fill: color,
                    stroke: node.id === callChain.startMethod ? '#1890FF' : '#fff',
                    lineWidth: node.id === callChain.startMethod ? 3 : 2,
                },
                size: size
            };
        });

        // 读取数据并渲染
        this.callChainGraph.data(g6Data);
        this.callChainGraph.render();

        // 聚焦起始方法
        setTimeout(() => {
            this.callChainGraph.focusItem(callChain.startMethod, true);
        }, 500);
    }

    /**
     * 将调用链数据转换为G6格式
     * @param {Object} callChain 调用链数据
     * @returns {Object} G6格式数据
     */
    convertCallChainToG6Data(callChain) {
        return {
            nodes: callChain.nodes.map(node => ({
                id: node.id,
                label: node.name || node.id,
                name: node.name,
                className: node.className,
                complexity: node.complexity,
                fanIn: node.fanIn,
                fanOut: node.fanOut
            })),
            edges: callChain.edges.map(edge => ({
                source: edge.source,
                target: edge.target,
                label: edge.callType,
                callType: edge.callType,
                line: edge.line
            }))
        };
    }

    /**
     * 显示调用链详情
     * @param {Object} callChain 调用链数据
     */
    displayCallChainDetails(callChain) {
        const chainSteps = document.getElementById('chain-steps');
        if (!chainSteps) return;

        // 构建调用链步骤
        const steps = this.buildCallChainSteps(callChain);

        chainSteps.innerHTML = steps.map((step, index) => `
            <div class="chain-step">
                <div class="step-number">${index + 1}</div>
                <div class="step-content">
                    <div class="step-method">${step.method}</div>
                    <div class="step-class">${step.className}</div>
                    <div class="step-info">
                        <span class="complexity">复杂度: ${step.complexity}</span>
                        ${step.line ? `<span class="line">行号: ${step.line}</span>` : ''}
                    </div>
                </div>
            </div>
        `).join('');
    }

    /**
     * 构建调用链步骤数组
     * @param {Object} callChain 调用链数据
     * @returns {Array} 调用链步骤数组
     */
    buildCallChainSteps(callChain) {
        const steps = [];
        const visited = new Set();
        const currentPath = [callChain.startMethod];

        // 简单的深度优先搜索构建调用链路径
        this.buildCallChainPath(callChain.startMethod, callChain, visited, currentPath, steps);

        return steps;
    }

    /**
     * 递归构建调用链路径
     * @param {string} currentMethod 当前方法ID
     * @param {Object} callChain 调用链数据
     * @param {Set} visited 已访问方法
     * @param {Array} currentPath 当前路径
     * @param {Array} steps 结果步骤数组
     */
    buildCallChainPath(currentMethod, callChain, visited, currentPath, steps) {
        if (visited.has(currentMethod)) {
            return;
        }

        visited.add(currentMethod);

        // 添加当前步骤
        const node = callChain.nodes.find(n => n.id === currentMethod);
        if (node) {
            const outgoingEdge = callChain.edges.find(e => e.source === currentMethod);

            steps.push({
                method: node.name || node.id,
                className: node.className || '',
                complexity: node.complexity || 0,
                line: outgoingEdge?.line || 0,
                depth: currentPath.length - 1
            });
        }

        // 查找下一个被调用的方法
        const nextCalls = callChain.edges.filter(e => e.source === currentMethod);

        // 为了避免显示过于复杂的调用链，这里只显示主要的调用路径
        if (nextCalls.length > 0) {
            const primaryCall = nextCalls[0]; // 选择第一个调用作为主要路径
            currentPath.push(primaryCall.target);
            this.buildCallChainPath(primaryCall.target, callChain, visited, currentPath, steps);
        }

        currentPath.pop();
    }

    /**
     * 初始化调用链模式
     */
    initCallChainMode() {
        const container = document.getElementById('call-chain-graph');
        if (!container) return;

        // 如果还没有数据，显示提示
        if (!this.currentData?.methodCallGraph) {
            container.innerHTML = '<div class="empty-state">请选择方法并点击"分析调用链"按钮</div>';
            return;
        }

        // 填充入口方法下拉框
        this.populateEntryMethodSelect();
    }

    /**
     * 填充入口方法下拉框
     */
    populateEntryMethodSelect() {
        const select = document.getElementById('entry-method-select');
        if (!select) return;

        const allMethods = this.getAllMethods();

        // 清空现有选项
        select.innerHTML = '<option value="">选择入口方法</option>';

        // 添加业务入口方法
        if (this.currentData?.entryPoints) {
            this.currentData.entryPoints.forEach(entry => {
                const option = document.createElement('option');
                option.value = `${entry.className}.${entry.methodName}`;
                option.textContent = `${entry.className}.${entry.methodName} (${entry.entryType})`;
                select.appendChild(option);
            });
        }

        // 如果没有业务入口方法，添加所有方法
        if (this.currentData.entryPoints?.length === 0) {
            allMethods.forEach(method => {
                const option = document.createElement('option');
                option.value = method.id;
                option.textContent = `${method.className}.${method.name}`;
                select.appendChild(option);
            });
        }
    }
}

// 添加数组的groupBy和mapValues polyfill
if (!Array.prototype.groupBy) {
    Array.prototype.groupBy = function(keySelector) {
        return this.reduce((groups, item) => {
            const key = keySelector(item);
            if (!groups[key]) {
                groups[key] = [];
            }
            groups[key].push(item);
            return groups;
        }, {});
    };
}

if (!Object.prototype.mapValues) {
    Object.prototype.mapValues = function(valueMapper) {
        const result = {};
        for (const [key, value] of Object.entries(this)) {
            result[key] = valueMapper(value, key);
        }
        return result;
    };
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