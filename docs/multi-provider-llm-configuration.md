# Multi-Provider LLM Configuration

This document explains how to configure and use multiple LLM providers (Groq, Gemini, etc.) in LoreVault.

## Overview

The multi-provider LLM configuration allows you to:
- Use different providers for different tasks (e.g., Groq for chat, Gemini for embeddings)
- Configure fallback providers for high availability
- Switch between providers without code changes
- Support any OpenAI-compatible API

## Configuration

### 1. Enable Multi-Provider Mode

Add to your `application.yml` or `application-dev.yml`:

```yaml
lorevault:
  multi-llm:
    enabled: true
```

### 2. Configure Providers

Define your providers with their specific settings:

```yaml
lorevault:
  multi-llm:
    enabled: true
    providers:
      groq:
        name: "Groq"
        base-url: "https://api.groq.com/openai/v1"
        api-key: "${GROQ_API_KEY}"
        chat-model: "llama-3.1-8b-instant"
        temperature: 0.1
        top-p: 0.9
        max-tokens: 6000
        enabled: true
      gemini:
        name: "Gemini"
        base-url: "https://generativelanguage.googleapis.com/v1beta/openai"
        api-key: "${GEMINI_AI_API_KEY}"
        chat-model: "gemini-2.5-flash-lite"
        embedding-model: "gemini-embedding-001"  # Optional - only if provider supports embeddings
        temperature: 0.1
        top-p: 0.9
        max-tokens: 6000
        enabled: true
```

### 3. Configure Task Mapping

Specify which providers to use for different tasks:

```yaml
lorevault:
  multi-llm:
    tasks:
      chat:
        primary: "groq"      # Use Groq for chat tasks (scene detection, etc.)
        fallback: "gemini"   # Fallback to Gemini if Groq fails
      embeddings:
        primary: "gemini"    # Use Gemini for embeddings
        fallback: null       # No fallback for embeddings
```

### 4. Configure Retry Settings

Set retry behavior for failed requests:

```yaml
lorevault:
  multi-llm:
    retry:
      max-attempts: 3
      base-delay-ms: 1000
      backoff-multiplier: 2.0
      max-delay-ms: 10000
      jitter-factor: 0.1
```

## Complete Example Configuration

```yaml
lorevault:
  multi-llm:
    enabled: true
    providers:
      groq:
        name: "Groq"
        base-url: "https://api.groq.com/openai/v1"
        api-key: "${GROQ_API_KEY}"
        chat-model: "llama-3.1-8b-instant"
        temperature: 0.1
        top-p: 0.9
        max-tokens: 6000
        enabled: true
      gemini:
        name: "Gemini"
        base-url: "https://generativelanguage.googleapis.com/v1beta/openai"
        api-key: "${GEMINI_AI_API_KEY}"
        chat-model: "gemini-2.5-flash-lite"
        embedding-model: "gemini-embedding-001"
        temperature: 0.1
        top-p: 0.9
        max-tokens: 6000
        enabled: true
      openai:
        name: "OpenAI"
        base-url: "https://api.openai.com/v1"
        api-key: "${OPENAI_API_KEY}"
        chat-model: "gpt-4o-mini"
        embedding-model: "text-embedding-3-small"
        temperature: 0.1
        top-p: 0.9
        max-tokens: 6000
        enabled: false  # Disabled by default
    tasks:
      chat:
        primary: "groq"
        fallback: "gemini"
      embeddings:
        primary: "gemini"
        fallback: "openai"
    retry:
      max-attempts: 3
      base-delay-ms: 1000
      backoff-multiplier: 2.0
      max-delay-ms: 10000
      jitter-factor: 0.1
    prompts:
      base-path: classpath:prompts
      scene-detection: scene-detection-v2.txt
```

## Environment Variables

Set up your API keys as environment variables:

```bash
export GROQ_API_KEY="your-groq-api-key"
export GEMINI_AI_API_KEY="your-gemini-api-key" 
export OPENAI_API_KEY="your-openai-api-key"
```

## Provider Support

### Currently Supported Providers

All providers must support OpenAI-compatible APIs:

1. **Groq** - Fast inference for chat tasks
   - Base URL: `https://api.groq.com/openai/v1`
   - Models: `llama-3.1-8b-instant`, `llama-3.1-70b-versatile`, etc.
   - Supports: Chat only (no embeddings)

2. **Gemini** - Google's model via OpenAI compatibility
   - Base URL: `https://generativelanguage.googleapis.com/v1beta/openai`
   - Models: `gemini-2.5-flash-lite`, `gemini-embedding-001`
   - Supports: Chat and embeddings

3. **OpenAI** - Original OpenAI models
   - Base URL: `https://api.openai.com/v1`
   - Models: `gpt-4o-mini`, `text-embedding-3-small`, etc.
   - Supports: Chat and embeddings

### Adding New Providers

To add a new OpenAI-compatible provider:

1. Add provider configuration in YAML
2. Update task mapping to use the new provider
3. Test with a small request first

## Migration from Single Provider

### Step 1: Keep Current Configuration

Your existing single-provider configuration will continue to work:

```yaml
lorevault:
  llm:
    scene-detection:
      model: gemini-2.5-flash-lite
      # ... existing config
```

### Step 2: Add Multi-Provider Configuration

Add the new multi-provider configuration alongside the existing one:

```yaml
lorevault:
  # Keep existing single-provider config
  llm:
    scene-detection:
      model: gemini-2.5-flash-lite
      # ... existing config
  
  # Add new multi-provider config (disabled initially)
  multi-llm:
    enabled: false
    # ... multi-provider config
```

### Step 3: Enable Multi-Provider Mode

When ready to switch, set `enabled: true` in the multi-provider configuration.

## Health Checks

The system will monitor all configured providers and report their health status. Check the `/actuator/health` endpoint to see provider status.

## Troubleshooting

### Provider Not Responding

1. Check API key is correct
2. Verify base URL is accessible
3. Check rate limits
4. Review logs for detailed error messages

### Fallback Not Working

1. Ensure fallback provider is enabled
2. Check fallback provider configuration
3. Verify fallback provider supports the requested task

### Configuration Issues

1. Validate YAML syntax
2. Ensure all required properties are set
3. Check environment variables are properly substituted

## Current Status

⚠️ **Work in Progress**: The multi-provider configuration is currently in development. 

**Implemented:**
- ✅ Multi-provider configuration properties
- ✅ Provider validation and mapping
- ✅ Configuration documentation

**TODO:**
- ⏳ ChatClient bean creation with Spring AI
- ⏳ Provider failover logic implementation  
- ⏳ Multi-provider embedding adapter completion
- ⏳ Health checks for multiple providers
- ⏳ Integration testing

**Next Steps:**
1. Complete Spring AI ChatClient integration
2. Implement provider failover in SceneDetectionClient
3. Update health checks to monitor all providers
4. Add integration tests for multi-provider scenarios
