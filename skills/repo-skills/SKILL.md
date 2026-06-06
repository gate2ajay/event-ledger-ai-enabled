---
name: repo-skills
description: Guidelines on developing features on isolated feature branches, utilizing conventional commit messages, avoiding direct pushes to master, and preventing security/credential leaks.
---

# Feature Implementation Skill

This skill documents the workflow and standards required for developing features, managing branches, formatting commit messages, and enforcing credential safety.

---

## 🌿 Branching Strategy

### 1. No Direct Pushes to Master
* **Rule**: The `master` (or `main`) branch is protected. Under no circumstances should code be committed or pushed directly to the `master` branch.
* **Process**:
  1. Always create a new feature branch from the latest `master`.
  2. Perform development, local validation, and tests on the feature branch.
  3. Push the feature branch to the remote repository.
  4. Submit a Pull Request (PR) to merge into `master`.

### 2. Feature Branch Naming Conventions
Always use structured and self-descriptive branch names:
* `feature/issue-number-short-description` (e.g., `feature/EL-45-threadpool-bulkhead`)
* `bugfix/issue-number-short-description` (e.g., `bugfix/EL-82-jwt-token-expiration`)
* `chore/short-description` (e.g., `chore/update-gradle-dependencies`)

---

## ✍️ Commit Message Standards

Commit messages must be clear, concise, and follow the **Conventional Commits** specification:

### Format
```
<type>(<scope>): <short summary>

[optional body describing the details of the change]

[optional footer referencing issue numbers, e.g., Closes #45]
```

### Allowed Types
* **`feat`**: A new feature (e.g., `feat(gateway): add threadpool bulkhead for account client`)
* **`fix`**: A bug fix (e.g., `fix(account): resolve dynamic running balance timestamp sorting`)
* **`docs`**: Documentation changes only (e.g., `docs(readme): update port map and setup guides`)
* **`style`**: Formatting, missing semi-colons, white-space changes (no functional code changes)
* **`refactor`**: Code changes that neither fix a bug nor add a feature (e.g., `refactor(common): modularize AOP aspects`)
* **`test`**: Adding missing tests or correcting existing tests
* **`chore`**: Updating build tasks, package manager configs, etc.

---

## 🔐 Credential & Secret Safety

Preventing credential leaks is a critical security boundary.

### 1. Zero Hardcoded Credentials
* **Never** hardcode passwords, private keys, JWT secrets, API tokens, database credentials, or internal M2M secrets in the codebase.
* In Spring Boot projects, leverage properties with environment variable fallbacks:
  ```yaml
  # Good Pattern
  services.account.m2m-secret: ${INTERNAL_M2M_SECRET:default-fallback-only-for-local-dev}
  ```

### 2. File Verification & `.gitignore`
* Ensure local environment configurations, IDE folders (`.idea`, `.vscode`), Gradle build caches, and private keys are registered in the [.gitignore](file:///home/ajayraja/workarea/projects/event-ledger-ai-enabled/.gitignore).
* **Pre-commit Checklist**:
  * Run `git diff --cached` before committing to inspect changes.
  * Verify that no raw credentials or environment files (`.env`, `application-local.yml` with secrets) are staged.
