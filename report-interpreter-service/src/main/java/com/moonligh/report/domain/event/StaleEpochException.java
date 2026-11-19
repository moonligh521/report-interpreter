package com.moonligh.report.domain.event;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 事件写入携带的 epoch 与任务当前 epoch 不一致（旧一代的在途写入）：直接丢弃，不落库。
 */
public class StaleEpochException extends BusinessException {

    private final long eventEpoch;
    private final long currentEpoch;

    public StaleEpochException(long eventEpoch, long currentEpoch) {
        super(ErrorCode.PARAM_INVALID, "事件 epoch 已过期");
        this.eventEpoch = eventEpoch;
        this.currentEpoch = currentEpoch;
    }

    public long getEventEpoch() {
        return eventEpoch;
    }

    public long getCurrentEpoch() {
        return currentEpoch;
    }
}
