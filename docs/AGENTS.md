# Valmora AI Agent Guide

## Purpose

Valmora is a modular MMO/RPG engine plugin for Paper. It is YAML-driven and API-extensible.

## Core Principles

- Behavior must not change during refactors
- Architecture must move toward modular, registry-driven systems
- All systems must support hot-reload
- API must remain stable and isolated

## Architecture

- api/ → public, stable
- core/ → engine systems
- module/ → gameplay systems
- infrastructure/ → low-level utilities

## Key Systems

- Registry System (global)
- Execution Engine (conditions/events/variables)
- Context System
- Reload System

## Rules

- Do NOT introduce hardcoded gameplay
- Do NOT duplicate logic across modules
- Do NOT bypass registries
- Do NOT break API contracts

## Refactoring Rules

- Preserve logic
- Improve structure
- Extract reusable components
- Remove duplication

## When Adding Features

- Must integrate with execution engine
- Must be configurable via YAML
- Must be reload-safe

## Output Expectations

- Clean, modular code
- Clear separation of concerns
- No dead code
- No tight coupling
