package com.moonligh.report.domain.archive;

/**
 * 档案投递结果。accepted=true 表示消费方已落地（或已存在，幂等）。
 */
public record ArchiveDeliveryResult(boolean accepted, String errorCode) {

    public static ArchiveDeliveryResult ok() {
        return new ArchiveDeliveryResult(true, null);
    }

    public static ArchiveDeliveryResult failed(String errorCode) {
        return new ArchiveDeliveryResult(false, errorCode);
    }
}
