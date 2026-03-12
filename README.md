# DB Compare

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Backend](https://img.shields.io/badge/Backend-Kotlin%20%7C%20Spring%20Boot-green.svg)
![Frontend](https://img.shields.io/badge/Frontend-Vue%203%20%7C%20PrimeVue-emerald.svg)

[English](README.md) | [中文](README_CN.md)

**DB Compare** is a powerful database comparison and synchronization tool designed to solve the problem of inconsistent database structures and data across multiple environments (Development, Test, Production). It not only compares table structure differences but also deeply compares data content, providing intelligent synchronization solutions for hierarchical data (e.g., menus, organizations).

## 📖 Introduction

In the software development process, maintaining database consistency across different environments is a tedious and error-prone task. DB Compare provides a visual interface to help developers quickly identify differences between a Source database and a Target database, automatically generating executable SQL scripts (Upgrade/Rollback) to ensure smooth database upgrades.

## ✨ Core Features

- **Structure Diff**
  - Accurately identifies additions, deletions, and modifications of tables, columns, and indexes.
  - Generates idempotent DDL statements (e.g., safe implementation of `ADD COLUMN IF NOT EXISTS`).

- **Data Diff**
  - Compares data differences (insert, delete, update) based on primary keys.
  - Supports custom comparison ranges.

- **Tree Table Support**
  - Designed specifically for hierarchical data (e.g., `system_menu`).
  - **Business Key Association**: Associates parent and child nodes using business fields (e.g., `name`) instead of physical IDs, resolving ID inconsistency issues across environments.
  - **Topological Sort**: Automatically calculates insertion order to ensure parent nodes are inserted before child nodes, avoiding foreign key constraint errors.
  - **Dynamic ID Mapping**: Generates `INSERT INTO ... SELECT` statements to automatically look up parent node IDs in the target environment.

- **SQL Generation**
  - **Upgrade SQL**: Synchronizes the target database to match the source database state.
  - **Rollback SQL**: Restores the target database to its original state.

- **Visual Interface**
  - Intuitively displays detailed differences and supports code highlighting for generated SQL.

## ⚙️ Configuration Guide

| Option | Description | Format / Example |
|--------|-------------|------------------|
| **Ignore Fields** | Columns to ignore during comparison (e.g., timestamps). | `create_time` (Global) <br> `user.last_login` (Specific table) |
| **Exclude Tables** | Tables to completely skip (structure & data). | `log_table`, `temp_%` |
| **Ignore Data** | Tables to skip data comparison (structure only). | `operation_log` |
| **Specified Primary Keys** | Define logical primary keys for comparison (overrides physical PK). | `system_config(config_key)` <br> `user_role(user_id, role_id)` |
| **Tree Table Config** | Define hierarchical structure for smart synchronization. <br> **Note**: Must be used with *Specified Primary Keys*. | `system_menu(id, parent_id)` <br> `org_unit(org_id, parent_org_id)` |
| **Exclude Data Rows** | Specific rows to exclude from data comparison. | `system_user(username=admin)` <br> `config(type#status=1#0)` |
| **Include Data Rows** | Only compare specific rows (whitelist). | `dict_data(dict_type=sys_sex)` |

## 🛠 Tech Stack

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

## 🚀 Quick Start

### Requirements
- JDK 17+
- Node.js 18+
- MySQL 8.0+

### Installation & Run

#### 1. Start Backend

```bash
cd backend
# Run Spring Boot application
./gradlew bootRun
```
The backend service starts by default at `http://localhost:8080`.

#### 2. Start Frontend

```bash
cd frontend
# Install dependencies
npm install
# Start development server
npm run dev
```
The frontend page is accessible by default at `http://localhost:5173`.

## 💡 Usage Example

### Tree Table Configuration Example
Assuming a menu table `system_menu` with the following structure:
- `id` (Primary Key)
- `name` (Menu Name, Business Unique Key)
- `parent_id` (Parent Menu ID)
- `path` (Path, Business Field)

In the comparison configuration:
1. **Tree Table Config**: `system_menu(id, parent_id)`
2. **Specified Primary Keys**: `system_menu(name)`

When querying data, parent_id will be automatically mapped to avoid comparing by ID:
```sql
SELECT m.name, m.path, p.name AS __parent_name
FROM system_menu m
LEFT JOIN system_menu p on m.parent_id = p.id;
```

The generated insertion statement will automatically handle ID mapping:
```sql
-- Example: Insert 'User Management' menu, where parent is 'System Settings'
INSERT INTO system_menu (name, parent_id, path) 
SELECT 'User Management', id, '/user' 
FROM system_menu 
WHERE name = 'System Settings';
```

## 📂 Project Structure

```
db-compare/
├── backend/                 # Backend code (Kotlin + Spring Boot)
│   ├── src/main/kotlin/     # Source code
│   └── src/main/resources/  # Configuration files
├── frontend/                # Frontend code (Vue 3)
│   ├── src/components/      # Vue components
│   └── src/api/             # API definitions
└── README.md                # Project documentation
```

## � License

This project is open-sourced under the [Apache License 2.0](LICENSE).
