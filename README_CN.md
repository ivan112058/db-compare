# DB Compare

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Backend](https://img.shields.io/badge/Backend-Kotlin%20%7C%20Spring%20Boot-green.svg)
![Frontend](https://img.shields.io/badge/Frontend-Vue%203%20%7C%20PrimeVue-emerald.svg)

[English](README.md) | [中文](README_CN.md)

**DB Compare** 是一个强大的数据库对比与同步工具，旨在解决多环境（开发、测试、生产）数据库结构和数据不一致的问题。它不仅能对比表结构差异，还能深入对比数据内容，并针对树形结构数据（如菜单、组织机构）提供了智能化的同步方案。

## 📖 简介

在软件开发过程中，维护不同环境的数据库一致性是一项繁琐且易出错的工作。DB Compare 提供了一个可视化的界面，帮助开发者快速发现源数据库（Source）与目标数据库（Target）之间的差异，并自动生成可执行的 SQL 脚本（Upgrade/Rollback），确保数据库平滑升级。

## ✨ 核心功能

- **结构对比 (Structure Diff)**
  - 精确识别表、列、索引的增删改差异。
  - 生成幂等的 DDL 语句（如 `ADD COLUMN IF NOT EXISTS` 的安全实现）。

- **数据对比 (Data Diff)**
  - 基于主键对比数据差异（新增、删除、修改）。
  - 支持自定义对比范围。

- **树表智能同步 (Tree Table Support)**
  - 专为层级数据设计（如 `system_menu`）。
  - **业务主键关联**：通过业务字段（如 `name`）而非物理 ID 关联父子节点，解决不同环境 ID 不一致问题。
  - **拓扑排序**：自动计算插入顺序，确保父节点先于子节点插入，避免外键约束错误。
  - **动态 ID 映射**：生成 `INSERT INTO ... SELECT` 语句，自动查找目标环境中的父节点 ID。

- **SQL 生成**
  - **升级脚本 (Upgrade SQL)**：将目标库同步为源库的状态。
  - **回滚脚本 (Rollback SQL)**：将目标库恢复为原状。

- **可视化界面**
  - 直观展示差异详情，支持代码高亮显示生成的 SQL。

## ⚙️ 配置说明

| 配置项 | 说明 | 格式 / 示例 |
|--------|------|-------------|
| **Ignore Fields** | 对比时忽略的字段（如时间戳、创建人等）。 | `create_time` (全局忽略) <br> `user.last_login` (指定表字段) |
| **Exclude Tables** | 完全排除对比的表（不对比结构也不对比数据）。 | `log_table`, `temp_%` |
| **Ignore Data** | 仅对比结构，跳过数据对比的表。 | `operation_log` |
| **Specified Primary Keys** | 指定逻辑主键用于数据对比（覆盖物理主键）。 | `system_config(config_key)` <br> `user_role(user_id, role_id)` |
| **Tree Table Config** | 配置树形表结构，用于生成父子关联的插入语句。 <br> **注意**：必须同时配置 *Specified Primary Keys*。 | `system_menu(id, parent_id)` <br> `org_unit(org_id, parent_org_id)` |
| **Exclude Data Rows** | 排除不需要对比的特定数据行。 | `system_user(username=admin)` <br> `config(type#status=1#0)` |
| **Include Data Rows** | 仅对比符合条件的特定数据行（白名单）。 | `dict_data(dict_type=sys_sex)` |

## 🛠 技术栈

### Backend
- **Language**: Kotlin
- **Framework**: Spring Boot 3
- **Build Tool**: Gradle
- **Database**: MySQL

### Frontend
- **Framework**: Vue 3
- **Build Tool**: Vite
- **UI Library**: PrimeVue 4, Tailwind CSS
- **Language**: TypeScript

## 🚀 快速开始

### 环境要求
- JDK 17+
- Node.js 18+
- MySQL 8.0+

### 安装与运行

#### 1. 启动后端

```bash
cd backend
# 运行 Spring Boot 应用
./gradlew bootRun
```
后端服务默认启动在 `http://localhost:8080`。

#### 2. 启动前端

```bash
cd frontend
# 安装依赖
npm install
# 启动开发服务器
npm run dev
```
前端页面默认访问地址 `http://localhost:5173`。

## 💡 使用示例

### 树表配置示例
假设有一张菜单表 `system_menu`，结构如下：
- `id` (主键)
- `name` (菜单名称，业务唯一)
- `parent_id` (父菜单ID)

在对比配置中：
1. **Tree Table Config**: `system_menu(id, parent_id)`
2. **Specified Primary Keys**: `system_menu(name)`

生成的插入语句将自动处理 ID 映射：
```sql
-- 示例：插入 '用户管理' 菜单，其父菜单为 '系统设置'
INSERT INTO system_menu (name, parent_id, path) 
SELECT '用户管理', id, '/user' 
FROM system_menu 
WHERE name = '系统设置';
```

## 📂 项目结构

```
db-compare/
├── backend/                 # 后端代码 (Kotlin + Spring Boot)
│   ├── src/main/kotlin/     # 源代码
│   └── src/main/resources/  # 配置文件
├── frontend/                # 前端代码 (Vue 3)
│   ├── src/components/      # Vue 组件
│   └── src/api/             # API 接口定义
└── README.md                # 项目说明文档
```

## 📄 License

本项目采用 [Apache License 2.0](LICENSE) 开源。
