# 学生宿舍信息管理系统

本项目按照 Java 课程设计指导书项目 5 实现，当前形态为 **Java Web 后台 + MySQL 数据库**。系统覆盖学生住宿信息管理、调换申请审批、楼栋/宿舍/床位基础数据、账号权限、操作审计、统计看板和智能宿舍分析。

## 功能概览

- 企业化 Web 前端：登录页、侧边栏导航、指标看板、分页表格、查询工具栏、维护表单、审批操作区和智能分析区。
- 账号体系：账号数据存储在 MySQL `users` 表，密码使用 PBKDF2 哈希保存，支持创建、修改、删除、启用/禁用账号和重置密码；普通用户必须唯一绑定一个学号，管理员不能绑定学号；系统禁止删除当前账号，并至少保留一个启用的管理员账号。
- 权限控制：管理员拥有完整管理权限；普通用户只能查看本人住宿信息、提交/查看/撤回本人调换申请。
- 学生住宿管理：支持添加、修改、删除、按学号查询、按宿舍查询、楼栋+系+班级+关键词组合查询、按系和班级排序；编辑住宿信息时不需要手工填写宿舍电话，系统会按宿舍基础数据自动带出。
- 服务端分页：学生表、调换申请表和操作日志表由后端按 `page/pageSize` 返回数据，避免前端一次加载过多记录。
- 楼栋/宿舍/床位基础数据：新增 `buildings`、`dorm_rooms`、`beds` 表，楼栋管理和宿舍管理为两个独立菜单，默认宿舍为标准四人间，空余床位按真实宿舍容量和床位状态统计。
- 调换申请流程：学生提交理由，系统锁定目标床位的待审申请；学生可撤回待审申请；管理员审批意见必填；同意后自动更新住宿信息。
- 操作审计：新增、修改、删除住宿信息，审批/撤回调换申请，账号管理、楼栋管理和宿舍管理都会写入 `operation_logs`。
- 床位明细：管理员可单独进入“床位明细”菜单，按楼栋或宿舍查询入住人数、总床位、空余床位和入住率。
- 智能分析：管理员按楼栋或宿舍统计入住率、空余床位、各系比例，并生成宿舍运营评估与建议。

## 运行环境

- JDK 17 及以上
- MySQL 8.0
- Windows PowerShell
- 不需要 Maven，脚本会自动下载 MySQL Connector/J 到 `lib/`

## 数据库配置

在本机创建 `config/database.properties`，内容参考：

```properties
db.host=localhost
db.port=3306
db.name=student_dormitory
db.user=root
db.password=your_mysql_password
```

真实配置文件 `config/database.properties` 已加入 `.gitignore`，不会上传到 GitHub。示例文件是 `config/database.example.properties`。

系统首次启动会自动建库、建表，并补齐演示数据。也可以手动执行：

```powershell
mysql -uroot -p < database\schema.sql
mysql -uroot -p student_dormitory < database\seed_demo_data.sql
```

当前数据库主要表：

- `users`：系统账号、角色、学生绑定、启用状态、密码哈希
- `buildings`：楼栋基础信息
- `dorm_rooms`：宿舍房间、楼层、类型、容量、电话、启停状态
- `beds`：宿舍床位及床位状态
- `students`：学生住宿信息
- `change_requests`：宿舍调换申请
- `operation_logs`：操作审计日志

## 启动系统

```powershell
.\run.ps1
```

浏览器访问：

```text
http://localhost:8080
```

备用命令：

```powershell
.\run.bat
.\run.ps1 -Desktop
.\run.ps1 -Console
.\run.ps1 -SmokeTest
.\run.ps1 -Restart
```

## 初始账号

| 角色 | 用户名 | 初始密码 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `admin123` | 可管理住宿、账号、楼栋、宿舍、审批、分析和日志 |
| 学生 | 学号，如 `20230001` | `student123` | 只能查看本人住宿信息、提交/查看/撤回本人调换申请 |

登录页不会预填或展示演示账号。部署后建议管理员立即修改初始密码。

## 智能分析配置

管理员登录后进入 **系统设置 -> 模型服务配置**，可以添加多个 OpenAI 兼容模型服务配置，并选择其中一个启用；也可以选择 **本地规则分析**，此时系统不会调用外部大模型接口。配置会保存到本机：

```text
config/model.properties
```

该文件已加入 `.gitignore`，不会上传到 GitHub。页面不会回显 API Key 明文。也可通过环境变量配置：

```powershell
$env:LLM_API_URL="https://your-provider.example/v1/chat/completions"
$env:LLM_API_KEY="your-api-key"
$env:LLM_MODEL="your-model-name"
```

未配置模型服务，或在系统设置中选择本地规则分析时，系统会使用本地规则生成分析建议。

## 常见问题

1. 端口被占用：使用 `.\run.ps1 -Restart`，或设置 `APP_PORT` 使用其他端口。
2. PowerShell 禁止运行脚本：改用 `.\run.bat`。
3. MySQL 未启动：在 Windows 服务中启动 `MySQL80`，或确认 `mysql --version` 可用。
4. MySQL 密码错误：修改 `config/database.properties` 中的 `db.password`。
5. 页面登录失败：确认数据库已初始化，或使用管理员账号在“账号管理”中重置学生密码。

## 项目结构

```text
src/main/java/com/dormitory/
  DormitoryManagementSystem.java     # 程序入口，默认启动 Web 服务
  DormitoryWebServer.java            # HTTP 服务与 REST API
  Mysql*.java                        # MySQL 连接、初始化和仓库实现
  UserService.java                   # 账号管理与密码修改
  DormInfrastructureService.java     # 楼栋、宿舍、床位基础数据
  StudentDormService.java            # 学生住宿业务逻辑
  ChangeRequestService.java          # 调换申请业务逻辑
  OperationLogService.java           # 操作审计
  DormAnalysisService.java           # 智能分析调度

src/main/resources/web/
  index.html                         # 企业化后台页面
  styles.css                         # 前端样式
  app.js                             # 前端交互与接口调用

database/
  schema.sql                         # MySQL 建库建表脚本
  seed_demo_data.sql                 # 演示学生与调换申请数据
```
