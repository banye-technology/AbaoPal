# Security Policy

## Supported versions

安全修复仅面向默认分支的最新代码。仓库目前不承诺为旧版本或第三方修改版提供补丁。

## Reporting a vulnerability

请使用 GitHub 仓库的 Private Vulnerability Reporting / Security Advisory 私下报告安全问题。报告中可包含受影响版本、复现步骤、影响范围和建议修复，但不要在公开 Issue 中发布 API Key、个人数据、可利用细节或真实受害者信息。

如果你发现密钥已经进入公开仓库或构建产物，请先在对应服务商处吊销并轮换；仅从 Git 历史删除字符串不能使密钥失效。

## Security boundaries

本项目使用 Android 无障碍、截图、麦克风、悬浮窗和跨应用 Intent 等高权限能力。运行模型生成的动作前，应把模型输出视为不可信输入。不要在包含支付、密码、验证码、身份信息或不可逆操作的环境中无人值守运行。

仓库不会提供生产签名文件、真实服务密钥或预编译 APK。构建者应保护 `local.properties`、签名密钥、CI Secret 和发布账号，并在每次发布前检查依赖、日志、网络端点和最终 APK 内容。
