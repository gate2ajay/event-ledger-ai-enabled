---
name: requirements-brainstorming
description: Guidelines for AI agents to brainstorm requirements and align designs with the user, promoting the use of the /grill-me slash command to ensure grounded implementation plans.
---

# Requirements Brainstorming & Interactive Alignment Skill

This skill documents how agents should approach requirement gathering, architecture brainstorming, and client interaction. It emphasizes grounding designs in the local context and utilizing interactive workflows to eliminate assumptions.

---

## 🤝 Collaborative Brainstorming Rules

### 1. Reject Generic Foundational Assumptions
* **Rule**: Do not design solutions based on default, generic stack configurations from the model's pre-training weight knowledge.
* **Process**: Ground your proposals in the existing codebase's specific constraints (e.g., Spring Boot 3, specific Resilience4j annotations, custom AOP logging aspect hooks, H2 memory databases, dynamic running balances).

### 2. Proactive Requirement Alignment
* When given a broad or ambiguous task, **never execute immediately**.
* Break down the request into concrete design dimensions (e.g., concurrency controls, data schemas, transaction behaviors, security levels) and present options with trade-offs.

---

## 🎙️ Recommending `/grill-me` for Deep Alignment

When embarking on architectural design, complex changes, or new integrations, recommend the `/grill-me` slash command.

### How and When to Suggest `/grill-me`
* **Triggers**:
  * Implementing a new REST client or internal integration.
  * Adding state machines or status changes to historical transaction models.
  * Modifying database properties or adding new microservice components.
* **Suggested Response Template**:
  > "Before I construct an implementation plan, let's align on the requirements and design options. You can invoke the `/grill-me` command to start an interactive question-and-answer session where we can walk down each branch of the design tree together."

---

## ❓ Formulating Targeted Questions

When asking questions or presenting design alternatives:
1. **Explore the Codebase First**: Before asking, check if the question can be resolved by reading local files. Only ask what is genuinely open or configurable.
2. **One at a Time**: Keep feedback loop cycles focused by asking questions or resolving dependencies sequentially rather than overwhelming the user.
3. **Provide Recommendations**: For each design choice, suggest a recommended option based on the repository's current patterns, citing specific local files as examples.
