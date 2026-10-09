# 藏页 · Media Vault

> 2026-10-09 范围调整：当前聚焦 PikPak 分享链接路由与在线文件夹。每条链接独立保留原目录结构，左侧入口为 `pikpak`；观看入口已暂停，旧播放路由返回目录。后文播放相关说明保留为历史实现记录。详见 [当前设计](docs/pikpak-share-playback-design.md)。

个人收藏目录：用类型、作品范围、角色、皮肤、服装属性和创作者六个维度整理套图、视频和其他资源，记录它们在硬盘、PikPak 或百度网盘中的位置，通过电脑或手机网页查找。

当前保存文字信息、标签和外部位置。图片、GIF、封面上传与预览暂缓；视频继续留在原处。应用不会根据填写的路径读取、移动或删除原文件，也不会自动登录网盘、下载链接或代理播放。

六维管理的规则见 [多维标签管理方案](docs/multidimensional-tag-management.md)，接口见 [V2 接口约定](docs/v2-api-contract.md)。已实现名称解析预览；来源采集、确认入库和内容识别仍待开发。

后续导入流程见 [来源解析与导入开发计划](docs/source-import-development-plan.md)：以本地硬盘和 PikPak 为主要入口，选择文件／文件夹后解析名称和目录，预览确认后将资源信息与原始位置录入资源库。当前已交付共用解析核心及文字预览入口，来源连接与正式入库尚未实现。保留现有六维，zw 等仍归内容类型。

最新功能设计见 [PikPak 分享链接管理与观看](docs/pikpak-share-playback-design.md)：本期仅做链接管理和站内播放，所有使用者均可添加、解析、管理和观看，按无需登录设计，不设置发布审核或角色限制。参考 OpenList 分享驱动和 Gopeed 解析实现，播放器选用 ArtPlayer；分享接入与播放器已有代码，已用用户提供的10.44秒H.264样本播放到结尾，拖动、刷新续播和清除进度已通过浏览器回归，长视频仍待验收。启用方式见 [PikPak Web 配置](modules/pikpak-web/README.md)。现有收藏系统的权限保持不变。账号扫描、转存、自动删除及收藏联动尚未实现。

## 已实现的功能

- 独立 [PikPak模块](modules/pikpak/README.md)：核心可单独Maven构建，无宿主业务和框架依赖；相邻Web工程提供HTTP、JDBC和公共API，前端提供分享、目录、观看页面。匿名添加、真实目录读取和普通短视频播放至结尾已验证；网络稳定性及长视频尚未验收。

- 管理员“来源解析”预览：输入文件名／路径和分类文件夹，输出六维候选、依据、歧义与停用提示；解析核心与来源解耦，不写正式数据。实际边界见 [解析模块](docs/parsing-module.md)，PikPak 分享适配层为后续开发。
- 收藏的创建、编辑、标星、搜索、筛选和分页，一个收藏可记录多个存储位置。
- 六维词表独立维护，各维允许留空或多选；内容类型（cos、zw、do 等）与文件形式（视频／图集／其他）分开。
- 管理界面将皮肤放在所属角色下面：展开角色即可搜索、分页浏览和管理子标签，新建时自动填写角色归属；收藏仍可直接搜索皮肤。
- 六维候选可直接搜索名称或辨识上下文，分页浏览，同名角色按原作、同名皮肤按角色、同名创作者按备注辨认。
- 皮肤推导其所属角色与明确原作，详情区分直接选择和推导依据；服装仅使用明确关联的实际属性。
- 不同维度之间同时满足；每个维度内部可切换**全部／任一**，默认全部。支持某维未标注和完全未标注。
- 词表可新增、编辑、停用和删除未引用项；已用角色／皮肤的归属受保护，已有停用关联可保留和检索，新关联校验整条归属链。
- 创作者可手动记录多个平台账号和 HTTPS 主页，只保存身份资料。
- 旧标签保留原路径与关联，通过管理员人工映射、预览和确认追加六维关联，预览过期返回冲突；旧标签及旧收藏写接口默认只读。
- 硬盘、PikPak、百度网盘来源管理；在线、离线、未知状态由用户手动报告，不代表实时探测。来源离线保留收藏和位置。
- 管理员与看客账号、登录、退出、修改自身密码、账号停用与密码重置。
- 修改时检查版本；旧页面提交发生冲突后，需要刷新再操作。删除收藏只删除目录记录和关联。

| 权限 | 管理员 ADMIN | 看客 VIEWER |
| --- | --- | --- |
| 浏览全部收藏、标签、来源、路径和链接 | 允许 | 允许 |
| 修改共享收藏、标签、来源和位置 | 允许 | 禁止 |
| 查看和管理账号、分配角色、重置他人密码 | 允许 | 禁止 |
| 验证旧密码后修改自己的密码、退出登录 | 允许 | 允许 |

所有账号查看同一个收藏库，当前没有逐条内容的可见性设置。未登录用户不能读取收藏数据。后端负责权限与 CSRF 校验，权限不依赖前端按钮是否显示。

在“标签整理”分别维护六个维度，再在“我的收藏”点击“搜索标签，添加筛选条件”或某个维度选择候选。例如角色“公孙离”可以直接检索，不需要先展开目录；选择角色与服装“高跟鞋”可组合筛选。每维的全部／任一规则均在界面显示。手机端先打开“筛选”。标签候选搜索与标题／说明搜索独立，未选择候选时不会自动猜测标签 ID。

皮肤标签目前表示收藏呈现该角色的该造型，所以会推导所属角色与原作；借用其他角色服装的内容应标实际角色、服装及说明，不能把皮肤归属当作实际出镜证据。一份合集的多角色和多服装标签描述整份收藏，不表达谁穿哪种服装或是否同一画面出镜。

### 旧标签升级

Flyway V2 只增加六维词表与关联表，保留旧收藏、位置、标签和原关联。升级前备份数据库，启动时自动应用结构迁移。

管理员在“标签整理 → 旧标签迁移”中为旧节点选择新实体，点击预览后再确认追加。预览包含旧直接标签及其祖先的映射，未映射项继续保留。确认时在目录协调锁和收藏行锁内重新比较预览摘要；收藏、词表或映射发生变化则要求重新预览，无需长期暂停整理。正式库不按名称自动推断原作／角色，不自动清除旧数据。

本工作区后来按用户明确要求，在完整备份后清除了 2 个旧层级标签和 1 条旧收藏关联。收藏、位置、来源、账号与新六维数据保留，受影响收藏版本递增；这次清理仅针对本机个人库，不属于启动或 Flyway 迁移流程。旧标签为空时，管理页隐藏迁移入口。

旧 `/api/assets` 与 `/api/tags` 读取仍保留用于核对，写入默认返回 `409 LEGACY_READ_ONLY`；当前网页全部使用 `/api/v2` 收藏接口。`vault.legacy-writes-enabled=true` 仅用于隔离的旧行为兼容测试，正常部署保持默认 false。

## 技术与目录

后端使用 Java 21、Spring Boot 4.1.1、Spring Security、JdbcTemplate、MySQL 和 Flyway；前端使用 Vue 3、TypeScript 和 Vite。前端构建后打包进后端 JAR，由一个服务提供网页和 API。云部署的 Compose 目标数据库为 MySQL 8.4。

```text
backend/           后端应用、Flyway 迁移及测试
frontend/          Vue 网页
scripts/           Windows 构建、运行、数据库和测试脚本
docs/              选型及阶段设计
compose.yml        应用与 MySQL 的部署配置
Dockerfile         前端、后端多阶段构建
.env.example       Compose 环境变量示例
.local/            本机运行配置、数据库数据和凭据；不提交 Git
.tools/            本机隔离工具链；不提交 Git
```

## 本机启动：当前 Windows 工作区

需要 Maven、Node.js 22 及 JDK 21 或更新版本。脚本优先选择 `.tools/jdk21/` 下的 JDK，否则使用当前进程的 `JAVA_HOME`，不会修改系统 Java 配置。本工作区已准备隔离工具链 `.tools/jdk21/jdk-21.0.12.1+1`。

本次开发使用独立 MySQL 8.4.11 实例 `127.0.0.1:33326`，开发库为 `media_vault_dev`。它的数据目录是 `.local/mysql84-test/data`，程序位于 `.tools/mysql84/mysql-8.4.11-winx64`，与本机已有的 3306 服务分开。

在项目根目录的 PowerShell 中依次运行：

```powershell
.\scripts\local-db.ps1 -Action Start
.\scripts\build.ps1
.\scripts\run-local.ps1
```

打开 [http://127.0.0.1:8080](http://127.0.0.1:8080)。首次构建会安装前端的锁定依赖并下载 Maven 依赖，生成包含网页的 `backend/target/media-vault-0.1.0.jar`。

`run-local.ps1` 默认加载 `.local/dev.env`。本次生成的随机初始管理员账号和密码保存在这个文件中，请在本机自行查看；仓库不提供通用默认密码。修改代码后重新运行构建脚本，再启动新 JAR。运行窗口按 `Ctrl+C` 停止应用。

数据库状态及停止命令：

```powershell
.\scripts\local-db.ps1 -Action Status
.\scripts\local-db.ps1 -Action Stop
```

`local-db.ps1` 只管理本工作区**已初始化**的独立实例，不是 MySQL 安装器。它默认使用工作区预置的 MySQL 路径，也可传入 `-MySqlHome <目录>`；不会操作已有的 3306 服务。停止数据库前先停止使用它的应用或测试。

### 在其他本机环境运行

`.local/` 与 `.tools/` 不随仓库分发。可使用下文 Compose 流程，或自行准备 MySQL 实例、开发库和独立账号，再创建 `.local/dev.env`。例如：

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:33326/media_vault_dev?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USERNAME=your-development-db-user
DB_PASSWORD=replace-with-your-development-db-password
BOOTSTRAP_USERNAME=your-admin-name
BOOTSTRAP_PASSWORD=replace-with-a-unique-admin-password
COOKIE_SECURE=false
```

将地址、库名与凭据替换为你自己的配置，首次启动由 Flyway 建表。初始密码至少 12 个字符，且不超过 72 个 UTF-8 字节。缺少本地 JDK 时，可在当前 PowerShell 中设置 `JAVA_HOME` 指向 JDK 21+，再执行脚本。

也可以指定不同配置文件：

```powershell
.\scripts\run-local.ps1 -EnvFile '.local/my-dev.env'
```

### 前端开发

后端保持运行，在另一个终端中启动 Vite：

```powershell
Set-Location frontend
npm ci
npm run dev
```

访问 Vite 输出的本机地址。开发服务器将 `/api` 转发到 `http://127.0.0.1:8080`；发布时使用构建后的同源网页。

## 账号初始化与管理

用户表为空时，应用从 `BOOTSTRAP_USERNAME`、`BOOTSTRAP_PASSWORD` 创建首个管理员；已有账号时，重启或修改这两个变量不会重置账号、密码或角色。没有公开注册入口，后续账号由管理员创建，默认角色为看客。

登录后可修改自己的密码，管理员可创建、停用账号、调整角色和重置密码。至少保留一个启用的管理员，最后一个管理员不能被停用或降级。停用、角色变化或密码变化会使原有会话在下一次受保护请求时失效，用户需重新登录。当前不提供账号硬删除。

## 测试

在项目根目录运行：

```powershell
.\scripts\test.ps1
```

脚本默认读取 `.local/test.env`。单元测试不需要数据库；真实 MySQL 集成测试需要以下变量，示例值需自行替换：

```dotenv
DB_TEST_URL=jdbc:mysql://127.0.0.1:33326/media_vault_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_TEST_USERNAME=your-isolated-test-db-user
DB_TEST_PASSWORD=replace-with-your-test-db-password
```

**集成测试会删除和重建测试库中的业务数据与测试账号。** 只允许使用专门的 `media_vault_test` 库及测试账号，不得指向开发或生产数据。测试既检查 URL 中的库名，也检查实际连接的数据库名称。

未设置合规 `DB_TEST_URL` 时，数据库集成测试会跳过，单元测试仍运行；已设置 URL 后，还需要完整凭据和可访问的数据库。查看测试输出中的运行与跳过数量，不能把跳过集成测试理解为数据库验证通过。

测试覆盖六维词表、直接／推导关联、ALL／ANY 去重分页、停用归属链、引用保护、旧标签映射与过期预览、位置版本，以及原有层级、权限／CSRF、会话和并发约束。本次已使用 **Java 21 与独立 MySQL 8.4.11 实际运行全部 62 项测试，23 项单元测试与 39 项集成测试全部通过，无跳过**。**Docker 镜像构建及 Compose 部署尚未实际运行验证**。

新版六维网页已在独立测试库完成 **19 项浏览器检查**：登录、六维新建与编辑、ALL／ANY、同名检索、实际服装筛选、创作者管理、手机布局、看客权限，以及人工映射、祖先预览、追加迁移、旧标签保留和幂等。未发现 JavaScript 运行错误。本机开发库升级前已备份，V2 只增加结构；升级后原有 2 份收藏、2 个标签、1 个来源、1 个账号及原标签关联保持不变，未自动映射个人数据。Compose 配置语法已检查，镜像运行仍待在可用 Docker 环境验证。

皮肤管理随后改为角色下的子列表，另完成 **14 项浏览器检查**：角色下新建、同名角色与皮肤隔离、固定归属编辑、收藏筛选及推导、引用保护、启停保留关联、角色内搜索分页、跨维度表单关闭、旧链接跳转、空迁移入口隐藏、手机布局和看客只读。角色搜索从输入时即进入加载状态，避免在搜索防抖期间打开会被父列表刷新关闭的子弹窗。

`build.ps1` 默认执行 Maven 构建阶段的测试，但不会主动加载 `.local/test.env`。完整验证应单独执行 `test.ps1`；已完成测试后可使用 `build.ps1 -SkipTests` 打包。

## Linux / 云服务器部署

服务器准备 Docker Engine 和 Compose 插件，将项目放到服务器后，在项目根目录执行：

```bash
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，填写所有密码项，数据库用户密码、MySQL root 密码和管理员密码分别设置；空密码会阻止 Compose 启动。仅本机 HTTP 调试时使用 `COOKIE_SECURE=false`；通过 HTTPS 访问时设置 `COOKIE_SECURE=true`。

```bash
docker compose up -d --build
docker compose ps
docker compose logs --tail=100 app
```

Compose 会构建前后端镜像、启动 MySQL，并由应用执行 Flyway 迁移。应用端口只绑定服务器 `127.0.0.1:8080`，MySQL 不向宿主机暴露端口，数据保存在 `mysql-data` 命名卷。

远程访问需要将自己的 HTTPS 反向代理指向 `http://127.0.0.1:8080`，配置域名与 TLS 证书，并转发正确的 `Host` 和 `X-Forwarded-Proto`。例如，在已配置 HTTPS 的 Nginx `server` 中加入：

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

通过你的 HTTPS 域名登录。`COOKIE_SECURE=true` 时浏览器只在 HTTPS 请求中发送会话 Cookie。若仅个人设备使用，可在 VPN 网络中提供这个 HTTPS 入口。

停止容器但保留数据卷：

```bash
docker compose down
```

更新代码后重新执行 `docker compose up -d --build`。更改 `.env` 不会自动修改已初始化 MySQL 数据卷中的数据库密码，也不会重置已有管理员；账号密码通过应用管理，数据库密码需按 MySQL 的密码变更流程处理。升级前备份 MySQL 数据并确认可恢复，保留数据卷与凭据文件。

## 当前边界

来源状态是手动报告；路径和链接是元数据，应用不会验证资源是否存在。网盘页面能否打开取决于原平台的账号、链接和权限。云端数据库中的名称、标签、路径与链接也属于个人数据，私有部署不等于端到端加密。

自动扫描、网盘 API、图片与 GIF 存储、缩略图、视频远程预览和移动端原生应用留待后续阶段。历史设计过程见 `docs/technical-selection.md` 与 `docs/mvp-design.md`，当前启动流程以本 README 和实际脚本为准。
