/**
 * 统一方法视图 - AntV G6 可视化组件
 *
 * 提供代码依赖关系的交互式可视化功能，包括：
 * - 统一的方法调用关系视图（径向布局）
 * - 复杂度筛选、调用次数筛选
 * - 入口方法选择、模糊搜索
 * - 方法详情显示和交互
 */

class UnifiedMethodView {
    constructor(container, data) {
        this.container = container;
        this.rawData = data;
        this.currentFilters = {
            complexity: 'all',
            callCount: 'all'
        };
        this.currentEntryMethod = '';
        this.currentSearchTerm = '';

        // 核心组件
        this.graph = null;
        this.dataManager = null;
        this.eventManager = null;
        this.uiManager = null;
        this.searchManager = null;

        // 初始化
        this.init();
    }

    /**
     * 初始化统一方法视图
     */
    init() {
        console.log('初始化统一方法视图...');

        try {
            // 标准化数据结构
            this.normalizeDataStructure();

            // 初始化核心组件
            this.initComponents();

            // 绑定事件监听器
            this.bindEventListeners();

            // 渲染初始视图
            this.render();

            console.log('统一方法视图初始化完成');
        } catch (error) {
            console.error('初始化统一方法视图失败:', error);
            this.showError('初始化失败: ' + error.message);
        }
    }

    /**
     * 标准化数据结构，兼容新旧版本
     */
    normalizeDataStructure() {
        if (!this.rawData.methodCallGraph) {
            console.error('缺少方法调用图数据');
            throw new Error('缺少方法调用图数据');
        }

        // 确保数据结构完整性
        this.normalizedData = {
            methodCallGraph: {
                nodes: this.rawData.methodCallGraph.nodes || [],
                edges: this.rawData.methodCallGraph.edges || []
            },
            classGraph: this.rawData.classGraph || { nodes: [], edges: [] },
            projectInfo: this.rawData.projectInfo || { name: '未知项目', location: '' }
        };

        console.log(`标准化数据完成: ${this.normalizedData.methodCallGraph.nodes.length} 个方法节点`);
    }

    /**
     * 初始化核心组件
     */
    initComponents() {
        // 初始化数据管理器
        this.dataManager = new UnifiedDataManager(this.normalizedData);

        // 初始化搜索管理器
        this.searchManager = new SearchManager(this.normalizedData);

        // 初始化图形渲染器
        const nodeCount = this.normalizedData.methodCallGraph.nodes.length;
        this.graph = new GraphRenderer(this.container, nodeCount);

        // 初始化事件管理器
        this.eventManager = new EventManager(this.graph.graph);

        // 初始化UI管理器
        this.uiManager = new UIManager();

        // 初始化入口方法选择器
        this.populateEntryMethodSelect();
    }

    /**
     * 填充入口方法下拉框
     */
    populateEntryMethodSelect() {
        const selectElement = document.getElementById('entry-method-select');
        if (!selectElement) return;

        // 找到所有业务入口方法
        const entryMethods = this.normalizedData.methodCallGraph.nodes.filter(node =>
            node.isEntryPoint || this.isBusinessEntryMethod(node)
        );

        // 按类名分组
        const groupedMethods = entryMethods.reduce((groups, method) => {
            const className = method.className || '未知类';
            if (!groups[className]) {
                groups[className] = [];
            }
            groups[className].push(method);
            return groups;
        }, {});

        // 清空现有选项（保留默认选项）
        selectElement.innerHTML = '<option value="">选择入口方法（可选）</option>';

        // 添加分组选项
        Object.keys(groupedMethods).sort().forEach(className => {
            const optgroup = document.createElement('optgroup');
            optgroup.label = className;

            groupedMethods[className].sort((a, b) => a.name.localeCompare(b.name)).forEach(method => {
                const option = document.createElement('option');
                option.value = method.id;
                option.textContent = `${method.name}() - ${method.packageName || ''}`;
                optgroup.appendChild(option);
            });

            selectElement.appendChild(optgroup);
        });
    }

    /**
     * 判断是否为业务入口方法
     */
    isBusinessEntryMethod(node) {
        if (!node.className) return false;

        const businessPatterns = [
            /^Controller$/i,
            /Controller$/i,
            /^Service$/i,
            /Service$/i,
            /^Repository$/i,
            /Repository$/i,
            /^Component$/i,
            /Component$/i,
            /^Rest/i,
            /^Api/i,
            /Handler$/i
        ];

        return businessPatterns.some(pattern => pattern.test(node.className));
    }

    /**
     * 绑定事件监听器
     */
    bindEventListeners() {
        // 筛选控件事件
        this.bindFilterEvents();

        // 搜索事件
        this.bindSearchEvents();

        // 按钮事件
        this.bindButtonEvents();

        // 绑定G6图形事件
        this.bindGraphEvents();
    }

    /**
     * 绑定筛选事件
     */
    bindFilterEvents() {
        // 复杂度筛选
        const complexityFilter = document.getElementById('complexity-filter');
        if (complexityFilter) {
            complexityFilter.addEventListener('change', (e) => {
                this.currentFilters.complexity = e.target.value;
                this.applyFiltersAndRender();
            });
        }

        // 调用次数筛选
        const callCountFilter = document.getElementById('call-count-filter');
        if (callCountFilter) {
            callCountFilter.addEventListener('change', (e) => {
                this.currentFilters.callCount = e.target.value;
                this.applyFiltersAndRender();
            });
        }

        // 入口方法选择
        const entryMethodSelect = document.getElementById('entry-method-select');
        if (entryMethodSelect) {
            entryMethodSelect.addEventListener('change', (e) => {
                this.currentEntryMethod = e.target.value;
                this.updateSelectedEntryDisplay();
                this.applyFiltersAndRender();
            });
        }
    }

    /**
     * 绑定搜索事件
     */
    bindSearchEvents() {
        const searchInput = document.getElementById('method-search-input');
        const clearSearchBtn = document.getElementById('clear-search');

        if (searchInput) {
            let searchTimer;

            searchInput.addEventListener('input', (e) => {
                clearTimeout(searchTimer);
                searchTimer = setTimeout(() => {
                    this.currentSearchTerm = e.target.value.trim();
                    if (this.currentSearchTerm) {
                        this.searchManager.showSearchResults(this.currentSearchTerm);
                    } else {
                        this.searchManager.hideSearchResults();
                    }
                    this.applyFiltersAndRender();
                }, 300);
            });

            // 键盘导航
            searchInput.addEventListener('keydown', (e) => {
                this.searchManager.handleKeyNavigation(e);
            });
        }

        if (clearSearchBtn) {
            clearSearchBtn.addEventListener('click', () => {
                this.currentSearchTerm = '';
                if (searchInput) searchInput.value = '';
                this.searchManager.hideSearchResults();
                this.applyFiltersAndRender();
            });
        }
    }

    /**
     * 绑定按钮事件
     */
    bindButtonEvents() {
        // 重置视图
        const resetBtn = document.getElementById('reset-view');
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                this.resetView();
            });
        }

        // 导出图片
        const exportBtn = document.getElementById('export-graph');
        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                this.exportGraph();
            });
        }
    }

    /**
     * 绑定G6图形事件
     */
    bindGraphEvents() {
        this.eventManager.setupEventHandlers((nodeModel) => {
            // 节点点击回调
            this.showMethodDetails(nodeModel);
        });
    }

    /**
     * 应用筛选条件并重新渲染
     */
    applyFiltersAndRender() {
        try {
            // 转换筛选条件
            const transformedFilters = this.transformFilters();

            // 应用数据筛选
            const filteredData = this.dataManager.transformData(
                transformedFilters,
                this.currentEntryMethod,
                this.currentSearchTerm
            );

            // 更新图形渲染
            this.graph.updateData(filteredData);

            // 更新UI统计
            this.uiManager.updateStats(filteredData, this.currentEntryMethod);

            // 更新方法来源图例
            this.updateMethodSourceLegend(filteredData);

        } catch (error) {
            console.error('应用筛选条件失败:', error);
            this.showError('筛选失败: ' + error.message);
        }
    }

    /**
     * 转换筛选条件格式
     */
    transformFilters() {
        const filters = {};

        if (this.currentFilters.complexity !== 'all') {
            filters.complexity = this.currentFilters.complexity;
        }

        if (this.currentFilters.callCount !== 'all') {
            filters.callCount = this.currentFilters.callCount;
        }

        return filters;
    }

    /**
     * 更新选中入口方法显示
     */
    updateSelectedEntryDisplay() {
        const selectedEntryStat = document.getElementById('selected-entry-stat');
        const selectedEntryName = document.getElementById('selected-entry-name');

        if (selectedEntryStat && selectedEntryName) {
            if (this.currentEntryMethod) {
                const method = this.normalizedData.methodCallGraph.nodes.find(n => n.id === this.currentEntryMethod);
                selectedEntryName.textContent = method ? `${method.className}.${method.name}()` : '未知方法';
                selectedEntryStat.style.display = 'block';
            } else {
                selectedEntryStat.style.display = 'none';
            }
        }
    }

    /**
     * 更新方法来源图例
     */
    updateMethodSourceLegend(data) {
        const legendElement = document.getElementById('method-source-legend');
        const legendContent = document.getElementById('legend-content');

        if (!legendElement || !legendContent) return;

        // 分析方法来源
        const sourceAnalysis = this.analyzeMethodSources(data.nodes);

        if (sourceAnalysis.hasExternalMethods) {
            legendContent.innerHTML = `
                <div class="legend-item">
                    <span class="legend-external">◼</span>
                    <span>外部依赖方法</span>
                </div>
                <div class="legend-item">
                    <span class="legend-internal">◼</span>
                    <span>内部项目方法</span>
                </div>
            `;
            legendElement.style.display = 'block';
        } else {
            legendElement.style.display = 'none';
        }
    }

    /**
     * 分析方法来源
     */
    analyzeMethodSources(nodes) {
        let hasExternalMethods = false;
        let hasInternalMethods = false;

        nodes.forEach(node => {
            if (node.isExternal === true) {
                hasExternalMethods = true;
            } else {
                hasInternalMethods = true;
            }
        });

        return { hasExternalMethods, hasInternalMethods };
    }

    /**
     * 显示方法详情
     */
    showMethodDetails(nodeModel) {
        this.uiManager.showMethodDetails(nodeModel, this.normalizedData);
    }

    /**
     * 重置视图
     */
    resetView() {
        // 重置筛选条件
        this.currentFilters = { complexity: 'all', callCount: 'all' };
        this.currentEntryMethod = '';
        this.currentSearchTerm = '';

        // 重置UI控件
        const complexityFilter = document.getElementById('complexity-filter');
        const callCountFilter = document.getElementById('call-count-filter');
        const entryMethodSelect = document.getElementById('entry-method-select');
        const searchInput = document.getElementById('method-search-input');

        if (complexityFilter) complexityFilter.value = 'all';
        if (callCountFilter) callCountFilter.value = 'all';
        if (entryMethodSelect) entryMethodSelect.value = '';
        if (searchInput) searchInput.value = '';

        // 隐藏搜索结果
        this.searchManager.hideSearchResults();

        // 重新渲染
        this.applyFiltersAndRender();

        // 重置图形视图
        this.graph.resetView();
    }

    /**
     * 导出图形
     */
    exportGraph() {
        try {
            this.graph.exportGraph('method-view');
            this.uiManager.showSuccess('图形已导出');
        } catch (error) {
            console.error('导出图形失败:', error);
            this.showError('导出失败: ' + error.message);
        }
    }

    /**
     * 渲染初始视图
     */
    render() {
        // 应用初始筛选条件
        this.applyFiltersAndRender();

        // 隐藏加载遮罩
        this.hideLoading();

        console.log('统一方法视图渲染完成');
    }

    /**
     * 显示错误信息
     */
    showError(message) {
        console.error(message);
        this.uiManager.showError(message);
    }

    /**
     * 隐藏加载遮罩
     */
    hideLoading() {
        const loadingOverlay = document.getElementById('loading-overlay');
        if (loadingOverlay) {
            loadingOverlay.style.display = 'none';
        }
    }
}

/**
 * 自适应径向布局配置类
 */
class AdaptiveRadialLayout {
    constructor(nodeCount) {
        this.nodeCount = nodeCount;
        this.config = this.calculateOptimalConfig();
    }

    /**
     * 根据节点数量动态计算最优布局参数
     */
    calculateOptimalConfig() {
        if (this.nodeCount <= 20) {
            return {
                unitRadius: 120,
                nodeSpacing: 35,
                nodeSize: 50,
                linkDistance: 180
            };
        } else if (this.nodeCount <= 50) {
            return {
                unitRadius: 90,
                nodeSpacing: 25,
                nodeSize: 40,
                linkDistance: 135
            };
        } else if (this.nodeCount <= 100) {
            return {
                unitRadius: 70,
                nodeSpacing: 20,
                nodeSize: 35,
                linkDistance: 105
            };
        } else if (this.nodeCount <= 200) {
            return {
                unitRadius: 55,
                nodeSpacing: 15,
                nodeSize: 30,
                linkDistance: 82
            };
        } else {
            return {
                unitRadius: 45,
                nodeSpacing: 12,
                nodeSize: 25,
                linkDistance: 67
            };
        }
    }

    /**
     * 获取G6径向布局配置
     */
    getLayoutConfig() {
        return {
            type: 'radial',
            unitRadius: this.config.unitRadius,
            preventOverlap: true,
            nodeSize: this.config.nodeSize,
            nodeSpacing: this.config.nodeSpacing,
            linkDistance: this.config.linkDistance,
            // 自适应防重叠强度
            preventOverlapPadding: Math.max(15, 50 - this.nodeCount * 0.1),
            // 简化径向布局配置，避免版本兼容性问题
            maxIteration: 500
        };
    }
}

/**
 * 统一数据管理器
 */
class UnifiedDataManager {
    constructor(normalizedData) {
        this.rawData = normalizedData;
    }

    /**
     * 转换数据，应用筛选条件
     */
    transformData(filters = {}, entryMethod = '', searchTerm = '') {
        let nodes = [...this.rawData.methodCallGraph.nodes];
        let edges = [...this.rawData.methodCallGraph.edges];

        // 1. 入口方法筛选
        if (entryMethod) {
            const connectedNodes = this.getConnectedNodes(entryMethod, edges);
            const connectedNodeIds = new Set(connectedNodes.map(n => n.id));
            nodes = nodes.filter(n => connectedNodeIds.has(n.id));
            edges = edges.filter(e => connectedNodeIds.has(e.source) && connectedNodeIds.has(e.target));
        }

        // 2. 复杂度筛选
        if (filters.complexity) {
            nodes = nodes.filter(node => {
                const complexity = node.complexity || 0;
                switch (filters.complexity) {
                    case 'high': return complexity > 30;
                    case 'medium': return complexity >= 15 && complexity <= 30;
                    case 'low': return complexity < 15;
                    default: return true;
                }
            });
        }

        // 3. 调用次数筛选
        if (filters.callCount) {
            const nodeCallCounts = this.calculateNodeCallCounts(edges);
            nodes = nodes.filter(node => {
                const callCount = nodeCallCounts[node.id] || 0;
                switch (filters.callCount) {
                    case 'zero': return callCount === 0;
                    case 'positive': return callCount > 0;
                    case 'ge5': return callCount >= 5;
                    case 'ge10': return callCount >= 10;
                    case 'ge20': return callCount >= 20;
                    default: return true;
                }
            });
        }

        // 4. 模糊搜索
        if (searchTerm) {
            const query = searchTerm.toLowerCase();
            nodes = nodes.filter(node =>
                node.name.toLowerCase().includes(query) ||
                node.className.toLowerCase().includes(query) ||
                node.packageName.toLowerCase().includes(query)
            );
        }

        // 5. 清理孤立的边
        const nodeIds = new Set(nodes.map(n => n.id));
        edges = edges.filter(e => nodeIds.has(e.source) && nodeIds.has(e.target));

        return { nodes, edges };
    }

    /**
     * 获取与入口方法相连的所有节点
     */
    getConnectedNodes(entryMethodId, edges) {
        const visited = new Set();
        const queue = [entryMethodId];

        while (queue.length > 0) {
            const current = queue.shift();
            if (visited.has(current)) continue;
            visited.add(current);

            // 找到所有相关的节点（双向查找）
            edges.forEach(edge => {
                if (edge.source === current && !visited.has(edge.target)) {
                    queue.push(edge.target);
                } else if (edge.target === current && !visited.has(edge.source)) {
                    queue.push(edge.source);
                }
            });
        }

        return Array.from(visited).map(id =>
            this.rawData.methodCallGraph.nodes.find(n => n.id === id)
        ).filter(Boolean);
    }

    /**
     * 计算节点调用次数
     */
    calculateNodeCallCounts(edges) {
        const counts = {};
        edges.forEach(edge => {
            counts[edge.source] = (counts[edge.source] || 0) + 1;
        });
        return counts;
    }
}

/**
 * 搜索管理器
 */
class SearchManager {
    constructor(normalizedData) {
        this.normalizedData = normalizedData;
        this.searchResults = [];
        this.selectedIndex = -1;
    }

    /**
     * 显示搜索结果
     */
    showSearchResults(query) {
        const resultsElement = document.getElementById('search-results');
        if (!resultsElement) return;

        // 执行搜索
        this.searchResults = this.performSearch(query);
        this.selectedIndex = -1;

        if (this.searchResults.length > 0) {
            // 生成搜索结果HTML
            resultsElement.innerHTML = this.generateSearchResultsHTML();
            resultsElement.style.display = 'block';
        } else {
            resultsElement.style.display = 'none';
        }
    }

    /**
     * 执行搜索
     */
    performSearch(query) {
        const lowerQuery = query.toLowerCase();
        return this.normalizedData.methodCallGraph.nodes
            .filter(node =>
                node.name.toLowerCase().includes(lowerQuery) ||
                node.className.toLowerCase().includes(lowerQuery) ||
                node.packageName.toLowerCase().includes(lowerQuery)
            )
            .slice(0, 20) // 限制结果数量
            .sort((a, b) => {
                // 优先匹配方法名，然后类名，然后包名
                const aNameMatch = a.name.toLowerCase().indexOf(lowerQuery);
                const bNameMatch = b.name.toLowerCase().indexOf(lowerQuery);
                if (aNameMatch !== bNameMatch) {
                    return aNameMatch - bNameMatch;
                }
                return a.name.localeCompare(b.name);
            });
    }

    /**
     * 生成搜索结果HTML
     */
    generateSearchResultsHTML() {
        const html = this.searchResults.map((method, index) => `
            <div class="search-result-item ${index === this.selectedIndex ? 'selected' : ''}"
                 data-method-id="${method.id}" data-index="${index}">
                <div class="method-name">${method.name}()</div>
                <div class="method-class">${method.className}</div>
                <div class="method-package">${method.packageName}</div>
            </div>
        `).join('');

        // 延迟绑定点击事件，确保DOM已更新
        setTimeout(() => {
            this.bindSearchResultEvents();
        }, 10);

        return html;
    }

    /**
     * 绑定搜索结果项的点击事件
     */
    bindSearchResultEvents() {
        const resultsElement = document.getElementById('search-results');
        if (!resultsElement) return;

        const items = resultsElement.querySelectorAll('.search-result-item');
        items.forEach(item => {
            item.onclick = () => {
                const methodId = item.getAttribute('data-method-id');
                const method = this.searchResults.find(m => m.id === methodId);
                if (method) {
                    this.selectMethod(method);
                }
            };
        });
    }

    /**
     * 隐藏搜索结果
     */
    hideSearchResults() {
        const resultsElement = document.getElementById('search-results');
        if (resultsElement) {
            resultsElement.style.display = 'none';
        }
        this.searchResults = [];
        this.selectedIndex = -1;
    }

    /**
     * 处理键盘导航
     */
    handleKeyNavigation(event) {
        const resultsElement = document.getElementById('search-results');
        if (!resultsElement || this.searchResults.length === 0) return;

        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                this.selectedIndex = Math.min(this.selectedIndex + 1, this.searchResults.length - 1);
                this.updateSearchSelection();
                break;
            case 'ArrowUp':
                event.preventDefault();
                this.selectedIndex = Math.max(this.selectedIndex - 1, -1);
                this.updateSearchSelection();
                break;
            case 'Enter':
                event.preventDefault();
                if (this.selectedIndex >= 0) {
                    this.selectMethod(this.searchResults[this.selectedIndex]);
                }
                break;
            case 'Escape':
                this.hideSearchResults();
                break;
        }
    }

    /**
     * 更新搜索选择状态
     */
    updateSearchSelection() {
        const resultsElement = document.getElementById('search-results');
        if (!resultsElement || this.searchResults.length === 0) return;

        const items = resultsElement.querySelectorAll('.search-result-item');
        items.forEach((item, index) => {
            if (index === this.selectedIndex) {
                item.classList.add('selected');
                item.scrollIntoView({ block: 'nearest' });
            } else {
                item.classList.remove('selected');
            }
        });

        // 更新搜索结果HTML以反映选择状态
        resultsElement.innerHTML = this.generateSearchResultsHTML();
    }

    /**
     * 选择方法
     */
    selectMethod(method) {
        // 更新入口方法选择器
        const entryMethodSelect = document.getElementById('entry-method-select');
        if (entryMethodSelect) {
            entryMethodSelect.value = method.id;
            // 触发change事件
            const changeEvent = new Event('change');
            entryMethodSelect.dispatchEvent(changeEvent);
        }

        // 更新搜索框
        const searchInput = document.getElementById('method-search-input');
        if (searchInput) {
            searchInput.value = `${method.className}.${method.name}()`;
        }

        // 隐藏搜索结果
        this.hideSearchResults();
    }
}

/**
 * 图形渲染器
 */
class GraphRenderer {
    constructor(container, nodeCount) {
        this.container = container;
        this.nodeCount = nodeCount;

        // 初始化自适应布局
        this.adaptiveLayout = new AdaptiveRadialLayout(nodeCount);

        // 创建G6图实例
        this.graph = this.createGraph();

        // 绑定容器事件
        this.bindContainerEvents();
    }

    /**
     * 创建G6图实例
     */
    createGraph() {
        return new G6.Graph({
            container: this.container,
            width: this.container.clientWidth,
            height: this.container.clientHeight,
            layout: this.adaptiveLayout.getLayoutConfig(),
            defaultNode: this.getDefaultNodeStyle(),
            defaultEdge: this.getDefaultEdgeStyle(),
            nodeStateStyles: this.getNodeStateStyles(),
            edgeStateStyles: this.getEdgeStateStyles(),
            modes: {
                default: [
                    {
                        type: 'drag-canvas',
                        enableOptimize: true,
                    },
                    {
                        type: 'zoom-canvas',
                        enableOptimize: true,
                        sensitivity: 1.2
                    }
                ]
            },
            // 优化性能
            renderer: 'canvas',
            fitView: true,
            fitViewPadding: [20, 40, 20, 40],
            animate: false,  // 暂时禁用动画避免兼容性问题
            // animateCfg: {
            //     duration: 500,
            //     easing: 'easeInOutQuart'
            // }
        });
    }

    /**
     * 获取默认节点样式
     */
    getDefaultNodeStyle() {
        return {
            type: 'circle',
            size: this.adaptiveLayout.config.nodeSize,
            style: {
                fill: '#1890ff',
                stroke: '#1890ff',
                lineWidth: 2,
                opacity: 0.9
            },
            labelCfg: {
                style: {
                    fill: '#333',
                    fontSize: Math.max(12, this.adaptiveLayout.config.nodeSize * 0.3),
                    fontWeight: 'bold'
                },
                position: 'bottom',
                offset: 5
            }
        };
    }

    /**
     * 获取默认边样式
     */
    getDefaultEdgeStyle() {
        return {
            type: 'polyline',
            style: {
                stroke: '#999',
                lineWidth: 2,
                endArrow: {
                    path: G6.Arrow.triangle(10, 12, 15),
                    fill: '#999'
                },
                opacity: 0.8
            },
            labelCfg: {
                autoRotate: true,
                style: {
                    fill: '#666',
                    fontSize: 10,
                    background: {
                        fill: '#fff',
                        stroke: '#999',
                        padding: [2, 4, 2, 4],
                        radius: 2
                    }
                }
            }
        };
    }

    /**
     * 获取节点状态样式
     */
    getNodeStateStyles() {
        return {
            hover: {
                lineWidth: 3,
                shadowColor: '#1890ff',
                shadowBlur: 15,
                shadowOffsetX: 0,
                shadowOffsetY: 0,
                opacity: 1
            },
            selected: {
                lineWidth: 4,
                shadowColor: '#ff4d4f',
                shadowBlur: 20,
                shadowOffsetX: 0,
                shadowOffsetY: 0,
                opacity: 1,
                stroke: '#ff4d4f'
            }
        };
    }

    /**
     * 获取边状态样式
     */
    getEdgeStateStyles() {
        return {
            hover: {
                lineWidth: 3,
                stroke: '#1890ff',
                opacity: 1
            },
            selected: {
                lineWidth: 4,
                stroke: '#ff4d4f',
                opacity: 1
            }
        };
    }

    /**
     * 更新数据
     */
    updateData(data) {
        if (!data || !data.nodes || !data.edges) return;

        // 转换节点样式
        const processedNodes = data.nodes.map(node => this.processNode(node));
        const processedEdges = data.edges.map(edge => this.processEdge(edge));

        // 更新图形数据
        this.graph.changeData({ nodes: processedNodes, edges: processedEdges });

        // 重新布局
        this.graph.layout();
    }

    /**
     * 处理节点数据
     */
    processNode(node) {
        const processedNode = { ...node };

        // 设置标签
        processedNode.label = node.name || 'Unknown';

        // 根据复杂度设置颜色
        const complexity = node.complexity || 0;
        if (complexity > 30) {
            processedNode.style = { fill: '#ff4d4f', stroke: '#ff4d4f' }; // 高复杂度 - 红色
        } else if (complexity >= 15) {
            processedNode.style = { fill: '#fa8c16', stroke: '#fa8c16' }; // 中复杂度 - 橙色
        } else {
            processedNode.style = { fill: '#52c41a', stroke: '#52c41a' }; // 低复杂度 - 绿色
        }

        // 外部方法特殊样式
        if (node.isExternal) {
            processedNode.style.dash = [5, 5];
            processedNode.type = 'circle';
        }

        // 入口方法特殊样式
        if (node.isEntryPoint) {
            processedNode.size = Math.max(this.adaptiveLayout.config.nodeSize * 1.2, 30);
            processedNode.style.strokeWidth = 3;
        }

        return processedNode;
    }

    /**
     * 处理边数据
     */
    processEdge(edge) {
        const processedEdge = { ...edge };

        // 设置边标签
        if (edge.callType) {
            processedEdge.label = edge.callType;
        }

        return processedEdge;
    }

    /**
     * 重置视图
     */
    resetView() {
        this.graph.fitView();
        this.graph.zoomTo(1);

        // 清除所有选中状态
        this.graph.clearAllStates();
    }

    /**
     * 导出图形
     */
    exportGraph(filename) {
        this.graph.downloadFullImage(filename, {
            backgroundColor: '#fff',
            padding: [30, 30, 30, 30]
        });
    }

    /**
     * 绑定容器事件
     */
    bindContainerEvents() {
        // 窗口大小变化时调整图形大小
        window.addEventListener('resize', () => {
            if (this.graph) {
                this.graph.changeSize(this.container.clientWidth, this.container.clientHeight);
            }
        });
    }
}

/**
 * 事件管理器
 */
class EventManager {
    constructor(graph) {
        this.graph = graph;
        this.nodeClickCallback = null;
    }

    /**
     * 设置事件处理器
     */
    setupEventHandlers(nodeClickCallback) {
        this.nodeClickCallback = nodeClickCallback;

        // 节点点击事件
        this.graph.on('node:click', (evt) => {
            const node = evt.item;
            const model = node.getModel();

            // 清除其他节点的选中状态
            this.graph.clearItemStates();

            // 设置当前节点为选中状态
            this.graph.setItemState(node, 'selected', true);

            // 高亮相关边
            this.highlightRelatedEdges(model.id);

            // 执行回调
            if (this.nodeClickCallback) {
                this.nodeClickCallback(model);
            }
        });

        // 节点悬停事件
        this.graph.on('node:mouseenter', (evt) => {
            const node = evt.item;
            if (!node.hasState('selected')) {
                this.graph.setItemState(node, 'hover', true);
            }
        });

        this.graph.on('node:mouseleave', (evt) => {
            const node = evt.item;
            if (!node.hasState('selected')) {
                this.graph.setItemState(node, 'hover', false);
            }
        });

        // 画布点击事件
        this.graph.on('canvas:click', () => {
            this.graph.clearAllStates();
        });
    }

    /**
     * 高亮相关边
     */
    highlightRelatedEdges(nodeId) {
        this.graph.getEdges().forEach(edge => {
            const model = edge.getModel();
            if (model.source === nodeId || model.target === nodeId) {
                this.graph.setItemState(edge, 'selected', true);
            }
        });
    }
}

/**
 * UI管理器
 */
class UIManager {
    constructor() {
        this.detailsPanel = document.getElementById('method-details-panel');
    }

    /**
     * 更新统计信息
     */
    updateStats(data, entryMethod) {
        // 更新节点数量
        const nodeCountElement = document.getElementById('node-count');
        if (nodeCountElement) {
            nodeCountElement.textContent = data.nodes.length;
        }

        // 更新边数量
        const edgeCountElement = document.getElementById('edge-count');
        if (edgeCountElement) {
            edgeCountElement.textContent = data.edges.length;
        }

        // 计算并更新复杂方法数量
        const complexMethodsCount = data.nodes.filter(node => (node.complexity || 0) > 30).length;
        const complexMethodsElement = document.getElementById('complex-methods-count');
        if (complexMethodsElement) {
            complexMethodsElement.textContent = complexMethodsCount;
        }

        // 计算并更新平均复杂度
        const avgComplexity = data.nodes.length > 0
            ? data.nodes.reduce((sum, node) => sum + (node.complexity || 0), 0) / data.nodes.length
            : 0;
        const avgComplexityElement = document.getElementById('avg-complexity');
        if (avgComplexityElement) {
            avgComplexityElement.textContent = avgComplexity.toFixed(1);
        }
    }

    /**
     * 显示方法详情
     */
    showMethodDetails(nodeModel, normalizedData) {
        if (!this.detailsPanel) return;

        // 查找相关的调用关系
        const methodCalls = normalizedData.methodCallGraph.edges.filter(e => e.source === nodeModel.id);
        const methodCalledBy = normalizedData.methodCallGraph.edges.filter(e => e.target === nodeModel.id);

        // 生成详情HTML
        const methodInfoHTML = this.generateMethodInfoHTML(nodeModel);
        const methodCallsHTML = this.generateMethodCallsHTML(methodCalls, normalizedData);
        const methodCalledByHTML = this.generateMethodCalledByHTML(methodCalledBy, normalizedData);

        // 更新面板内容
        const methodInfoElement = document.getElementById('method-info');
        const callsListElement = document.getElementById('calls-list');
        const calledByListElement = document.getElementById('called-by-list');

        if (methodInfoElement) methodInfoElement.innerHTML = methodInfoHTML;
        if (callsListElement) callsListElement.innerHTML = methodCallsHTML;
        if (calledByListElement) calledByListElement.innerHTML = methodCalledByHTML;

        // 显示面板
        this.detailsPanel.classList.add('show');

        // 绑定关闭按钮事件
        const closeBtn = document.getElementById('close-details');
        if (closeBtn) {
            closeBtn.onclick = () => this.hideMethodDetails();
        }
    }

    /**
     * 生成方法信息HTML
     */
    generateMethodInfoHTML(nodeModel) {
        const complexity = nodeModel.complexity || 0;
        const complexityLevel = complexity > 30 ? '高' : complexity >= 15 ? '中' : '低';
        const complexityColor = complexity > 30 ? '#ff4d4f' : complexity >= 15 ? '#fa8c16' : '#52c41a';

        return `
            <div class="method-signature">
                <strong class="method-name">${nodeModel.name}()</strong>
                <div class="method-class">${nodeModel.packageName}.${nodeModel.className}</div>
            </div>
            <div class="method-metrics">
                <div class="metric-item">
                    <span class="metric-label">复杂度:</span>
                    <span class="metric-value" style="color: ${complexityColor}">${complexity} (${complexityLevel})</span>
                </div>
                ${nodeModel.isEntryPoint ? '<div class="metric-item"><span class="metric-label">类型:</span><span class="metric-value">入口方法</span></div>' : ''}
                ${nodeModel.isExternal ? '<div class="metric-item"><span class="metric-label">来源:</span><span class="metric-value">外部依赖</span></div>' : ''}
            </div>
            ${nodeModel.description ? `<div class="method-description">${nodeModel.description}</div>` : ''}
        `;
    }

    /**
     * 生成方法调用列表HTML
     */
    generateMethodCallsHTML(methodCalls, normalizedData) {
        if (methodCalls.length === 0) {
            return '<div class="empty-message">无调用关系</div>';
        }

        return methodCalls.map(call => {
            const targetMethod = normalizedData.methodCallGraph.nodes.find(n => n.id === call.target);
            if (!targetMethod) return '';

            return `
                <div class="call-item">
                    <div class="call-target">${targetMethod.className}.${targetMethod.name}()</div>
                    <div class="call-type">${call.callType || '调用'}</div>
                </div>
            `;
        }).join('');
    }

    /**
     * 生成被调用关系列表HTML
     */
    generateMethodCalledByHTML(methodCalledBy, normalizedData) {
        if (methodCalledBy.length === 0) {
            return '<div class="empty-message">无被调用关系</div>';
        }

        return methodCalledBy.map(call => {
            const sourceMethod = normalizedData.methodCallGraph.nodes.find(n => n.id === call.source);
            if (!sourceMethod) return '';

            return `
                <div class="call-item">
                    <div class="call-source">${sourceMethod.className}.${sourceMethod.name}()</div>
                    <div class="call-type">${call.callType || '被调用'}</div>
                </div>
            `;
        }).join('');
    }

    /**
     * 隐藏方法详情
     */
    hideMethodDetails() {
        if (this.detailsPanel) {
            this.detailsPanel.classList.remove('show');
        }
    }

    /**
     * 显示成功消息
     */
    showSuccess(message) {
        // 这里可以实现一个简单的消息提示
        console.log('Success:', message);
        // 或者使用更好的提示组件
        this.showToast(message, 'success');
    }

    /**
     * 显示错误消息
     */
    showError(message) {
        console.error('Error:', message);
        this.showToast(message, 'error');
    }

    /**
     * 显示消息提示
     */
    showToast(message, type = 'info') {
        // 创建消息元素
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        toast.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 12px 20px;
            background: ${type === 'error' ? '#ff4d4f' : type === 'success' ? '#52c41a' : '#1890ff'};
            color: white;
            border-radius: 4px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.15);
            z-index: 10000;
            animation: slideIn 0.3s ease-out;
        `;

        document.body.appendChild(toast);

        // 自动移除
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => {
                if (document.body.contains(toast)) {
                    document.body.removeChild(toast);
                }
            }, 300);
        }, 3000);
    }
}

/**
 * 初始化包级视图
 */
function initializePackageView() {
    try {
        const container = document.getElementById('package-graph');
        if (!container) {
            console.warn('未找到包级图形容器');
            return;
        }

        const dataElement = document.getElementById('analysis-data');
        if (!dataElement) {
            throw new Error('找不到分析数据元素');
        }

        const rawData = JSON.parse(dataElement.textContent);
        const packageData = rawData.packageGraph || { nodes: [], edges: [] };

        if (packageData.nodes.length === 0) {
            container.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: center; height: 400px; flex-direction: column; color: #666;">
                    <i class="fas fa-folder-open" style="font-size: 48px; margin-bottom: 16px; color: #1890ff;"></i>
                    <h3>无包级依赖数据</h3>
                    <p>当前项目中未发现包级依赖关系</p>
                </div>
            `;
            return;
        }

        // 创建包级G6图
        const packageGraph = new G6.Graph({
            container: container,
            width: container.clientWidth,
            height: container.clientHeight,
            layout: {
                type: 'dagre',
                rankdir: 'TB',
                nodesep: 50,
                ranksep: 100
            },
            defaultNode: {
                type: 'circle',
                size: 40,
                style: {
                    fill: '#1890ff',
                    stroke: '#1890ff',
                    lineWidth: 2,
                },
                labelCfg: {
                    style: {
                        fill: '#fff',
                        fontSize: 12,
                        fontWeight: 'bold'
                    }
                }
            },
            defaultEdge: {
                type: 'polyline',
                style: {
                    stroke: '#999',
                    lineWidth: 2,
                    endArrow: {
                        path: G6.Arrow.triangle(10, 12, 15),
                        fill: '#999'
                    }
                }
            },
            modes: {
                default: ['drag-canvas', 'zoom-canvas']
            }
        });

        // 处理包数据
        const processedNodes = packageData.nodes.map(node => ({
            id: node.id,
            label: node.name || node.id,
            style: {
                fill: node.packageName ? '#52c41a' : '#1890ff',
                stroke: node.packageName ? '#52c41a' : '#1890ff',
            }
        }));

        const processedEdges = packageData.edges.map(edge => ({
            id: `${edge.source}-${edge.target}`,
            source: edge.source,
            target: edge.target,
            label: edge.dependencyType || 'depends on'
        }));

        packageGraph.data({ nodes: processedNodes, edges: processedEdges });
        packageGraph.render();

        console.log('包级视图初始化成功');

    } catch (error) {
        console.error('包级视图初始化失败:', error);
        const container = document.getElementById('package-graph');
        if (container) {
            container.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: center; height: 400px; flex-direction: column; color: #666;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 48px; margin-bottom: 16px; color: #ff4d4f;"></i>
                    <h3>包级视图初始化失败</h3>
                    <p>${error.message}</p>
                </div>
            `;
        }
    }
}

/**
 * 初始化类级视图
 */
function initializeClassView() {
    try {
        const container = document.getElementById('class-graph');
        if (!container) {
            console.warn('未找到类级图形容器');
            return;
        }

        const dataElement = document.getElementById('analysis-data');
        if (!dataElement) {
            throw new Error('找不到分析数据元素');
        }

        const rawData = JSON.parse(dataElement.textContent);
        const classData = rawData.classGraph || { nodes: [], edges: [] };

        if (classData.nodes.length === 0) {
            container.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: center; height: 400px; flex-direction: column; color: #666;">
                    <i class="fas fa-cube" style="font-size: 48px; margin-bottom: 16px; color: #52c41a;"></i>
                    <h3>无类级依赖数据</h3>
                    <p>当前项目中未发现类级依赖关系</p>
                </div>
            `;
            return;
        }

        // 创建类级G6图
        const classGraph = new G6.Graph({
            container: container,
            width: container.clientWidth,
            height: container.clientHeight,
            layout: {
                type: 'force',
                preventOverlap: true,
                nodeSize: 40,
                linkDistance: 150,
                nodeStrength: -50,
                edgeStrength: 0.1
            },
            defaultNode: {
                type: 'circle',
                size: 45,
                style: {
                    fill: '#722ed1',
                    stroke: '#722ed1',
                    lineWidth: 2,
                },
                labelCfg: {
                    style: {
                        fill: '#fff',
                        fontSize: 11,
                        fontWeight: 'bold'
                    },
                    position: 'center'
                }
            },
            defaultEdge: {
                type: 'line',
                style: {
                    stroke: '#999',
                    lineWidth: 2,
                    endArrow: {
                        path: G6.Arrow.triangle(8, 10, 12),
                        fill: '#999'
                    }
                }
            },
            modes: {
                default: ['drag-canvas', 'zoom-canvas']
            }
        });

        // 处理类数据
        const processedNodes = classData.nodes.map(node => {
            // 根据复杂度设置颜色
            const complexity = node.complexity || 0;
            let color = '#722ed1';
            if (complexity > 30) color = '#ff4d4f';
            else if (complexity > 15) color = '#fa8c16';
            else if (complexity > 0) color = '#52c41a';

            return {
                id: node.id,
                label: node.name || node.id,
                style: {
                    fill: color,
                    stroke: color,
                },
                data: node // 保存原始数据供点击使用
            };
        });

        const processedEdges = classData.edges.map(edge => ({
            id: `${edge.source}-${edge.target}`,
            source: edge.source,
            target: edge.target,
            label: edge.relationshipType || 'association'
        }));

        classGraph.data({ nodes: processedNodes, edges: processedEdges });

        // 添加点击事件
        classGraph.on('node:click', (evt) => {
            const node = evt.item;
            const model = node.getModel();
            showClassDetails(model.data);
        });

        classGraph.render();

        console.log('类级视图初始化成功');

    } catch (error) {
        console.error('类级视图初始化失败:', error);
        const container = document.getElementById('class-graph');
        if (container) {
            container.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: center; height: 400px; flex-direction: column; color: #666;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 48px; margin-bottom: 16px; color: #ff4d4f;"></i>
                    <h3>类级视图初始化失败</h3>
                    <p>${error.message}</p>
                </div>
            `;
        }
    }
}

/**
 * 显示类详情
 */
function showClassDetails(classData) {
    const detailsContainer = document.getElementById('class-details');
    if (!detailsContainer || !classData) return;

    const complexity = classData.complexity || 0;
    const complexityLevel = complexity > 30 ? '高' : complexity >= 15 ? '中' : '低';
    const complexityColor = complexity > 30 ? '#ff4d4f' : complexity >= 15 ? '#fa8c16' : '#52c41a';

    detailsContainer.innerHTML = `
        <div class="class-detail-item">
            <strong>类名:</strong> ${classData.className || classData.name || classData.id}
        </div>
        <div class="class-detail-item">
            <strong>包名:</strong> ${classData.packageName || 'N/A'}
        </div>
        <div class="class-detail-item">
            <strong>复杂度:</strong>
            <span style="color: ${complexityColor}; font-weight: bold;">${complexity} (${complexityLevel})</span>
        </div>
        <div class="class-detail-item">
            <strong>方法数量:</strong> ${classData.methodCount || 0}
        </div>
        <div class="class-detail-item">
            <strong>字段数量:</strong> ${classData.fieldCount || 0}
        </div>
        ${classData.description ? `
            <div class="class-detail-item">
                <strong>描述:</strong> ${classData.description}
            </div>
        ` : ''}
    `;
}

/**
 * 主入口函数
 */
function initializeUnifiedMethodView() {
    try {
        // 获取数据
        const dataElement = document.getElementById('analysis-data');
        if (!dataElement) {
            throw new Error('找不到分析数据元素');
        }

        const rawData = JSON.parse(dataElement.textContent);

        // 获取容器
        const container = document.getElementById('method-graph');
        if (!container) {
            throw new Error('找不到方法图形容器');
        }

        // 创建统一方法视图实例
        const unifiedMethodView = new UnifiedMethodView(container, rawData);

        // 暴露到全局作用域以便调试
        window.unifiedMethodView = unifiedMethodView;

        console.log('统一方法视图初始化成功');
        return unifiedMethodView;

    } catch (error) {
        console.error('初始化统一方法视图失败:', error);

        // 显示错误信息
        const container = document.getElementById('method-graph');
        if (container) {
            container.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: center; height: 400px; flex-direction: column; color: #666;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 48px; margin-bottom: 16px; color: #ff4d4f;"></i>
                    <h3>初始化失败</h3>
                    <p>${error.message}</p>
                </div>
            `;
        }

        // 隐藏加载遮罩
        const loadingOverlay = document.getElementById('loading-overlay');
        if (loadingOverlay) {
            loadingOverlay.style.display = 'none';
        }

        return null;
    }
}

/**
 * 初始化所有视图
 */
function initializeAllViews() {
    try {
        console.log('开始初始化所有视图...');

        // 延迟初始化确保DOM完全加载
        setTimeout(() => {
            initializePackageView();
            initializeClassView();
            initializeUnifiedMethodView();
        }, 300);

        console.log('所有视图初始化完成');

    } catch (error) {
        console.error('视图初始化失败:', error);
    }
}

/**
 * 页面加载完成后初始化
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM 加载完成，开始初始化所有视图...');

    // 延迟初始化确保所有资源加载完成
    setTimeout(() => {
        initializeAllViews();
    }, 100);
});