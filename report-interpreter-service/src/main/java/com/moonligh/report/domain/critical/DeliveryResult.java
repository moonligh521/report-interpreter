package com.moonligh.report.domain.critical;

/**
 * 通道投递结果。accepted=true 仅表示下游接受；失败时 errorCode 必填。
 */
public record DeliveryResult(boolean accepted, String errorCode) {

    public static DeliveryResult ok() {
        return new DeliveryResult(true, null);
    }

    public static DeliveryResult failed(String errorCode) {
        return new DeliveryResult(false, errorCode);
    }
}
