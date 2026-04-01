---
title: "docs: Update README tech stack, requirements, and installation"
type: docs
status: active
date: 2026-04-01
---

# Update README Documentation

## Overview

Update README.md and README_CN.md to reflect the current project state after the Ktor + Datastar + Oat migration. The documentation currently describes the old Spring Boot + Vue stack and outdated installation instructions.

## Problem Frame

The README files contain outdated information about the tech stack, environment requirements, and installation instructions. After the refactor to Ktor and static HTML frontend, the documentation needs to be updated to match the current implementation.

## Requirements Trace

- R1. Update tech stack section to reflect Ktor instead of Spring Boot
- R2. Update tech stack section to reflect static HTML + Datastar + Oat instead of Vue + PrimeVue + Tailwind
- R3. Update environment requirements (remove Node.js requirement)
- R4. Update installation and run instructions for the new structure
- R5. Update project structure description

## Scope Boundaries

- Only update existing README content, don't add new sections
- Maintain the same language and tone as existing documentation
- Keep both English and Chinese versions synchronized

## Context & Research

### Relevant Code and Patterns

- Current build.gradle.kts uses Ktor 2.3.7, not Spring Boot
- No frontend directory exists - frontend is now static HTML in resources
- Gradle wrapper is in root, not backend directory
- Module is named `dbc`, not `backend`

### Current Issues in README

**Tech Stack Section:**
- Lists Spring Boot 3, should be Ktor
- Lists Vue 3, Vite, PrimeVue 4, Tailwind CSS, TypeScript - should be Datastar + Oat
- MySQL connector is correct

**Environment Requirements:**
- Lists Node.js 18+ which is no longer needed
- JDK 17+ should be JDK 21+ (based on build.gradle.kts JVM_21 target)

**Installation Instructions:**
- References `cd frontend` and `npm install` - frontend no longer exists
- References `./gradlew :dbc:bootRun` which is correct

**Project Structure:**
- Shows frontend directory which doesn't exist
- Should reflect current structure with static HTML in dbc/src/main/resources

## Implementation Units

- [ ] **Unit 1: Update README.md tech stack and requirements**

**Goal:** Update the tech stack section and environment requirements in README.md

**Requirements:** R1, R2, R3

**Dependencies:** None

**Files:**
- Modify: `README.md`

**Approach:**
- Update "🛠 Tech Stack" section:
  - Backend: Ktor instead of Spring Boot
  - Frontend: Datastar + Oat instead of Vue + PrimeVue + Tailwind
- Update "🚀 Quick Start > Requirements":
  - Remove Node.js requirement
  - Update JDK to 21+
- Keep MySQL 8.0+ requirement

**Verification:**
- Tech stack reflects current Ktor implementation
- Node.js is removed from requirements
- JDK version matches build.gradle.kts

---

- [ ] **Unit 2: Update README.md installation and project structure**

**Goal:** Update installation instructions and project structure in README.md

**Requirements:** R4, R5

**Dependencies:** Unit 1

**Files:**
- Modify: `README.md`

**Approach:**
- Update "Installation & Run" section:
  - Remove frontend startup instructions
  - Keep backend startup command
  - Remove frontend access URL
- Update "Project Structure" section:
  - Remove frontend directory
  - Update description to reflect Ktor
  - Keep accurate directory listing

**Verification:**
- Installation instructions match current project setup
- Project structure reflects actual directory layout

---

- [ ] **Unit 3: Update README_CN.md (Chinese version)**

**Goal:** Apply the same updates to the Chinese README

**Requirements:** R1, R2, R3, R4, R5

**Dependencies:** Units 1, 2

**Files:**
- Modify: `README_CN.md`

**Approach:**
- Mirror all changes from README.md to README_CN.md
- Keep Chinese translations accurate
- Maintain consistent formatting

**Verification:**
- Chinese version matches English version content
- All sections are properly translated

## Test Scenarios

- [ ] README.md tech stack shows Ktor, not Spring Boot
- [ ] README.md tech stack shows Datastar + Oat, not Vue + PrimeVue + Tailwind
- [ ] README.md requirements show JDK 21+, no Node.js
- [ ] README.md installation has no frontend instructions
- [ ] README.md project structure shows no frontend directory
- [ ] README_CN.md matches README.md content
- [ ] Both READMEs have consistent formatting

## Verification

- Documentation accurately reflects the current Ktor + static HTML implementation
- No references to old Spring Boot or Vue stack remain
- Installation instructions are clear and accurate