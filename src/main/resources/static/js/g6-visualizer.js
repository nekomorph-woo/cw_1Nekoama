/**
 * AntV G6 可视化组件
 *
 * 提供代码依赖关系的交互式可视化功能，包括：
 * - 包级依赖图
 * - 类级关系图
 * - 方法复杂度热力图
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
        ['package-graph', 'class-graph', 'method-heatmap', 'scene-graph'].forEach(id => {
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

        // 热力图指标切换
        const heatmapMetric = document.getElementById('heatmap-metric');
        if (heatmapMetric) {
            heatmapMetric.addEventListener('change', (e) => {
                this.updateMethodHeatmap(e.target.value);
            });
        }

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
                this.updateMethodHeatmap('cyclomatic');
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
     * 更新方法热力图
     */
    updateMethodHeatmap(metric) {
        const container = document.getElementById('method-heatmap');
        if (!container || !this.currentData) return;

        // 这里简化实现，实际应该使用热力图库如D3.js
        const methods = this.getTopMethodsByMetric(metric, 20);

        let html = '<div class="heatmap-grid">';
        methods.forEach(method => {
            const value = this.getMethodMetricValue(method, metric);
            const color = this.getHeatmapColor(value, metric);

            html += `
                <div class="heatmap-cell" style="background-color: ${color};"
                     title="${method.className}.${method.methodName}: ${value}">
                    <div class="method-name">${method.methodName}</div>
                    <div class="method-class">${method.className}</div>
                    <div class="metric-value">${value}</div>
                </div>
            `;
        });
        html += '</div>';

        container.innerHTML = html;
    }

    /**
     * 获取按指标排序的顶级方法
     */
    getTopMethodsByMetric(metric, limit) {
        const methods = [];

        this.currentData.methods?.forEach(method => {
            methods.push({
                ...method,
                metricValue: this.getMethodMetricValue(method, metric)
            });
        });

        return methods
            .sort((a, b) => b.metricValue - a.metricValue)
            .slice(0, limit);
    }

    /**
     * 获取方法指标值
     */
    getMethodMetricValue(method, metric) {
        const metrics = method.metrics;
        switch (metric) {
            case 'cyclomatic':
                return metrics?.cyclomaticComplexity || 0;
            case 'cognitive':
                return metrics?.cognitiveComplexity || 0;
            case 'lines':
                return metrics?.linesOfCode || 0;
            case 'parameters':
                return metrics?.parameterCount || 0;
            default:
                return 0;
        }
    }

    /**
     * 获取热力图颜色
     */
    getHeatmapColor(value, metric) {
        const thresholds = this.getMetricThresholds(metric);

        if (value >= thresholds.high) return '#FF4D4F';
        if (value >= thresholds.medium) return '#FA8C16';
        if (value >= thresholds.low) return '#FAAD14';
        return '#52C41A';
    }

    /**
     * 获取指标阈值
     */
    getMetricThresholds(metric) {
        switch (metric) {
            case 'cyclomatic':
                return { high: 10, medium: 5, low: 2 };
            case 'cognitive':
                return { high: 15, medium: 8, low: 4 };
            case 'lines':
                return { high: 50, medium: 25, low: 10 };
            case 'parameters':
                return { high: 6, medium: 4, low: 2 };
            default:
                return { high: 10, medium: 5, low: 2 };
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