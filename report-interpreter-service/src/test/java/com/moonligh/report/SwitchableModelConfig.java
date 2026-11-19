package com.moonligh.report;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.moonligh.report.domain.interpret.DraftBlock;
import com.moonligh.report.domain.interpret.InterpretationContext;
import com.moonligh.report.domain.interpret.InterpretationModel;
import com.moonligh.report.infrastructure.model.FakeInterpretationModel;

/**
 * 集成测试用：默认委托 FakeInterpretationModel，用例可临时换成「篡改 / 降级 / 造假」的模型，
 * 验证 Guard 在整条链路上拦截；用例结束必须 {@link SwitchableInterpretationModel#reset()}。
 */
@TestConfiguration
class SwitchableModelConfig {

    @Bean
    @Primary
    SwitchableInterpretationModel switchableInterpretationModel(FakeInterpretationModel fake) {
        return new SwitchableInterpretationModel(fake);
    }

    static final class SwitchableInterpretationModel implements InterpretationModel {

        private final InterpretationModel fallback;
        private volatile BiFunction<InterpretationContext, Integer, List<DraftBlock>> override;

        SwitchableInterpretationModel(InterpretationModel fallback) {
            this.fallback = fallback;
        }

        void use(BiFunction<InterpretationContext, Integer, List<DraftBlock>> model) {
            this.override = model;
        }

        void reset() {
            this.override = null;
        }

        @Override
        public List<DraftBlock> generate(InterpretationContext context, int attempt) {
            BiFunction<InterpretationContext, Integer, List<DraftBlock>> o = override;
            return o == null ? fallback.generate(context, attempt) : o.apply(context, attempt);
        }
    }
}
