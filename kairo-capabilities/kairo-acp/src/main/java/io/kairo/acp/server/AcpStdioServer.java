/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.acp.wire.JsonRpcLineCodec;
import io.kairo.acp.wire.JsonRpcLineMessage;
import io.kairo.acp.wire.JsonRpcLineMessage.Errors;
import io.kairo.api.acp.AcpAgent;
import io.kairo.api.acp.AcpCapabilities;
import io.kairo.api.acp.AcpContentBlock;
import io.kairo.api.acp.AcpImplementation;
import io.kairo.api.acp.AcpInitializeRequest;
import io.kairo.api.acp.AcpInitializeResponse;
import io.kairo.api.acp.AcpNewSessionRequest;
import io.kairo.api.acp.AcpPromptRequest;
import io.kairo.api.acp.AcpPromptResponse;
import io.kairo.api.acp.AcpSessionUpdate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hosts an {@link AcpAgent} over stdio JSON-RPC. Designed to be the {@code main()} entry point of a
 * Kairo-based agent that an editor launches as a subprocess.
 *
 * <p>Wire shape per ACP convention:
 *
 * <ul>
 *   <li>{@code stdin} — one JSON-RPC frame per line from the editor (requests + notifications)
 *   <li>{@code stdout} — one JSON-RPC frame per line back to the editor (responses + {@code
 *       session/update} notifications). Logging MUST go to {@code stderr} to avoid corrupting this
 *       stream.
 * </ul>
 *
 * <p>MVP method coverage:
 *
 * <ul>
 *   <li>{@code initialize} → {@link AcpAgent#initialize}
 *   <li>{@code session/new} → {@link AcpAgent#newSession}
 *   <li>{@code session/prompt} → {@link AcpAgent#prompt} with streaming {@code session/update}
 *   <li>{@code authenticate} → returns success no-op (kairo agents auth via env vars, not the
 *       editor)
 *   <li>everything else → {@code METHOD_NOT_FOUND}
 * </ul>
 */
public final class AcpStdioServer {

    private static final Logger log = LoggerFactory.getLogger(AcpStdioServer.class);

    private final AcpAgent agent;
    private final InputStream in;
    private final OutputStream out;
    private final JsonRpcLineCodec codec;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AcpStdioServer(AcpAgent agent) {
        this(agent, System.in, System.out, new JsonRpcLineCodec());
    }

    public AcpStdioServer(
            AcpAgent agent, InputStream in, OutputStream out, JsonRpcLineCodec codec) {
        this.agent = agent;
        this.in = in;
        this.out = out;
        this.codec = codec;
    }

    /**
     * Run the dispatch loop. Blocks until {@code in} closes (EOF) or {@link #stop()} fires.
     * Exceptions inside one request never tear down the loop — they get serialized into a JSON-RPC
     * error response and the next message is read.
     */
    public void serve() throws IOException {
        running.set(true);
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            while (running.get()) {
                JsonRpcLineMessage msg;
                try {
                    msg = codec.readMessage(reader);
                } catch (JsonProcessingException e) {
                    // Bad JSON on this line — emit parse error and keep serving the next line.
                    log.warn("ACP parse error: {}", e.getMessage());
                    writeFrame(codec.errorResponse(null, Errors.PARSE_ERROR, e.getMessage()));
                    continue;
                } catch (IOException e) {
                    // Real I/O failure (stdin closed, etc.) — exit the loop.
                    if (running.get()) log.warn("ACP read error: {}", e.getMessage());
                    return;
                } catch (RuntimeException e) {
                    // Classifier saw a JSON that didn't fit Request/Response/Notification shape.
                    log.warn("ACP frame error: {}", e.getMessage());
                    writeFrame(codec.errorResponse(null, Errors.INVALID_REQUEST, e.getMessage()));
                    continue;
                }
                if (msg == null) {
                    log.debug("ACP reader saw clean EOF");
                    return;
                }
                dispatch(msg);
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    // ---- dispatch -----------------------------------------------------------------------------

    private void dispatch(JsonRpcLineMessage msg) {
        if (msg instanceof JsonRpcLineMessage.Request req) {
            handleRequest(req);
        } else if (msg instanceof JsonRpcLineMessage.Notification n) {
            handleNotification(n);
        }
        // We don't expect Responses on stdin (we don't make outbound requests in MVP).
    }

    private void handleRequest(JsonRpcLineMessage.Request req) {
        try {
            switch (req.method()) {
                case "initialize" -> handleInitialize(req);
                case "authenticate" -> handleAuthenticate(req);
                case "session/new" -> handleNewSession(req);
                case "session/prompt" -> handlePrompt(req);
                case "session/load" -> handleLoadSession(req);
                case "session/cancel" -> handleCancelSession(req);
                default ->
                        writeFrame(
                                codec.errorResponse(
                                        req.id(),
                                        Errors.METHOD_NOT_FOUND,
                                        "method '" + req.method() + "' not implemented yet"));
            }
        } catch (Exception e) {
            log.warn("ACP {} handler failed: {}", req.method(), e.getMessage(), e);
            writeFrame(codec.errorResponse(req.id(), Errors.INTERNAL_ERROR, e.getMessage()));
        }
    }

    private void handleLoadSession(JsonRpcLineMessage.Request req) {
        String sessionId = req.params() == null ? "" : req.params().path("sessionId").asText("");
        var resp = agent.loadSession(sessionId).block();
        ObjectNode result = codec.mapper().createObjectNode();
        result.put("sessionId", resp == null ? sessionId : resp.sessionId());
        writeFrame(codec.response(req.id(), result));
    }

    private void handleCancelSession(JsonRpcLineMessage.Request req) {
        String sessionId = req.params() == null ? "" : req.params().path("sessionId").asText("");
        agent.cancel(sessionId).block();
        writeFrame(codec.response(req.id(), codec.mapper().nullNode()));
    }

    private void handleNotification(JsonRpcLineMessage.Notification n) {
        log.debug("ACP notification ignored: {}", n.method());
    }

    // ---- method handlers ----------------------------------------------------------------------

    private void handleInitialize(JsonRpcLineMessage.Request req) {
        AcpInitializeRequest reqParams = parseInitialize(req.params());
        AcpInitializeResponse resp = agent.initialize(reqParams).block();
        ObjectMapper m = codec.mapper();
        ObjectNode result = m.createObjectNode();
        result.put(
                "protocolVersion",
                resp == null
                        ? AcpInitializeResponse.CURRENT_PROTOCOL_VERSION
                        : resp.protocolVersion());
        AcpImplementation info = resp == null ? null : resp.agentInfo();
        ObjectNode infoNode = m.createObjectNode();
        infoNode.put("name", info == null ? "kairo" : info.name());
        infoNode.put("version", info == null ? "0.0.0" : info.version());
        result.set("agentInfo", infoNode);
        result.set(
                "agentCapabilities",
                capabilitiesToJson(
                        resp == null ? AcpCapabilities.textOnly() : resp.agentCapabilities()));
        // Zed stalls waiting for `authMethods` even when no auth is needed — must include the
        // field (empty array means "no auth required, proceed to session/new").
        result.set("authMethods", m.createArrayNode());
        writeFrame(codec.response(req.id(), result));
    }

    private void handleAuthenticate(JsonRpcLineMessage.Request req) {
        // MVP: agents auth via their own env vars; we acknowledge whatever method the editor
        // asked for. Editors that don't need auth typically skip this call entirely.
        writeFrame(codec.response(req.id(), codec.mapper().nullNode()));
    }

    private void handleNewSession(JsonRpcLineMessage.Request req) {
        AcpNewSessionRequest reqParams = parseNewSession(req.params());
        var resp = agent.newSession(reqParams).block();
        ObjectMapper m = codec.mapper();
        ObjectNode result = m.createObjectNode();
        result.put("sessionId", resp == null ? "default" : resp.sessionId());
        // Zed waits for `modes` after session/new before enabling the prompt button. Even when
        // the agent doesn't really have multiple modes, we must advertise at least one.
        ObjectNode modes = m.createObjectNode();
        modes.put("currentModeId", "default");
        ArrayNode available = modes.putArray("availableModes");
        ObjectNode defaultMode = available.addObject();
        defaultMode.put("id", "default");
        defaultMode.put("name", "Default");
        defaultMode.put("description", "Default agent mode");
        result.set("modes", modes);
        writeFrame(codec.response(req.id(), result));
    }

    private void handlePrompt(JsonRpcLineMessage.Request req) {
        AcpPromptRequest reqParams = parsePrompt(req.params());
        AcpPromptResponse resp =
                agent.prompt(reqParams, update -> writeFrame(sessionUpdateNotification(update)))
                        .block();
        ObjectNode result = codec.mapper().createObjectNode();
        result.put(
                "stopReason",
                stopReasonWire(
                        resp == null ? AcpPromptResponse.StopReason.END_TURN : resp.stopReason()));
        writeFrame(codec.response(req.id(), result));
    }

    // ---- JSON shape parsers --------------------------------------------------------------------

    private AcpInitializeRequest parseInitialize(JsonNode params) {
        if (params == null || params.isMissingNode()) {
            return new AcpInitializeRequest(
                    AcpInitializeResponse.CURRENT_PROTOCOL_VERSION,
                    new AcpImplementation("unknown", "0.0.0"));
        }
        int version =
                params.path("protocolVersion")
                        .asInt(AcpInitializeResponse.CURRENT_PROTOCOL_VERSION);
        JsonNode info = params.path("clientInfo");
        return new AcpInitializeRequest(
                version,
                new AcpImplementation(
                        info.path("name").asText("unknown"), info.path("version").asText("0.0.0")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AcpNewSessionRequest parseNewSession(JsonNode params) {
        String cwd = params == null ? null : params.path("cwd").asText(null);
        List<Map<String, Object>> mcp = new ArrayList<>();
        if (params != null && params.has("mcpServers") && params.get("mcpServers").isArray()) {
            for (JsonNode mcpNode : params.get("mcpServers")) {
                try {
                    Map<String, Object> asMap = codec.mapper().convertValue(mcpNode, Map.class);
                    mcp.add(asMap);
                } catch (Exception ignore) {
                    // Skip malformed MCP descriptors — MVP doesn't act on them anyway.
                }
            }
        }
        return new AcpNewSessionRequest(cwd, mcp);
    }

    private AcpPromptRequest parsePrompt(JsonNode params) {
        String sessionId = params == null ? "" : params.path("sessionId").asText("");
        List<AcpContentBlock> blocks = new ArrayList<>();
        if (params != null && params.has("prompt") && params.get("prompt").isArray()) {
            for (JsonNode bn : params.get("prompt")) {
                String type = bn.path("type").asText();
                switch (type) {
                    case "text" -> blocks.add(new AcpContentBlock.Text(bn.path("text").asText("")));
                    case "image" ->
                            blocks.add(
                                    new AcpContentBlock.Image(
                                            bn.path("mimeType").asText("image/png"),
                                            bn.path("data").asText("")));
                    case "audio" ->
                            blocks.add(
                                    new AcpContentBlock.Audio(
                                            bn.path("mimeType").asText("audio/wav"),
                                            bn.path("data").asText("")));
                    case "resource_link" ->
                            blocks.add(
                                    new AcpContentBlock.ResourceLink(
                                            bn.path("uri").asText(""),
                                            bn.path("mimeType").asText("")));
                    case "resource" ->
                            blocks.add(
                                    new AcpContentBlock.EmbeddedResource(
                                            bn.path("uri").asText(""),
                                            bn.path("mimeType").asText("text/plain"),
                                            bn.path("text").asText("")));
                    default ->
                            log.debug(
                                    "ACP prompt: ignoring unsupported content block type '{}'",
                                    type);
                }
            }
        }
        return new AcpPromptRequest(sessionId, blocks);
    }

    // ---- JSON shape builders --------------------------------------------------------------------

    private ObjectNode capabilitiesToJson(AcpCapabilities caps) {
        ObjectMapper m = codec.mapper();
        ObjectNode n = m.createObjectNode();
        n.put("loadSession", caps.loadSession());
        ObjectNode prompt = m.createObjectNode();
        prompt.put("image", caps.promptImage());
        prompt.put("audio", caps.promptAudio());
        n.set("promptCapabilities", prompt);
        ObjectNode session = m.createObjectNode();
        if (caps.sessionFork()) session.putObject("fork");
        if (caps.sessionResume()) session.putObject("resume");
        if (caps.sessionList()) session.putObject("list");
        n.set("sessionCapabilities", session);
        return n;
    }

    private ObjectNode sessionUpdateNotification(AcpSessionUpdate update) {
        ObjectMapper m = codec.mapper();
        ObjectNode params = m.createObjectNode();
        params.put("sessionId", update.sessionId());

        // ACP session/update is internally tagged with `sessionUpdate` as the discriminator.
        // Per https://agentclientprotocol.com/protocol/prompt-turn :
        //   - agent_message_chunk / agent_thought_chunk: `content` is a SINGLE object
        //     ({"type":"text","text":"..."}), NOT an array.
        //   - tool lifecycle uses `tool_call` (initial) + `tool_call_update` (subsequent
        //     status / content deltas). There is no `tool_call_start` / `tool_call_progress`
        //     / `tool_call_complete` in the spec — those names crashed Zed's Rust
        //     deserializer with "expected variant identifier".
        //   - tool_call_update.content is an ARRAY of wrapper objects
        //     ({"type":"content","content":{"type":"text",...}}).
        ObjectNode update_ = m.createObjectNode();
        if (update instanceof AcpSessionUpdate.AgentMessageChunk amc) {
            update_.put("sessionUpdate", "agent_message_chunk");
            ObjectNode content = update_.putObject("content");
            content.put("type", "text");
            content.put("text", amc.text());
        } else if (update instanceof AcpSessionUpdate.AgentThoughtChunk atc) {
            update_.put("sessionUpdate", "agent_thought_chunk");
            ObjectNode content = update_.putObject("content");
            content.put("type", "text");
            content.put("text", atc.text());
        } else if (update instanceof AcpSessionUpdate.ToolCallStart start) {
            update_.put("sessionUpdate", "tool_call");
            update_.put("toolCallId", start.toolCallId());
            update_.put("title", start.title());
            update_.put("kind", "other");
            update_.put("status", "in_progress");
            update_.set(
                    "rawInput", m.valueToTree(start.input() == null ? Map.of() : start.input()));
        } else if (update instanceof AcpSessionUpdate.ToolCallProgress prog) {
            update_.put("sessionUpdate", "tool_call_update");
            update_.put("toolCallId", prog.toolCallId());
            ArrayNode content = update_.putArray("content");
            ObjectNode wrap = content.addObject();
            wrap.put("type", "content");
            ObjectNode inner = wrap.putObject("content");
            inner.put("type", "text");
            inner.put("text", prog.chunk() == null ? "" : prog.chunk());
        } else if (update instanceof AcpSessionUpdate.ToolCallComplete done) {
            update_.put("sessionUpdate", "tool_call_update");
            update_.put("toolCallId", done.toolCallId());
            update_.put("status", done.success() ? "completed" : "failed");
            if (done.output() != null && !done.output().isEmpty()) {
                ArrayNode content = update_.putArray("content");
                ObjectNode wrap = content.addObject();
                wrap.put("type", "content");
                ObjectNode inner = wrap.putObject("content");
                inner.put("type", "text");
                inner.put("text", done.output());
            }
        }
        params.set("update", update_);
        return codec.notification("session/update", params);
    }

    private static String stopReasonWire(AcpPromptResponse.StopReason reason) {
        return switch (reason) {
            case END_TURN -> "end_turn";
            case MAX_TOKENS -> "max_tokens";
            case MAX_TURN_REQUESTS -> "max_turn_requests";
            case REFUSAL -> "refusal";
            case CANCELLED -> "cancelled";
        };
    }

    // ---- output helper -------------------------------------------------------------------------

    private void writeFrame(ObjectNode node) {
        try {
            codec.writeMessage(out, node);
        } catch (IOException e) {
            log.warn("ACP write failed: {}", e.getMessage());
            running.set(false);
        }
    }
}
