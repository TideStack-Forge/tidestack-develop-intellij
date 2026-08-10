# IDE Debug Integration - 部署和测试指南

## 概述

本文档提供 IDE 调试集成功能的完整部署和测试流程。

## 前置条件

- Java 17+
- Maven 3.6+
- IntelliJ IDEA 2023.1+
- Docker（用于容器测试）
- 网络连接（用于下载依赖）

## 一、平台端部署

### 1.1 编译平台

```bash
# 进入 worktree 目录
cd /Users/songmingxu/Projects/ouroboros/.worktrees/codex/ide-debug-integration

# 编译 dev-module（包含新功能）
mvn clean install -pl dev-module/ouroboros-dev-app-management -am -Dmaven.test.skip=false

# 编译完整平台
mvn clean install -pl platform/ouroboros-develop -am -Dmaven.test.skip=true
```

**注意**：如果遇到 Jabel 插件问题，可以：
- 临时禁用 Jabel：`mvn clean install -Djabel.javac=javac`
- 或使用主分支已编译的 jar

### 1.2 启动平台服务

```bash
# 方式 1：使用 Maven
cd platform/ouroboros-develop
mvn spring-boot:run -Dspring-boot.run.profiles=debug

# 方式 2：使用 jar
java -jar platform/ouroboros-develop/target/ouroboros-develop-1.0.0-beta.2.jar \
  --spring.profiles.active=debug
```

### 1.3 验证平台服务

访问 http://localhost:8080，确认：
- 平台正常启动
- 可以登录管理界面
- 容器管理页面可访问

### 1.4 创建测试应用和容器

1. 登录平台
2. 创建测试应用（如果不存在）
3. 创建测试容器（profile: test）
4. 记录容器 ID

## 二、IDEA 插件部署

### 2.1 解决 Gradle Wrapper 问题

**问题**：`gradle/wrapper/gradle-wrapper.jar` 缺失

**解决方案 1**：手动下载（推荐）

```bash
cd ide-plugins/intellij-plugin

# 下载 Gradle wrapper jar
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar

# 或使用 wget
wget -O gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar
```

**解决方案 2**：使用系统 Gradle

```bash
# 安装 Gradle（如果未安装）
brew install gradle

# 使用系统 Gradle 构建
cd ide-plugins/intellij-plugin
gradle wrapper --gradle-version 8.4
```

**解决方案 3**：从其他项目复制

```bash
# 从其他 Gradle 项目复制 wrapper jar
cp /path/to/other/gradle/project/gradle/wrapper/gradle-wrapper.jar \
  ide-plugins/intellij-plugin/gradle/wrapper/
```

### 2.2 编译插件

```bash
cd ide-plugins/intellij-plugin

# 构建插件
./gradlew build

# 输出位置
# build/distributions/tidestack-develop-0.1.0.zip
```

### 2.3 安装插件到 IDEA

**方式 1**：通过 IDEA 界面安装

1. 打开 IDEA
2. Settings > Plugins > ⚙️ > Install Plugin from Disk...
3. 选择 `ide-plugins/intellij-plugin/build/distributions/tidestack-develop-0.1.0.zip`
4. 重启 IDEA

**方式 2**：通过命令行安装

```bash
# 复制到 IDEA 插件目录
cp ide-plugins/intellij-plugin/build/distributions/tidestack-develop-0.1.0.zip \
  ~/Library/Application\ Support/JetBrains/IntelliJIdea2023.1/plugins/

# 解压
cd ~/Library/Application\ Support/JetBrains/IntelliJIdea2023.1/plugins/
unzip tidestack-develop-0.1.0.zip

# 重启 IDEA
```

### 2.4 配置插件

1. 打开 IDEA
2. Settings > Tools > Ouroboros
3. 配置参数：
   - Platform URL: `http://localhost:8080`
   - App Name: `your-app-name`
   - Profile: `test`
   - Dev Key: `（留空，稍后生成）`

## 三、端到端测试

### 3.1 生成 Dev Key

**方式 1**：通过平台 UI

1. 登录平台
2. 进入容器管理页面
3. 找到测试容器
4. 点击"调试密钥"按钮
5. 点击"生成新密钥"
6. 复制生成的 Dev Key

**方式 2**：通过 API

```bash
# 生成 Dev Key
curl -X POST http://localhost:8080/api/dev/your-app-name/dev-keys \
  -H "Content-Type: application/json" \
  -d '{
    "expiryDays": 90,
    "description": "IDEA Plugin Test"
  }'

# 响应示例
{
  "code": 0,
  "data": {
    "devKey": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "appName": "your-app-name",
    "profileId": "test",
    "expiresAt": "2026-07-11T00:00:00Z"
  }
}
```

### 3.2 配置 Dev Key

1. 复制生成的 Dev Key
2. 打开 IDEA Settings > Tools > Ouroboros
3. 粘贴到 Dev Key 字段
4. 点击 Apply

### 3.3 准备测试项目

1. 在 IDEA 中打开一个 Maven 项目
2. 确保项目可以正常编译
3. 确保项目输出 jar 文件

### 3.4 执行一键部署调试

1. 在 IDEA 工具栏找到"Deploy & Debug"按钮（🚀 图标）
2. 点击按钮
3. 观察通知：
   - "Building project..." - 项目构建中
   - "Uploading jar..." - jar 上传中
   - "Restarting container..." - 容器重启中
   - "Creating debug session..." - 创建调试会话
   - "Attaching debugger..." - 附加调试器
   - "Debug session started" - 调试会话已启动

### 3.5 验证调试功能

1. 在代码中设置断点
2. 触发断点（通过 API 调用或其他方式）
3. 确认 IDEA 停在断点处
4. 验证调试功能：
   - 查看变量
   - 单步执行
   - 表达式求值
   - 调用栈

### 3.6 测试错误处理

**测试场景 1**：无效的 Dev Key

1. 修改 Dev Key 为无效值
2. 点击"Deploy & Debug"
3. 预期：显示错误通知"Invalid dev key"

**测试场景 2**：容器不存在

1. 修改 App Name 为不存在的应用
2. 点击"Deploy & Debug"
3. 预期：显示错误通知"Container not found"

**测试场景 3**：jar 文件不存在

1. 清理项目输出目录
2. 点击"Deploy & Debug"（不先构建）
3. 预期：显示错误通知"Jar file not found"

**测试场景 4**：网络错误

1. 停止平台服务
2. 点击"Deploy & Debug"
3. 预期：显示错误通知"Failed to connect to platform"

### 3.7 测试 Dev Key 管理

**列出所有 Dev Keys**

```bash
curl http://localhost:8080/api/dev/your-app-name/dev-keys
```

**验证 Dev Key**

```bash
curl -X POST http://localhost:8080/api/dev/your-app-name/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "devKey": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  }'
```

**撤销 Dev Key**

```bash
curl -X DELETE http://localhost:8080/api/dev/your-app-name/dev-keys
```

## 四、故障排查

### 4.1 平台端问题

**问题**：平台启动失败

```bash
# 检查日志
tail -f platform/ouroboros-develop/logs/ouroboros.log

# 检查端口占用
lsof -i :8080

# 检查 Java 版本
java -version  # 应该是 17+
```

**问题**：API 返回 404

```bash
# 检查 Controller 是否注册
curl http://localhost:8080/actuator/mappings | grep debug-keys

# 检查 Spring Boot 配置
cat platform/ouroboros-develop/src/main/resources/application.yml
```

**问题**：Dev Key 生成失败

```bash
# 检查工作空间目录权限
ls -la ~/.ouroboros/workspaces/

# 检查日志
grep "DebugKeyGenerationService" platform/ouroboros-develop/logs/ouroboros.log
```

### 4.2 IDEA 插件问题

**问题**：插件未显示在工具栏

1. 检查插件是否已安装：Settings > Plugins
2. 检查插件是否已启用
3. 重启 IDEA

**问题**：配置界面无法打开

1. 检查 IDEA 日志：Help > Show Log in Finder
2. 搜索 "OuroborosConfigurable" 相关错误
3. 检查插件版本兼容性

**问题**：Deploy & Debug 按钮无响应

1. 检查 IDEA 日志
2. 检查配置是否完整（URL、App Name、Profile、Dev Key）
3. 检查网络连接

**问题**：jar 文件找不到

```bash
# 检查项目输出目录
ls -la target/*.jar

# 检查 Maven 配置
cat pom.xml | grep packaging
```

**问题**：远程调试无法附加

1. 检查容器是否启动调试端口
2. 检查调试端口是否可访问
3. 检查 IDEA 调试配置

### 4.3 网络问题

**问题**：无法连接平台

```bash
# 测试连接
curl http://localhost:8080/actuator/health

# 检查防火墙
sudo pfctl -s rules | grep 8080

# 检查 hosts 文件
cat /etc/hosts
```

**问题**：jar 上传超时

1. 检查 jar 文件大小
2. 增加超时时间（在插件配置中）
3. 检查网络带宽

## 五、性能测试

### 5.1 测试指标

- jar 上传时间（目标：< 10s for 50MB jar）
- 容器重启时间（目标：< 30s）
- 调试会话创建时间（目标：< 5s）
- 调试器附加时间（目标：< 3s）
- 端到端总时间（目标：< 60s）

### 5.2 测试方法

```bash
# 记录开始时间
start_time=$(date +%s)

# 执行 Deploy & Debug
# （通过 IDEA 界面）

# 记录结束时间
end_time=$(date +%s)

# 计算耗时
echo "Total time: $((end_time - start_time)) seconds"
```

### 5.3 性能优化建议

1. **jar 上传优化**
   - 使用增量上传（只上传变更的类）
   - 压缩传输
   - 并行上传多个文件

2. **容器重启优化**
   - 使用热重载（Spring DevTools）
   - 减少启动时间（延迟初始化）
   - 使用容器快照

3. **调试会话优化**
   - 复用现有会话
   - 预创建调试端口
   - 使用连接池

## 六、安全测试

### 6.1 Dev Key 安全性

**测试 1**：过期 Key 拒绝

```bash
# 生成一个已过期的 Key（修改数据库）
# 尝试使用过期 Key
# 预期：返回 401 Unauthorized
```

**测试 2**：撤销 Key 拒绝

```bash
# 生成 Key
# 撤销 Key
# 尝试使用撤销的 Key
# 预期：返回 401 Unauthorized
```

**测试 3**：跨应用访问拒绝

```bash
# 使用 app1 的 Key 访问 app2 的容器
# 预期：返回 403 Forbidden
```

**测试 4**：Key 泄露检测

```bash
# 检查 Key 是否记录在日志中
grep -r "devKey" platform/ouroboros-develop/logs/

# 预期：不应该有明文 Key
```

### 6.2 API 安全性

**测试 1**：未授权访问

```bash
# 不提供 Dev Key
curl http://localhost:8080/api/dev/containers/xxx/restart

# 预期：返回 401 Unauthorized
```

**测试 2**：SQL 注入

```bash
# 尝试 SQL 注入
curl "http://localhost:8080/api/dev/%27%3B%20DROP%20TABLE%20debug_keys%3B%20--/dev-keys"

# 预期：返回 400 Bad Request 或正常处理（参数化查询）
```

**测试 3**：路径遍历

```bash
# 尝试路径遍历
curl -X POST http://localhost:8080/api/dev/containers/../../etc/passwd/restart

# 预期：返回 400 Bad Request
```

## 七、文档和交付

### 7.1 用户文档

创建以下文档：

1. **快速开始指南**
   - 5 分钟内完成首次部署调试
   - 截图和视频演示

2. **配置指南**
   - 详细配置说明
   - 常见配置场景

3. **故障排查指南**
   - 常见问题和解决方案
   - 日志分析方法

4. **最佳实践**
   - 推荐的工作流程
   - 性能优化建议
   - 安全注意事项

### 7.2 开发者文档

创建以下文档：

1. **架构设计文档**
   - 系统架构图
   - 组件交互图
   - 数据流图

2. **API 文档**
   - REST API 规范
   - 请求/响应示例
   - 错误码说明

3. **扩展指南**
   - 如何添加新功能
   - 如何自定义行为
   - 插件开发指南

4. **测试指南**
   - 测试策略
   - 测试用例
   - 自动化测试

### 7.3 交付清单

- [ ] 平台端代码（已完成）
- [ ] 前端 UI（已完成）
- [ ] IDEA 插件代码（已完成）
- [ ] 单元测试（已完成）
- [ ] 集成测试（已完成）
- [ ] 端到端测试（待完成）
- [ ] 用户文档（待完成）
- [ ] 开发者文档（待完成）
- [ ] 部署脚本（待完成）
- [ ] 监控和告警（待完成）

## 八、后续改进

### 8.1 短期改进（1-2 周）

1. **增量部署**
   - 只上传变更的类文件
   - 使用类加载器热替换

2. **批量操作**
   - 同时部署到多个容器
   - 批量重启容器

3. **调试会话管理**
   - 查看活跃的调试会话
   - 手动断开调试会话
   - 调试会话超时自动清理

### 8.2 中期改进（1-2 月）

1. **高级调试功能**
   - 条件断点同步
   - 远程表达式求值
   - 远程代码热替换

2. **性能监控**
   - 部署耗时统计
   - 调试会话统计
   - 资源使用监控

3. **团队协作**
   - 共享调试会话
   - 调试会话权限管理
   - 调试活动通知

### 8.3 长期改进（3-6 月）

1. **多 IDE 支持**
   - VS Code 插件
   - Eclipse 插件
   - 其他 JetBrains IDE

2. **云端调试**
   - 支持远程 Kubernetes 集群
   - 支持云端容器服务
   - 支持多云环境

3. **智能调试**
   - 自动断点建议
   - 异常自动捕获
   - 性能瓶颈自动识别

## 九、总结

本指南提供了 IDE 调试集成功能的完整部署和测试流程。按照本指南操作，可以：

1. 成功部署平台端和 IDEA 插件
2. 完成端到端功能测试
3. 验证安全性和性能
4. 准备生产环境部署

如有问题，请参考故障排查章节或联系开发团队。
