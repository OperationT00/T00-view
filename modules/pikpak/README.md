# PikPak 独立模块

Java 21 独立 Maven 工程，产物 `com.example.pikpak:pikpak-module:0.1.0`。只包含 PikPak 分享管理、目录读取和播放源解析业务；不依赖宿主工程、Spring、用户、角色、收藏、标签或名称解析器。

## 当前交付

- `PikPakModule`：对外门面，添加／批量添加、去重、搜索分页、版本编辑删除、目录读取和播放源解析调度。
- `PikPakShareLink`：标准分享URL识别和规范化；拒绝伪域名、异常路径和未经支持的参数，不联网。
- `PikPakShareGateway`：PikPak网络接口边界，负责把上游目录和媒体转换为模块DTO。
- `PikPakShareStore`：原子持久化契约，支持分享身份唯一约束、版本检查和提取码保留／替换／清除。
- `InMemoryPikPakShareStore`：供独立嵌入、演示和测试的内存实现，进程结束后丢失数据，不是生产持久化。
- 类型与错误均为本模块所有；返回分享记录不含提取码，媒体诊断字符串隐藏临时地址。

**本工程交付业务核心，不单独提供网页。** 相邻的 `modules/pikpak-web` 已加入真实HTTP、JDBC、公共API适配，`frontend/src/modules/pikpak` 已加入管理、目录及播放器页面，用户提供的普通短视频已在本机网页播放到结尾，完整文件分段读取及解码通过。核心测试使用Gateway替身；浏览器拖动、刷新续播和清除进度已验证，长视频和部署网络仍待验收，详见 [验证记录](../../docs/pikpak-playback-verification.md)。

## 依赖方向

```text
前端 PikPak 页面 → PikPak Web 适配器 → PikPakModule
                                       ↓        ↓
                              PikPakShareGateway PikPakShareStore
                                       ↑        ↑
                              PikPak HTTP适配器  PikPak MySQL适配器
```

适配器由相邻的 `pikpak-web` 工程管理，只通过端口接入。宿主只负责装配、公共路由和部署配置；核心不能反向调用宿主服务。提取码的持久化加密由存储适配器处理，密钥由部署配置注入。

不提前抽象通用网盘平台，也不添加百度网盘、本地硬盘、标签推断、账号转存或自动删除。

## 独立构建

在已配置 JDK 21 与 Maven 的环境运行：

```sh
mvn -f modules/pikpak/pom.xml clean verify
```

不要求启动宿主、数据库或前端。产物为 `modules/pikpak/target/pikpak-module-0.1.0.jar`。运行时依赖只有JDK，JUnit仅用于测试；没有执行时偷偷调用PikPak或写宿主数据库的初始化逻辑。

## 调用约定

构造 `PikPakModule(store, gateway, clock)` 后，宿主可调用 `add`、`addBatch`、`get`、`list`、`update`、`delete`、`browse`、`playback`。没有用户名／角色参数，所有使用者采用相同业务规则。

- `add`不自动联网，重复链接返回原记录，不覆盖现有名称／提取码。
- `update`要求版本一致；提取码null表示保留，空字符串表示清除。
- `delete`只删除本站记录，没有云盘删除接口。
- `browse`只读一页，网络适配器必须验证目录归属，模块检查响应上限和重复游标。
- `playback`要求适配器确认文件属于分享，排除明确过期的媒体；返回的是服务端媒体描述，不代表浏览器已经播放成功。
- 网络请求结束后复核分享版本，防止提取码更新或记录删除后返回过时结果。

## 后续实现位置

1. `com.example.pikpak.adapter.http`：真实PikPak请求、验证、目录分页、媒体映射；不将网页签名细节塞进门面。
2. `com.example.pikpak.adapter.jdbc`：本模块专有表和迁移，数据库连接由外部提供，无宿主业务外键。
3. Web适配和 `frontend/src/modules/pikpak/`：公共API和页面，禁止引用宿主auth状态作为观看前提；沿用功能设计中的ArtPlayer。
4. 原型实测：目录、声画、后半段与结尾完整性；接口替身测试不能替代真实播放。

完整功能范围见 [分享管理与观看设计](../../docs/pikpak-share-playback-design.md)。
