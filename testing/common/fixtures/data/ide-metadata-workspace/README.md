# IDE 编辑器共享文档

`documents.json` 供 VS Code Extension Host、Kotlin 页面导出、IntelliJ Platform/JCEF 与 Playwright 共同消费。样本验证页面外层未知字段、Form 省略默认属性、inline 弹窗、共享引用与原生文档操作；不定义新的元数据类型或创建规则。创建规则仍来自 `ide-plugins/common/metadata-editor-schema/metadataCreationCases.json`。

每例在独立工作区构造文档，由正式宿主维护修订并保存。执行、预检、生命周期与通过标准见 [场景运行手册](../../../../integration/regression/ide/metadata-editors/harness.playbook.md)。样本变更需同步场景断言，不能用接受新快照来掩盖隐式写入。

`business` 样本包含五种 CRUD 的 Groovy 执行前脚本和 JavaScript 执行后脚本。测试通过正式业务模型页面展开各字段，检查 Monaco、语言、独立保存、完整代码上下文及离线编辑；JCEF 也消费此样本。

`logicflow` 样本使用正式 `const`、`script`、`return` 节点与两条连线；常量节点作为流程入口。在线协议夹具返回 VS Code 正式构建产物的完整节点目录，离线测试直接使用各宿主随包快照，不维护测试专用的节点定义。脚本属性和 `logicflow.script` 上下文均来自真实目录。浏览器夹具通过 HTTP 服务返回受控 LSP 提示、诊断，便于在两个宿主复用相同断言。
