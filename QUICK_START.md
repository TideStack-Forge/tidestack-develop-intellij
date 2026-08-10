# IDEA 插件快速构建指南

## 问题：Gradle Wrapper Jar 缺失

由于网络限制，`gradle/wrapper/gradle-wrapper.jar` 无法自动下载。

## 解决方案

### 方案 1：手动下载 Wrapper Jar（推荐）

```bash
cd ide-plugins/intellij-plugin

# 使用 curl 下载
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar

# 或使用 wget
wget -O gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar

# 或从 Gradle 官方下载完整 wrapper
curl -L -o gradle-wrapper.zip \
  https://services.gradle.org/distributions/gradle-8.4-bin.zip
unzip gradle-wrapper.zip
cp gradle-8.4/lib/plugins/gradle-wrapper-*.jar gradle/wrapper/gradle-wrapper.jar
```

### 方案 2：从其他项目复制

如果你有其他使用 Gradle 8.x 的项目：

```bash
# 找到其他项目的 wrapper jar
find ~ -name "gradle-wrapper.jar" -path "*/gradle-8.*/wrapper/*" 2>/dev/null | head -1

# 复制到当前项目
cp /path/to/other/project/gradle/wrapper/gradle-wrapper.jar \
  ide-plugins/intellij-plugin/gradle/wrapper/
```

### 方案 3：使用系统 Gradle

```bash
# 安装 Gradle（如果未安装）
brew install gradle

# 初始化 wrapper
cd ide-plugins/intellij-plugin
gradle wrapper --gradle-version 8.4

# 这会自动下载并配置 wrapper jar
```

### 方案 4：离线下载（最可靠）

1. 在有网络的机器上下载：
   ```bash
   curl -L -O https://services.gradle.org/distributions/gradle-8.4-bin.zip
   ```

2. 解压并提取 wrapper jar：
   ```bash
   unzip gradle-8.4-bin.zip
   cp gradle-8.4/lib/plugins/gradle-wrapper-8.4.jar gradle-wrapper.jar
   ```

3. 传输到目标机器并放置到正确位置：
   ```bash
   cp gradle-wrapper.jar ide-plugins/intellij-plugin/gradle/wrapper/gradle-wrapper.jar
   ```

## 构建插件

一旦 wrapper jar 就位：

```bash
cd ide-plugins/intellij-plugin

# 构建插件
./gradlew build

# 输出位置
ls -lh build/distributions/tidestack-develop-0.1.0.zip
```

## 安装插件

### 方式 1：通过 IDEA 界面

1. 打开 IntelliJ IDEA
2. File > Settings > Plugins
3. 点击 ⚙️ > Install Plugin from Disk...
4. 选择 `build/distributions/tidestack-develop-0.1.0.zip`
5. 重启 IDEA

### 方式 2：命令行安装

```bash
# 复制到 IDEA 插件目录
IDEA_PLUGINS=~/Library/Application\ Support/JetBrains/IntelliJIdea2023.1/plugins
cp build/distributions/tidestack-develop-0.1.0.zip "$IDEA_PLUGINS/"

# 解压
cd "$IDEA_PLUGINS"
unzip -o tidestack-develop-0.1.0.zip

# 重启 IDEA
```

## 验证安装

1. 重启 IDEA 后，检查 Settings > Plugins
2. 搜索 "Ouroboros"，应该看到已安装的插件
3. 检查 Settings > Tools > Ouroboros，应该有配置界面
4. 工具栏应该有 "Deploy & Debug" 按钮（🚀 图标）

## 开发模式运行

如果需要在开发模式下测试插件：

```bash
cd ide-plugins/intellij-plugin

# 启动 IDEA 沙箱环境
./gradlew runIde

# 这会启动一个新的 IDEA 实例，插件已自动安装
```

## 故障排查

### 问题：./gradlew 权限不足

```bash
chmod +x gradlew
```

### 问题：Gradle 版本不兼容

检查 `gradle/wrapper/gradle-wrapper.properties`：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
```

确保版本是 8.4。

### 问题：构建失败

```bash
# 清理并重新构建
./gradlew clean build --refresh-dependencies

# 查看详细日志
./gradlew build --info
```

### 问题：插件无法加载

检查 IDEA 日志：

```bash
# macOS
tail -f ~/Library/Logs/JetBrains/IntelliJIdea2023.1/idea.log

# 搜索错误
grep -i "ouroboros" ~/Library/Logs/JetBrains/IntelliJIdea2023.1/idea.log
```

## 下一步

安装完成后，参考以下文档：

- [E2E_TESTING_GUIDE.md](E2E_TESTING_GUIDE.md) - IDE 调试集成端到端测试流程
- [../DEPLOYMENT_GUIDE.md](../docs/DEPLOYMENT_GUIDE.md) - Ouroboros 平台部署指南

主要步骤：
1. 配置插件连接平台
2. 生成 Dev Key
3. 执行端到端测试
