package com.moonligh.report.service;

/**
 * 已读入内存的上传文件（service 层不依赖 Web 类型）。
 */
public record UploadedFile(String fileName, String contentType, byte[] content) {
}