package com.moonligh.report.service;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.moonligh.report.domain.rule.RuleContext;

/**
 * RuleContext 快照的 JSON 编解码。写入与读回均为 RuleContext record，业务层不经过 Map。
 * Instant 以 ISO-8601 字符串读写（不依赖 jsr310 模块）。
 */
public final class RuleContextJson {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(instantModule());

    private RuleContextJson() {
    }

    public static String write(RuleContext context) {
        try {
            return MAPPER.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("RuleContext 序列化失败", e);
        }
    }

    public static RuleContext read(String json) {
        try {
            return MAPPER.readValue(json, RuleContext.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("RuleContext 反序列化失败", e);
        }
    }

    private static SimpleModule instantModule() {
        SimpleModule module = new SimpleModule("report-instant");
        module.addSerializer(Instant.class, new StdSerializer<>(Instant.class) {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(Instant.class, new StdDeserializer<>(Instant.class) {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return Instant.parse(p.getValueAsString());
            }
        });
        return module;
    }
}
