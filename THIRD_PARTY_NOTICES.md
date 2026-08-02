# Third-Party Notices

本文件记录仓库中直接检入的运行时二进制和模型。项目根目录的 MIT License 仅覆盖项目自有部分，不会重新许可下列第三方材料。

## sherpa-onnx Android AAR

- 文件：`app/libs/sherpa-onnx-1.12.21.aar`
- 版本：1.12.21
- 上游：https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.21
- 原始下载：https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.21/sherpa-onnx-1.12.21.aar
- SHA-256：`AAF81CCFE4D019E493E0BCF4B579F01115AF092AA61800B79A6D09A9FCD10DFF`
- 许可证：Apache License 2.0；完整文本见 `app/src/main/assets/licenses/Apache-2.0.txt`

该 AAR 包含 ONNX Runtime 1.17.1 组件。ONNX Runtime 使用 MIT License，并包含自己的第三方组件声明：

- https://github.com/microsoft/onnxruntime/tree/v1.17.1
- `app/src/main/assets/licenses/ONNX-Runtime-MIT.txt`
- `app/src/main/assets/licenses/ONNX-Runtime-ThirdPartyNotices.txt`

## sherpa-onnx WenetSpeech KWS 模型

- 目录：`app/src/main/assets/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/`
- 模型标识：`sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01`
- 上游发布：https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- 原始包：https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2
- 许可证：Apache License 2.0；完整文本见 `app/src/main/assets/licenses/Apache-2.0.txt`

检入的 ONNX 文件：

| 文件 | SHA-256 |
| --- | --- |
| `encoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx` | `DD784973FC9D2FABB3B800D6DCD20FC3B0CA84F8E2415AFE54B032878E447F4D` |
| `decoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx` | `ED83454004D5BD16D831EAF00ADCD181ED7734886AAB6EF440F3FFA5AA3CFE3B` |
| `joiner-epoch-99-avg-1-chunk-16-left-64.int8.onnx` | `F79760052B87239E325F0567C752AD3130B30D92EFFB847D4307743C20C59A24` |

同目录的 `configuration.json`、`tokens.txt` 和默认 `keywords.txt` 来自同一上游模型包。

## 3D-Speaker 说话人识别模型

- 文件：`app/src/main/assets/speaker_recognition/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx`
- 上游项目：https://github.com/alibaba-damo-academy/3D-Speaker
- sherpa-onnx 发布页：https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
- 原始下载：https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx
- SHA-256：`1A331345F04805BADBB495C775A6DDFFCDD1A732567D5EC8B3D5749E3C7A5E4B`
- 许可证：Apache License 2.0；完整文本见 `app/src/main/assets/licenses/Apache-2.0.txt`

## 其他依赖

Gradle 构建会解析 AndroidX、Kotlin、Compose、Retrofit、OkHttp、Gson、DashScope SDK、Jackson、Room 等依赖；它们分别适用各自许可证。发布二进制前，应结合锁定的依赖解析结果重新生成和复核完整的依赖/许可清单。

本清单用于提供来源和许可材料，不构成法律意见，也不是完整的版权、专利或商标审查。发布者仍需确认其具体分发方式符合所有上游条款。
