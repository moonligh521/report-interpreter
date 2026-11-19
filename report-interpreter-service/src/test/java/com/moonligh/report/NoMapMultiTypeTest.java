package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.report.api.req.AckCriticalReq;
import com.moonligh.report.api.req.AskQuestionReq;
import com.moonligh.report.api.req.CorrectionPatch;
import com.moonligh.report.api.req.CorrectionReq;
import com.moonligh.report.api.req.ResolveReviewReq;
import com.moonligh.report.api.res.AckCriticalRes;
import com.moonligh.report.api.res.AskQuestionRes;
import com.moonligh.report.api.res.AssessmentDto;
import com.moonligh.report.api.res.CorrectionRes;
import com.moonligh.report.api.res.FindingDto;
import com.moonligh.report.api.res.IndicatorDto;
import com.moonligh.report.api.res.ReportDetailRes;
import com.moonligh.report.api.res.ResolveReviewRes;
import com.moonligh.report.api.res.SectionDto;
import com.moonligh.report.api.res.SubReportDto;

/** AC-M11：多类型新增契约不得出现 Map。 */
class NoMapMultiTypeTest {

    @Test
    void newApiContractsAreRecordsWithoutMap() {
        for (Class<?> c : List.of(
                AskQuestionReq.class, AskQuestionRes.class,
                AckCriticalReq.class, AckCriticalRes.class,
                CorrectionReq.class, CorrectionPatch.class, CorrectionRes.class,
                ResolveReviewReq.class, ResolveReviewRes.class,
                SubReportDto.class, SectionDto.class, FindingDto.class, AssessmentDto.class,
                IndicatorDto.class, ReportDetailRes.class)) {
            assertTrue(c.isRecord(), c.getName());
            for (RecordComponent rc : c.getRecordComponents()) {
                String generic = rc.getGenericType().getTypeName();
                assertFalse(generic.contains("java.util.Map"), c.getSimpleName() + "." + rc.getName());
                assertFalse(Object.class.equals(rc.getType()), c.getSimpleName() + "." + rc.getName());
            }
        }
    }
}
