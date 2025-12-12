[English](https://github.com/KiteMC/VerifyMC/releases/tag/v1.2.4) | 简体中文 | [官方文档](https://kitemc.com/docs/verifymc/)

# VerifyMC v1.2.4 更新日志

## 🐛 问题修复与改进

### 修复的问题

- **服务器重启后白名单失效**：修复了服务器重启后，除 OP 外其他白名单玩家无法加入服务器的问题
- **字符编码错乱**：修复了部分用户访问 Web 界面时出现字符乱码的问题
- **邮件主题配置**：将邮件主题从 i18n 文件移至 config.yml，支持自定义配置

### 改进内容

- 所有 HTTP 响应现在明确使用 UTF-8 编码，并正确声明字符集
- 改进了静态文件的 Content-Type 处理，为文本类文件明确指定字符集
- 增强了白名单同步逻辑，确保服务器重启后的一致性
