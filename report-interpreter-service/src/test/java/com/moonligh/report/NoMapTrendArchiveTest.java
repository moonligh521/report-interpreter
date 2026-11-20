package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.report.api.msg.ArchiveOutboxMessageV2;
import com.moonligh.report.api.res.ArchiveOutboxRes;
import com.moonligh.report.api.res.FhirHints;
import com.moonligh.report.api.res.LoincMapping;
import com.moonligh.report.api.res.TrendPoint;
import com.moonligh.report.api.res.TrendRes;

/** AC-T10：V0.3 新增契约无 Map。 */
class NoMapTrendArchiveTest {

    @Test
    void newApiContractsAreRecordsWithoutMap() {
        for (Class<?> c : List.of(
                TrendRes.class, TrendPoint.class,
                ArchiveOutboxMessageV2.class, ArchiveOutboxRes.class,
                FhirHints.class, LoincMapping.class)) {
            assertTrue(c.isRecord(), c.getName());
            for (RecordComponent rc : c.getRecordComponents()) {
                String generic = rc.getGenericType().getTypeName();
                assertFalse(generic.contains("java.util.Map"), c.getSimpleName() + "." + rc.getName());
                assertFalse(Object.class.equals(rc.getType()), c.getSimpleName() + "." + rc.getName());
            }
        }
    }
}
