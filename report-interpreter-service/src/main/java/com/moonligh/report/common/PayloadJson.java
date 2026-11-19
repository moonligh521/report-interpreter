package com.moonligh.report.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 强类型载荷与 JSON 列之间的编解码：只接受具体类型（record / sealed 层次），不产出也不读取 Map。
 * 异常信息不带载荷内容，避免把指标全文 / 证件号带进日志。
 */
public final class PayloadJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PayloadJson() {
    }

    public static String write(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("载荷序列化失败：" + payload.getClass().getSimpleName());
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("载荷反序列化失败：" + type.getSimpleName());
        }
    }
}
