package com.moonligh.report.api.res;

import java.util.List;

/** 追问答复：块与证据均经 Guard/Validator；钉死版本与解读一致。 */
public record AskQuestionRes(
        String answerId,
        List<InterpretationBlockRes> blocks,
        List<EvidenceRes> evidence,
        PinnedVersions pinnedVersions) {
}
