
<div align="center">

<img src="https://github.com/user-attachments/assets/e8a3df6b-6e67-485a-ae1c-018ac24e87d4" width="120" height="120" style="border-radius: 24px;" alt="HyperIsland Icon"/>

# OPPOPods

**为 HyperOS 设备提供系统级 OPPO 耳机控制**

[![GitHub Release](https://img.shields.io/github/v/release/1812z/OppoPods?style=flat-square&logo=github&color=black)](https://github.com/1812z/OppoPods/releases)
![Downloads](https://img.shields.io/github/downloads/1812z/OppoPods/total?style=flat-square)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/ROM-澎湃OS3%2F4-orange?style=flat-square)](https://hyperos.mi.com)


**[English](README_EN.md)** | **简体中文**

</div>


为小米 HyperOS 设备提供系统级 OPPO 耳机控制的 Xposed 模块。


### 耳机功能

- **降噪控制** — 在关闭 / 降噪 / 自适应 / 通透模式之间切换
- **游戏模式** — 低延迟音频开关，支持连接时自动开启
- **电量显示** — 实时显示左耳、右耳、充电盒电量

### 澎湃集成
- **超级岛** — 支持官方超级岛或模块内建超级岛
- **融合设备中心** — 支持融合设备中心控制
- **设置集成** — 支持系统蓝牙设置控制
- **设备流转** — 支持融合设备中心内多设备一键流转
- **型号伪装** — 伪装受支持的小米耳机

### 模块功能
- **快捷弹窗** — 点击通知或控制中心耳机卡片，弹出浮窗显示电量、降噪、游戏模式控制；点击「更多」进入完整页面
- **快捷跳转** — 通知或控制中心耳机卡片，支持快速跳转欢律/模块设置/系统设置
- **自定义耳机图片** — 支持一键导入欢律资源

### 系统要求

- 小米设备，运行 **HyperOS**（Android 15+）(超级岛仅支持OS3/4)
- **LSPosed** API版本>=101

### 使用

1. 安装 APK
2. 在 LSPosed 中启用模块并勾选推荐作用域
3. 软件右上角一键重启作用域
4. 通过蓝牙连接你的 OPPO 耳机

### OPPO LE Audio（LC3）

入口：**设置 → 独立「OPPO LE Audio」卡片**。顶部为总开关，下方按延迟、连接策略、稳定性、OPPO 私有协议分组。

总开关默认关闭；关闭时，设备选择、全部高级选项、参数编辑及恢复默认按钮均禁用。开启后才能配置，已有保存的总开关状态会保留。

总开关下的「选择介入设备」打开配对设备多选窗口，点击「保存」写入 MAC 白名单；取消不修改。默认白名单为空，不介入任何设备，升级后请先选择耳机。匹配不再依赖 OPPO/OnePlus/Enco 名称，经典地址与双耳 LE 地址只通过系统地址映射和同组关系关联。移除设备时释放其模块 GATT 持有者；恢复高级默认值保留设备选择。

链路不是只有 AT 应答：先保护/修复经典 A2DP、HFP 策略，让主耳能够建立 HFP 和厂商初始化通道，再优先尝试 LE Audio，补齐同组耳机、保留两只耳的 GATT 链路并激活主耳。单独打开系统 LC3 可能让 HyperOS 禁用经典/HFP，AT 根本不会进入；Java 层显示 LE 已连接也不能证明双耳都有稳定音频。

| 开关 | 用途与说明 |
| --- | --- |
| 修复连接策略锁死 | 默认开启；拦内部 FORBIDDEN 回写，在连接前恢复经典策略。本进程识别用户主动关闭的 profile；跨蓝牙进程重启的历史禁用无法可靠区分，可能被修复。 |
| LE Audio 连接优先 / 主动发起 LE 握手 | 默认开启；来向事件只给 LE 一轮机会，失败后放行经典，不全局强制 dual mode。 |
| 蓝牙重启后补拨经典 | 默认开启；服务就绪和系统回连时每启动周期补一次，保留 HFP 初始化机会；不在合盖后的 LE 断开事件里拨经典。 |
| LE 优先时拦截 HFP | **默认关闭，请保持关闭**；Enco X3 主耳依赖 HFP/AT 协调双耳，开启可能导致无握手或单耳出声。 |
| 保持 GATT 链路持有 | 默认开启；实际 LE ACL 建立后挂 direct 持有者，覆盖服务发现，避免闲置拆链。可能增加功耗；主动断开和关闭功能会释放。 |
| 补齐双耳 LE Audio 连接 | 默认开启；同组仍有在线成员时，对缺失成员补一次 direct 连接。服务发现失败但 ACL 仍在线时，首次会话前最多刷新一次 GATT 缓存并重试。 |
| 补全可用音频上下文 | 默认开启；恢复参考模块的空值修复，按该组 native 事件取掩码，不覆盖系统非零值，并在双耳就绪后尝试激活主耳。 |
| 全局低延迟 | **默认关闭**；宣告游戏状态以参与低延迟协商，可能降低码率与音质，实际参数取决于系统配置，播放后可能被系统复位。 |
| 应答厂商 AT / 下发 +VDSP | 默认开启；回答 VDID/VDSF/OESF/VDSP，并在 HFP 连接后下发初始化。默认 Vendor ID=1946、OESF 掩码=0x3f。 |

升级后先重启蓝牙进程，再将双耳回盒并重开盖；开关对下一次连接链路生效。基础日志包含 hook 安装、策略修复、AT 收发、LE 状态、双耳补连及 GATT 持有/释放，标签为 `OppoPods-LEAudio`。

当前移植恢复连接/稳定性链路，尚未移植参考项目的 BLE/SIRK 跨手机归属协调和 HCI 0x13 自动整组让位。用户主动断开会暂停本机整组补连并释放持有者，重新手动连接恢复。

### 致谢

- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — 原始项目
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 风格 Compose UI 组件
- [OPPOPods](https://github.com/Leaf-lsgtky/OppoPods) - by Leaf-lsgtky
- [OPPOLeaConnect](https://github.com/Leaf-lsgtky/OPPOLeaConnect) — LE Audio 连接、策略修复与双耳稳定性实现参考（GPL-3.0）

### 许可证

GPL-3.0

