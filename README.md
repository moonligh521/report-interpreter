# 报告解读

把体检单、化验单解析成指标，再用检索依据生成白话解读。

本仓库是 [moonligh](https://moonligh.cn/) 的个人开源项目，采用 [Apache-2.0](LICENSE) 许可证。

## 概述

本项目读取体检报告、病历单和化验单。版面解析使用视觉模型，或 OCR 加模型纠错，把复杂表格收成「指标、数值、参考范围」。异常指标先经向量检索召回指南或百科片段，再生成白话解读和健康建议，每条建议都能回到命中的片段。

解读用 SSE 逐段输出。完成后，消息队列把脱敏后的结构化结果写入个人健康档案。仓库只附带合成报告。

## 状态

当前提交的是仓库骨架和社区文件。包名、目录和文档已经按开源仓库放好，业务代码和构建文件尚未写入。下面的功能范围是这个仓库要完成的事，还不是已经可运行的能力。

## 功能范围

- 解析体检报告、病历单和化验单，处理复杂表格
- 输出指标、数值和参考范围
- 按异常指标检索依据后再生成解读
- 用 SSE 流式输出，建议对应到命中的片段
- 用 RocketMQ 把脱敏后的结果异步写入 MySQL 健康档案

## 技术选择

五个仓库使用同一套运行时，模型接入使用 Spring AI。

- JDK 21
- Spring Boot 4.1.1
- Spring AI 2.0.1
- MySQL 8.0.46
- Redis 8.2.10
- RocketMQ 5.5.1，Java 客户端使用 rocketmq-spring 2.3.6

在本仓库中，Spring AI 负责版面理解、检索增强和流式解读。RocketMQ 把脱敏后的结构化结果异步送出。MySQL 保存健康档案。Redis 缓存同一份报告的解析结果。

源码目录按 Maven 标准布局预留。构建文件会和第一批可运行代码一起提交。

## 目录

```text
src/main/java/com/moonligh/report/
src/main/resources/
src/test/java/com/moonligh/report/
```

Java 包名是 `com.moonligh.report`。

## 数据

示例报告由合成指标构成。真实体检单、化验单和病历不进入仓库。

## 路线图

- [ ] 解析体检报告、病历单和化验单，处理复杂表格
- [ ] 输出指标、数值和参考范围
- [ ] 按异常指标检索依据后再生成解读
- [ ] 用 SSE 流式输出，建议对应到命中的片段
- [ ] 用 RocketMQ 把脱敏后的结果异步写入 MySQL 健康档案
- [ ] 补齐构建文件、启动入口和示例
- [ ] 为核心路径补测试

## 声明

本项目提供的是报告内容说明，不能替代检验医师或临床医生的诊断。

## 系列仓库

- `registration-assistant`：导诊挂号助手
- `nutritionist`：饮食方案
- `case-analysis`：案情分析
- `contract-review`：合同审查

## 参与贡献

贡献方式见 [CONTRIBUTING.md](CONTRIBUTING.md)，行为准则见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。安全问题见 [SECURITY.md](SECURITY.md)，请不要把漏洞细节写进公开 Issue。

## 许可证

Copyright 2026 moonligh

本项目使用 Apache License 2.0，全文见 [LICENSE](LICENSE)。
