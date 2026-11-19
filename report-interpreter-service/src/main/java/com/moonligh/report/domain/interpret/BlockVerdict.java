package com.moonligh.report.domain.interpret;

/**
 * 单块校验结论。reason 为稳定的机器码（不含患者数据），用于日志与指标。
 */
public record BlockVerdict(DraftBlock block, ValidationStatus status, String reason) {

    public boolean valid() {
        return status == ValidationStatus.VALID;
    }
}
