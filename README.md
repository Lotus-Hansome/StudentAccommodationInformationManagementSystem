# 学生宿舍信息管理系统

本项目按照《Java 课程设计指导书》项目 5 实现，当前默认形态为 **Java Web 企业后台 + MySQL 数据库**。系统覆盖学生宿舍信息添加、修改、删除、查询、显示、排序、调换申请审批、统计看板和智能宿舍运营分析。

## 功能概览

- 企业化 Web 前端：登录页、侧边导航、顶部用户区、指标卡、表格、查询工具栏、维护表单、审批操作区、智能分析区。
- 权限控制：系统管理员拥有完整管理权限；普通用户仅能查看住宿信息并提交/查看调换申请。
- 学生住宿管理：支持添加、修改、删除、按学号查询、按宿舍查询、按系和班级排序。
- 宿舍调换流程：学生提交调换申请和理由；管理员同意或拒绝；同意后自动更新学生宿舍号、宿舍电话和床位。
- MySQL 持久化：首次启动自动创建数据库、数据表和演示数据。
- 智能分析：管理员按楼栋或宿舍统计入住率、空余床位、各系比例，并生成运营评估与建议。

## 运行环境

- JDK 17 及以上，当前环境已验证 JDK 23。
- MySQL 8.0。
- Windows PowerShell。
- 不需要 Maven。脚本会自动下载 MySQL Connector/J 到 `lib/`。

## 数据库配置

请在本机创建 `config/database.properties`，内容参考：

```properties
db.host=localhost
db.port=3306
db.name=student_dormitory
db.user=root
db.password=your_mysql_password
```

真实配置位于 `config/database.properties`，已加入 `.gitignore`。提交或拷贝项目时可参考 `config/database.example.properties`。

系统首次启动会自动执行建库建表逻辑，也可手动执行：

```powershell
mysql -uroot -p < database\schema.sql
```

如需补充较完整的演示数据，可执行：

```powershell
mysql -uroot -p student_dormitory < database\seed_demo_data.sql
```

该脚本会插入 24 条学生住宿记录，覆盖 2、3、4、5 号楼，并补充待审、已同意、已拒绝三类调换申请样例。脚本使用幂等写法，可重复执行。

## 启动系统

启动 Web 企业后台：

```powershell
.\run.ps1
```

如果 PowerShell 提示禁止运行脚本，可改用：

```powershell
.\run.bat
```

打开浏览器访问：

```text
http://localhost:8080
```

备用入口：

```powershell
.\run.ps1 -Desktop
.\run.ps1 -Console
.\run.ps1 -SmokeTest
.\run.ps1 -Restart
```

## 无法运行时检查

1. 端口被占用：新版 `run.ps1` 会自动识别。如果本系统已运行，会直接提示 `Application is already running: http://localhost:8080`。
2. PowerShell 策略限制：使用 `.\run.bat` 启动。
3. MySQL 未启动：在 Windows 服务中启动 `MySQL80`，或确认 `mysql --version` 可用。
4. MySQL 密码错误：修改 `config/database.properties` 中的 `db.password`。
5. 浏览器没有自动打开：手动访问 `http://localhost:8080`。

## 默认账号

| 角色 | 用户名 | 密码 | 绑定学号 | 权限 |
| --- | --- | --- | --- | --- |
| 系统管理员 | `admin` | `admin123` | 无 | 学生住宿管理、调换审批、统计分析 |
| 普通用户 | `student` | `student123` | `20230001` | 住宿查询、排序查看、提交/查看本人调换申请 |

## 智能分析配置

管理员登录后可进入 **系统设置 -> 模型服务配置**，填写 OpenAI 兼容接口地址、模型名称和 API Key。配置会保存到本机：

```text
config/model.properties
```

该文件已加入 `.gitignore`，不会上传到 GitHub。页面不会回显 API Key 明文，后续修改时密钥留空会保留原密钥。可参考：

```text
config/model.example.properties
```

也可以继续使用部署环境变量：

```powershell
$env:LLM_API_URL="https://your-provider.example/v1/chat/completions"
$env:LLM_API_KEY="your-api-key"
$env:LLM_MODEL="your-model-name"
```

未配置接口时，系统会使用本地规则生成分析建议，便于离线答辩演示。

## 项目结构

```text
src/main/java/com/dormitory/
  DormitoryManagementSystem.java     # 程序入口，默认启动 Web 服务
  DormitoryWebServer.java            # HTTP 服务与 REST API
  DormitoryManagementGui.java        # 备用 Swing 桌面界面
  Mysql*.java                        # MySQL 连接、初始化、仓储
  StudentDormService.java            # 学生住宿业务逻辑
  ChangeRequestService.java          # 调换申请业务逻辑
  DormAnalysisService.java           # 智能分析调度

src/main/resources/web/
  index.html                         # 企业后台页面
  styles.css                         # 前端样式
  app.js                             # 前端交互与接口调用

database/schema.sql                  # MySQL 建库建表脚本
docs/                                # 课程设计报告材料
```
