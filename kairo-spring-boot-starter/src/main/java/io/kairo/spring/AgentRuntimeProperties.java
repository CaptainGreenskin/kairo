/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.spring;

import io.kairo.spring.config.AgentProperties;
import io.kairo.spring.config.CheckpointProperties;
import io.kairo.spring.config.EmbeddingProperties;
import io.kairo.spring.config.MemoryProperties;
import io.kairo.spring.config.ModelProperties;
import io.kairo.spring.config.SkillProperties;
import io.kairo.spring.config.ToolProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Kairo, bound to the {@code kairo} prefix.
 *
 * <p>This class serves as a thin aggregator that delegates to per-capability property classes. Each
 * nested property class is also independently available as a standalone {@link
 * ConfigurationProperties} bean for direct injection.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>{@code
 * kairo:
 *   model:
 *     provider: anthropic
 *     api-key: ${ANTHROPIC_API_KEY}
 *     model-name: claude-sonnet-4-20250514
 *   agent:
 *     name: my-agent
 *     system-prompt: You are a helpful assistant.
 *     max-iterations: 50
 *   tool:
 *     enable-file-tools: true
 *     enable-exec-tools: true
 *   skills:
 *     enabled: true
 *     search-paths:
 *       - classpath:skills
 *       - ./project-skills
 *       - ~/.kairo/skills
 *     readonly: false
 * }</pre>
 */
@ConfigurationProperties(prefix = "kairo")
public class AgentRuntimeProperties {

    /** Model/provider configuration under {@code kairo.model.*}. */
    @NestedConfigurationProperty private ModelProperties model = new ModelProperties();

    /** Runtime loop behavior under {@code kairo.agent.*}. */
    @NestedConfigurationProperty private AgentProperties agent = new AgentProperties();

    /** Tooling switches and safety patterns under {@code kairo.tool.*}. */
    @NestedConfigurationProperty private ToolProperties tool = new ToolProperties();

    /** Memory backend configuration under {@code kairo.memory.*}. */
    @NestedConfigurationProperty private MemoryProperties memory = new MemoryProperties();

    /** Skill loading/search configuration under {@code kairo.skills.*}. */
    @NestedConfigurationProperty private SkillProperties skills = new SkillProperties();

    /** Embedding provider selection under {@code kairo.embedding.*}. */
    @NestedConfigurationProperty private EmbeddingProperties embedding = new EmbeddingProperties();

    /** Agent checkpoint enablement under {@code kairo.checkpoint.*}. */
    @NestedConfigurationProperty
    private CheckpointProperties checkpoint = new CheckpointProperties();

    public ModelProperties getModel() {
        return model;
    }

    public void setModel(ModelProperties model) {
        this.model = model;
    }

    public AgentProperties getAgent() {
        return agent;
    }

    public void setAgent(AgentProperties agent) {
        this.agent = agent;
    }

    public ToolProperties getTool() {
        return tool;
    }

    public void setTool(ToolProperties tool) {
        this.tool = tool;
    }

    public MemoryProperties getMemory() {
        return memory;
    }

    public void setMemory(MemoryProperties memory) {
        this.memory = memory;
    }

    public SkillProperties getSkills() {
        return skills;
    }

    public void setSkills(SkillProperties skills) {
        this.skills = skills;
    }

    public EmbeddingProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingProperties embedding) {
        this.embedding = embedding;
    }

    public CheckpointProperties getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(CheckpointProperties checkpoint) {
        this.checkpoint = checkpoint;
    }
}
