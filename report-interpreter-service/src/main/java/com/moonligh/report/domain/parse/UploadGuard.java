package com.moonligh.report.domain.parse;

import java.util.Locale;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 上传文件安全校验：MIME 与魔数必须一致，限制大小，拒绝空文件。
 */
public final class UploadGuard {

    private UploadGuard() {
    }

    /** 校验通过时返回文件种类。 */
    public static Kind check(
            String fileName, String contentType, byte[] content, long maxBytes, boolean allowTextFixture) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        if (content.length > maxBytes) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件超过大小上限");
        }
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        if (isTextFixture(fileName, mime)) {
            return checkText(allowTextFixture, content);
        }
        return switch (mime) {
            case "application/pdf" -> requireMagic(startsWith(content, '%', 'P', 'D', 'F', '-'), Kind.PDF);
            case "image/png" -> requireMagic(
                    startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), Kind.IMAGE);
            case "image/jpeg", "image/jpg" -> requireMagic(startsWith(content, 0xFF, 0xD8, 0xFF), Kind.IMAGE);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的文件类型");
        };
    }

    private static boolean isTextFixture(String fileName, String mime) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return "text/plain".equals(mime)
                || name.endsWith(".lab.txt")
                || name.endsWith(".checkup.txt")
                || name.endsWith(".ecg.txt")
                || name.endsWith(".us.txt");
    }

    private static Kind checkText(boolean allowTextFixture, byte[] content) {
        if (!allowTextFixture) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的文件类型");
        }
        for (byte b : content) {
            if (b == 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "文本文件含非法字节");
            }
        }
        return Kind.TEXT_FIXTURE;
    }

    private static Kind requireMagic(boolean ok, Kind kind) {
        if (!ok) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件内容与声明类型不符");
        }
        return kind;
    }

    private static boolean startsWith(byte[] content, int... magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((content[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** 文件种类。 */
    public enum Kind {
        PDF, IMAGE, TEXT_FIXTURE
    }
}