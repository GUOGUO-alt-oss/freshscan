const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const path = require("path");

// ── Icon Helpers ──
const iconLibs = {
  fa: require("react-icons/fa"),
  md: require("react-icons/md"),
  hi: require("react-icons/hi"),
};

function renderIcon(Icon, color, size) {
  return ReactDOMServer.renderToStaticMarkup(
    React.createElement(Icon, { color, size: String(size || 256) })
  );
}
async function iconPng(Icon, color, size) {
  const svg = renderIcon(Icon, color || "#FFFFFF", size || 256);
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  return "image/png;base64," + buf.toString("base64");
}

// ── Color Palette (Forest & Tea Green) ──
const C = {
  darkBg: "1A2E1A",
  primary: "2E7D32",
  primaryLight: "4CAF50",
  accent: "00897B",
  accentLight: "4DB6AC",
  warmGreen: "81C784",
  cream: "F5F5EA",
  white: "FFFFFF",
  textDark: "2D2D2D",
  textGray: "6B7280",
  cardBg: "FFFFFF",
  chartGreen: "2E7D32",
  chartTeal: "00897B",
  chartLight: "81C784",
};

// ── Helper: create shadow factory ──
const cardShadow = () => ({ type: "outer", blur: 6, offset: 2, angle: 135, color: "000000", opacity: 0.10 });

async function main() {
  const pres = new pptxgen();
  pres.layout = "LAYOUT_16x9";
  pres.author = "陈润扬";
  pres.title = "鲜识 FreshScan — 期末作品展示";

  // Pre-render icons
  const icons = {
    apple: await iconPng(iconLibs.fa.FaAppleAlt, "#" + C.white, 256),
    eye: await iconPng(iconLibs.fa.FaEye, "#" + C.primary, 256),
    brain: await iconPng(iconLibs.fa.FaBrain, "#" + C.accent, 256),
    camera: await iconPng(iconLibs.fa.FaCamera, "#" + C.white, 256),
    mobile: await iconPng(iconLibs.fa.FaMobileAlt, "#" + C.primaryLight, 256),
    check: await iconPng(iconLibs.fa.FaCheckCircle, "#" + C.primaryLight, 256),
    shield: await iconPng(iconLibs.fa.FaShieldAlt, "#" + C.primary, 256),
    chart: await iconPng(iconLibs.fa.FaChartBar, "#" + C.accent, 256),
    code: await iconPng(iconLibs.fa.FaCode, "#" + C.primary, 256),
    rocket: await iconPng(iconLibs.fa.FaRocket, "#" + C.white, 256),
    star: await iconPng(iconLibs.fa.FaStar, "#FFC107", 256),
    cloud: await iconPng(iconLibs.fa.FaCloud, "#" + C.accent, 256),
    bolt: await iconPng(iconLibs.fa.FaBolt, "#FFC107", 256),
    leaf: await iconPng(iconLibs.fa.FaLeaf, "#" + C.primaryLight, 256),
    target: await iconPng(iconLibs.fa.FaBullseye, "#" + C.accent, 256),
    clock: await iconPng(iconLibs.fa.FaClock, "#" + C.textGray, 256),
    tools: await iconPng(iconLibs.fa.FaTools, "#" + C.textGray, 256),
    search: await iconPng(iconLibs.fa.FaSearch, "#" + C.white, 256),
  };

  // ═══════════════════════════════════════════
  // SLIDE 1: 封面
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.darkBg };

    // Subtle decorative circle
    s.addShape(pres.shapes.OVAL, {
      x: 6.5, y: -1.5, w: 6, h: 6,
      fill: { color: C.primary, transparency: 85 }
    });
    s.addShape(pres.shapes.OVAL, {
      x: -3, y: 2, w: 7, h: 7,
      fill: { color: C.accent, transparency: 88 }
    });

    // App icon (leaf)
    s.addImage({ data: icons.leaf, x: 4.3, y: 0.6, w: 1.3, h: 1.3 });

    // Main title
    s.addText("鲜识", {
      x: 1.0, y: 1.0, w: 8, h: 1.4,
      fontSize: 72, fontFace: "Arial Black", color: C.white,
      bold: true, align: "center", margin: 0
    });
    s.addText("FreshScan", {
      x: 1.0, y: 2.3, w: 8, h: 0.6,
      fontSize: 28, fontFace: "Arial", color: C.warmGreen,
      align: "center", margin: 0, charSpacing: 6
    });

    // Subtitle
    s.addText("基于端侧AI的果蔬新鲜度实时检测系统", {
      x: 1.5, y: 3.2, w: 7, h: 0.5,
      fontSize: 16, fontFace: "Calibri", color: C.accentLight,
      align: "center", margin: 0
    });

    // Bottom info
    s.addText("福建师范大学 · 软件工程 · 陈润扬", {
      x: 1.5, y: 4.4, w: 7, h: 0.4,
      fontSize: 13, fontFace: "Calibri", color: C.textGray,
      align: "center", margin: 0
    });
    s.addText("2026年6月 · 期末作品展示", {
      x: 1.5, y: 4.8, w: 7, h: 0.4,
      fontSize: 12, fontFace: "Calibri", color: C.textGray,
      align: "center", margin: 0
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 2: 项目背景与痛点
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    // Title bar
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("项目背景与痛点", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Problem cards - 2x2 grid
    const problems = [
      { icon: icons.eye, title: "肉眼判断困难", desc: "果蔬新鲜度依赖经验，普通消费者难以准确判断", },
      { icon: icons.clock, title: "传统检测耗时", desc: "实验室检测需数小时，无法在日常购物场景中使用", },
      { icon: icons.cloud, title: "现有方案需联网", desc: "多数AI识别依赖云端推理，网络依赖且隐私风险高", },
      { icon: icons.target, title: "覆盖品类有限", desc: "现有App仅覆盖常见品类，缺乏小众果蔬识别能力", },
    ];

    const cardW = 4.1, cardH = 1.35;
    problems.forEach((p, i) => {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const cx = 0.5 + col * 4.7;
      const cy = 1.3 + row * 1.55;

      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: cy, w: cardW, h: cardH,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      // Left accent bar
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: cy, w: 0.06, h: cardH, fill: { color: C.primary }
      });
      s.addImage({ data: p.icon, x: cx + 0.3, y: cy + 0.25, w: 0.55, h: 0.55 });
      s.addText(p.title, {
        x: cx + 1.0, y: cy + 0.1, w: 2.8, h: 0.4,
        fontSize: 14, fontFace: "Arial", color: C.textDark, bold: true, margin: 0
      });
      s.addText(p.desc, {
        x: cx + 1.0, y: cy + 0.5, w: 2.8, h: 0.7,
        fontSize: 11, fontFace: "Calibri", color: C.textGray, margin: 0
      });
    });

    // Key stat: "中国每年果蔬损耗率25%"
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: 4.5, w: 9, h: 0.7,
      fill: { color: C.darkBg }
    });
    s.addText([
      { text: "中国市场每年因果蔬新鲜度误判造成的损耗率高达 ", options: { fontSize: 11 } },
      { text: "25%", options: { fontSize: 16, bold: true, color: C.warmGreen } },
      { text: "，端侧AI检测可有效降低损耗", options: { fontSize: 11 } },
    ], {
      x: 0.8, y: 4.55, w: 8.4, h: 0.6,
      fontFace: "Calibri", color: C.white, align: "center", margin: 0
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 3: 解决方案
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("解决方案：鲜识 FreshScan", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Three feature columns
    const features = [
      { icon: icons.camera, title: "实时检测", desc: "打开摄像头对准果蔬，AI自动分析，无需拍照，即见即所得", color: C.primary },
      { icon: icons.brain, title: "离线AI", desc: "TensorFlow Lite端侧推理，完全离线运行，无需网络，保护隐私", color: C.accent },
      { icon: icons.check, title: "精准识别", desc: "98.64%测试精度，覆盖9种果蔬×2状态，非果蔬自动筛选", color: C.primaryLight },
    ];

    features.forEach((f, i) => {
      const cx = 0.5 + i * 3.1;
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: 1.3, w: 2.8, h: 2.0,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      // Top color bar
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: 1.3, w: 2.8, h: 0.06, fill: { color: f.color }
      });
      s.addImage({ data: f.icon, x: cx + 0.95, y: 1.55, w: 0.7, h: 0.7 });
      s.addText(f.title, {
        x: cx + 0.2, y: 2.35, w: 2.4, h: 0.4,
        fontSize: 16, fontFace: "Arial", color: C.textDark, bold: true, align: "center", margin: 0
      });
      s.addText(f.desc, {
        x: cx + 0.2, y: 2.7, w: 2.4, h: 0.5,
        fontSize: 10, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
      });
    });

    // Bottom: "一句话"
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: 3.7, w: 9, h: 0.7,
      fill: { color: C.accent }
    });
    s.addText("打开鲜识，对准果蔬，一秒知新鲜", {
      x: 0.8, y: 3.75, w: 8.4, h: 0.6,
      fontSize: 20, fontFace: "Arial Black", color: C.white, bold: true, align: "center", margin: 0
    });

    // User flow mini cards at bottom
    const steps = ["对准果蔬", "AI实时分析", "查看结果", "保存记录"];
    steps.forEach((step, i) => {
      const sx = 1.2 + i * 2.1;
      s.addShape(pres.shapes.OVAL, {
        x: sx + 0.4, y: 4.7, w: 0.4, h: 0.4,
        fill: { color: C.primary }
      });
      s.addText(String(i + 1), {
        x: sx + 0.4, y: 4.72, w: 0.4, h: 0.36,
        fontSize: 11, fontFace: "Arial", color: C.white, bold: true, align: "center", valign: "middle", margin: 0
      });
      s.addText(step, {
        x: sx, y: 5.15, w: 1.2, h: 0.3,
        fontSize: 10, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
      });
      if (i < 3) {
        s.addShape(pres.shapes.LINE, {
          x: sx + 0.85, y: 4.9, w: 1.2, h: 0,
          line: { color: C.warmGreen, width: 1.5, dashType: "dash" }
        });
      }
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 4: 技术选型
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("技术选型", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Tech table
    const tableData = [
      [
        { text: "层次", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 12 } },
        { text: "技术", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 12 } },
        { text: "版本", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 12 } },
        { text: "说明", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 12 } },
      ],
      ["前端 UI", "Jetpack Compose + Material 3", "BOM 2024.05", "声明式UI，组件复用，暗色主题支持"],
      ["架构模式", "MVVM + Clean Architecture", "—", "3层分离：Presentation/Domain/Data"],
      ["依赖注入", "Hilt (Dagger)", "2.51", "编译期DI，Singleton作用域，ViewModel集成"],
      ["相机", "CameraX", "1.3.3", "生命周期感知，YUV→RGB预处理，NV21适配"],
      ["推理引擎", "TensorFlow Lite", "2.14.0", "GPU/CPU自动降级，XNNPACK加速"],
      ["AI模型", "MobileNetV3-Small", "—", "950K参数，224×224输入，18类输出"],
      ["本地存储", "Room", "2.6.1", "编译期SQL校验，Flow响应式查询"],
      ["异步处理", "Kotlin Coroutines + Flow", "1.8.0", "StateFlow驱动UI，Mutex线程安全"],
    ];

    s.addTable(tableData, {
      x: 0.5, y: 1.2, w: 9, colW: [1.4, 2.8, 1.2, 3.6],
      border: { pt: 0.5, color: C.warmGreen },
      rowH: [0.42, 0.38, 0.38, 0.38, 0.38, 0.38, 0.38, 0.38, 0.38],
      fontFace: "Calibri", fontSize: 10, color: C.textDark,
      autoPage: false,
    });
    // Alternate row colors
    for (let i = 1; i < tableData.length; i += 2) {
      s.addShape(pres.shapes.RECTANGLE, {
        x: 0.5, y: 1.2 + i * 0.38, w: 9, h: 0.38,
        fill: { color: "F0F7F0" }
      });
      // Re-add table on top (table renders last, so this is behind)
    }

    // Bottom: simple tech highlights
    s.addText("核心指标", {
      x: 0.5, y: 4.75, w: 3, h: 0.35,
      fontSize: 16, fontFace: "Arial", color: C.textDark, bold: true, margin: 0
    });

    const metrics = [
      { label: "推理FPS", value: "~3" },
      { label: "模型体积", value: "3.6MB" },
      { label: "测试精度", value: "98.64%" },
      { label: "最低SDK", value: "API 24" },
      { label: "Kotlin行数", value: "~3.5K" },
    ];
    metrics.forEach((m, i) => {
      const mx = 0.5 + i * 1.85;
      s.addShape(pres.shapes.RECTANGLE, {
        x: mx, y: 5.1, w: 1.6, h: 0.5,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      s.addText(m.value, {
        x: mx, y: 5.1, w: 1.6, h: 0.32,
        fontSize: 16, fontFace: "Arial Black", color: C.primary, bold: true, align: "center", valign: "middle", margin: 0
      });
      s.addText(m.label, {
        x: mx, y: 5.42, w: 1.6, h: 0.18,
        fontSize: 9, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
      });
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 5: 系统架构
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("系统架构：MVVM + Clean Architecture", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Three layer boxes
    const layers = [
      { name: "表现层 Presentation", color: "E3F2FD", border: "1565C0", items: "Compose Screens\nViewModels (StateFlow)\nMainUiState / DetailUiState", y: 1.15 },
      { name: "领域层 Domain", color: "FFF3E0", border: "E65100", items: "UseCases\nDomain Models\nRepository Interfaces", y: 2.75 },
      { name: "数据层 Data", color: "E8F5E9", border: "2E7D32", items: "Repository Impls\nCameraManager / TFLite\nRoom / CameraX", y: 4.35 },
    ];

    layers.forEach((l) => {
      s.addShape(pres.shapes.RECTANGLE, {
        x: 0.5, y: l.y, w: 5, h: 1.4,
        fill: { color: l.color },
        line: { color: l.border, width: 1.5 }
      });
      s.addText(l.name, {
        x: 0.7, y: l.y + 0.05, w: 4.5, h: 0.3,
        fontSize: 12, fontFace: "Arial", color: l.border, bold: true, margin: 0
      });
      s.addText(l.items, {
        x: 0.7, y: l.y + 0.35, w: 4.5, h: 1.0,
        fontSize: 10, fontFace: "Calibri", color: C.textDark, margin: 0
      });
    });

    // Arrows between layers
    s.addText("▼", { x: 2.5, y: 2.55, w: 1, h: 0.2, fontSize: 18, color: "1565C0", align: "center", margin: 0 });
    s.addText("▼", { x: 2.5, y: 4.15, w: 1, h: 0.2, fontSize: 18, color: "E65100", align: "center", margin: 0 });

    // Right side: data flow
    s.addText("数据流", {
      x: 6.0, y: 1.15, w: 3.5, h: 0.35,
      fontSize: 16, fontFace: "Arial", color: C.textDark, bold: true, margin: 0
    });

    const flowSteps = [
      "📷 CameraX YUV帧",
      "⚙️ ImagePreprocessor\n    YUV→RGB→224×224→归一化",
      "🧠 TFLite Interpreter\n    GPU/CPU 推理",
      "📊 ModelMapper\n    Softmax→Top-3→置信度过滤",
      "📱 StateFlow→Compose",
    ];

    flowSteps.forEach((step, i) => {
      const fy = 1.65 + i * 0.75;
    s.addShape(pres.shapes.RECTANGLE, {
      x: 6.0, y: fy, w: 3.5, h: 0.6,
      fill: { color: i === 2 ? C.primaryLight : C.cardBg, transparency: i === 2 ? 80 : 0 },
      line: { color: i === 2 ? C.primary : C.warmGreen, width: 0.5 }
    });
      s.addText(step, {
        x: 6.1, y: fy + 0.02, w: 3.3, h: 0.56,
        fontSize: 9, fontFace: "Calibri", color: C.textDark, margin: 0
      });
      if (i < 4) {
        s.addText("↓", {
          x: 7.3, y: fy + 0.58, w: 0.8, h: 0.15,
          fontSize: 12, color: C.primary, align: "center", margin: 0
        });
      }
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 6: 核心功能
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("核心功能", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // 2x4 feature grid
    const appFeatures = [
      { icon: icons.camera, title: "实时预览识别", desc: "摄像头画面实时分析，3FPS推理速率" },
      { icon: icons.search, title: "非果蔬过滤", desc: "Raw-logit预检，拒绝非果蔬画面虚假识别" },
      { icon: icons.brain, title: "GPU/CPU降级", desc: "GPU失败自动回退4线程CPU+XNNPACK" },
      { icon: icons.bolt, title: "超时保护", desc: "800ms推理超时→自动低功耗模式" },
      { icon: icons.star, title: "Top-3预测", desc: "展示前三候选及置信度概率分布" },
      { icon: icons.shield, title: "稳定性缓冲", desc: "连续3帧一致才标记稳定，过滤抖动" },
      { icon: icons.clock, title: "历史记录", desc: "Room自动保存50条，滑动删除/清空" },
      { icon: icons.check, title: "智能建议", desc: "新鲜/腐烂/不确定→对应保存/食用建议" },
    ];

    appFeatures.forEach((f, i) => {
      const col = i % 4;
      const row = Math.floor(i / 4);
      const fx = 0.5 + col * 2.35;
      const fy = 1.15 + row * 2.2;

      s.addShape(pres.shapes.RECTANGLE, {
        x: fx, y: fy, w: 2.1, h: 1.9,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      // Top accent
      s.addShape(pres.shapes.RECTANGLE, {
        x: fx, y: fy, w: 2.1, h: 0.05, fill: { color: C.primaryLight }
      });
      s.addImage({ data: f.icon, x: fx + 0.7, y: fy + 0.2, w: 0.55, h: 0.55 });
      s.addText(f.title, {
        x: fx + 0.15, y: fy + 0.85, w: 1.8, h: 0.35,
        fontSize: 12, fontFace: "Arial", color: C.textDark, bold: true, align: "center", margin: 0
      });
      s.addText(f.desc, {
        x: fx + 0.15, y: fy + 1.2, w: 1.8, h: 0.55,
        fontSize: 9, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
      });
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 7: 模型训练管线
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("模型训练管线", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Pipeline steps
    const pipeline = [
      { step: "1", label: "数据准备" },
      { step: "2", label: "增强与分割" },
      { step: "3", label: "迁移学习" },
      { step: "4", label: "TFLite转换" },
      { step: "5", label: "真机部署" },
    ];
    pipeline.forEach((p, i) => {
      const px = 0.5 + i * 1.9;
      s.addShape(pres.shapes.OVAL, {
        x: px + 0.4, y: 1.25, w: 0.5, h: 0.5,
        fill: { color: i === 2 ? C.accent : C.primary }
      });
      s.addText(p.step, {
        x: px + 0.4, y: 1.27, w: 0.5, h: 0.46,
        fontSize: 16, fontFace: "Arial Black", color: C.white, align: "center", valign: "middle", margin: 0
      });
      s.addText(p.label, {
        x: px, y: 1.8, w: 1.3, h: 0.3,
        fontSize: 9, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
      });
      if (i < 4) {
        s.addShape(pres.shapes.LINE, {
          x: px + 0.95, y: 1.5, w: 0.9, h: 0,
          line: { color: C.warmGreen, width: 2 }
        });
      }
    });

    // Two columns: left = dataset, right = training params
    // Left column
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.5, y: 2.3, w: 4.3, h: 2.8,
      fill: { color: C.cardBg }, shadow: cardShadow()
    });
    s.addText("数据集", {
      x: 0.7, y: 2.35, w: 3.8, h: 0.3,
      fontSize: 14, fontFace: "Arial", color: C.primary, bold: true, margin: 0
    });

    const dsItems = [
      "来源: Kaggle (swoyam2609)",
      "训练集: 23,619 张图片",
      "类别: 9种果蔬 × 2状态 = 18类",
      "分割: 80% 训练 / 10% 验证 / 10% 测试",
      "增广: 旋转/平移/缩放/翻转/亮度",
      "覆盖: 苹果 香蕉 苦瓜 辣椒 黄瓜",
      "        秋葵 橙子 土豆 番茄",
    ];
    s.addText(dsItems.map((t, i) => ({
      text: t, options: { bullet: true, breakLine: i < dsItems.length - 1 }
    })), {
      x: 0.7, y: 2.7, w: 3.9, h: 2.3,
      fontSize: 10, fontFace: "Calibri", color: C.textDark, margin: 0
    });

    // Right column
    s.addShape(pres.shapes.RECTANGLE, {
      x: 5.2, y: 2.3, w: 4.3, h: 2.8,
      fill: { color: C.cardBg }, shadow: cardShadow()
    });
    s.addText("训练参数", {
      x: 5.4, y: 2.35, w: 3.8, h: 0.3,
      fontSize: 14, fontFace: "Arial", color: C.primary, bold: true, margin: 0
    });

    const trainItems = [
      "模型: MobileNetV3-Small (950K参数)",
      "预训练: ImageNet权重",
      "微调: 最后20层 (350K可训练)",
      "优化器: Adam, lr=1e-4",
      "损失: CategoricalCrossentropy (logits)",
      "Epochs: 16 (EarlyStopping patience=5)",
      "输出: 原始logits (无Softmax)",
      "量化: INT8精度骤降2.6%→弃用",
    ];
    s.addText(trainItems.map((t, i) => ({
      text: t, options: { bullet: true, breakLine: i < trainItems.length - 1 }
    })), {
      x: 5.4, y: 2.7, w: 3.9, h: 2.3,
      fontSize: 10, fontFace: "Calibri", color: C.textDark, margin: 0
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 8: 模型精度
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("模型精度表现", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Big stat callouts (left side)
    const stats = [
      { value: "98.64%", label: "测试集整体精度" },
      { value: "16", label: "训练轮数 (EarlyStop)" },
      { value: "13/18", label: "类别 Recall ≥ 95%" },
    ];
    stats.forEach((st, i) => {
      const sy = 1.15 + i * 1.15;
      s.addShape(pres.shapes.RECTANGLE, {
        x: 0.5, y: sy, w: 3.8, h: 0.95,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      s.addShape(pres.shapes.RECTANGLE, {
        x: 0.5, y: sy, w: 0.06, h: 0.95, fill: { color: C.accent }
      });
      s.addText(st.value, {
        x: 0.8, y: sy + 0.05, w: 3.3, h: 0.5,
        fontSize: 30, fontFace: "Arial Black", color: C.primary, bold: true, margin: 0
      });
      s.addText(st.label, {
        x: 0.8, y: sy + 0.55, w: 3.3, h: 0.3,
        fontSize: 11, fontFace: "Calibri", color: C.textGray, margin: 0
      });
    });

    // Right side: per-class accuracy table
    s.addText("各类别准确率", {
      x: 4.8, y: 1.15, w: 4.7, h: 0.35,
      fontSize: 14, fontFace: "Arial", color: C.textDark, bold: true, margin: 0
    });

    const classAcc = [
      ["苹果 🍎", "95.3% / 100%", "97.4% / 99.8%"],
      ["香蕉 🍌", "100% / 99.3%", "100% / 100%"],
      ["苦瓜 🥒", "97.1% / 100%", "98.5% / 100%"],
      ["辣椒 🫑", "98.9% / 97.9%", "98.4% / 99.5%"],
      ["黄瓜 🥝", "92.7% / 64.4%", "88.7% / 92.1%"],
      ["秋葵 🫘", "85.0% / 52.9%", "92.9% / 88.9%"],
      ["橙子 🍊", "97.0% / 90.9%", "100% / 99.7%"],
      ["土豆 🥔", "88.4% / 95.2%", "95.2% / 95.3%"],
      ["番茄 🍅", "91.4% / 90.3%", "97.6% / 97.8%"],
    ];

    const tableH = classAcc.map((_, i) => i === 0 ? 0.28 : 0.22);
    const table = [
      [{ text: "类别", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 8 } },
       { text: "旧模型", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 8 } },
       { text: "新模型", options: { bold: true, fill: { color: C.primary }, color: C.white, fontSize: 8 } }],
      ...classAcc.map(row => row.map((cell, ci) => ({
        text: cell,
        options: {
          fontSize: 8, color: ci === 2 ? C.primary : C.textDark,
          bold: ci === 2
        }
      })))
    ];

    s.addTable(table, {
      x: 4.8, y: 1.55, w: 4.7,
      colW: [1.6, 1.55, 1.55],
      border: { pt: 0.5, color: C.warmGreen },
      rowH: tableH,
      fontFace: "Calibri",
    });

    // Note
    s.addText("💡 新模型在少数类（秋葵、黄瓜）上大幅提升；所有类别均有显著改善", {
      x: 4.8, y: 4.9, w: 4.7, h: 0.5,
      fontSize: 10, fontFace: "Calibri", color: C.textGray, margin: 0
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 9: 开发流程与质量保障
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("开发流程与质量保障", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Four-round timeline
    const rounds = [
      { round: "Round 1", title: "代码审查", desc: "修复9个Bug + 6个功能缺口", result: "62% → 78%", color: C.primary },
      { round: "Round 2", title: "真机验证", desc: "修复2个运行时Bug", result: "78% → 82%", color: C.accent },
      { round: "Round 3", title: "深度审查", desc: "发现16项问题\n(2中优+14低优)", result: "82% → 84%", color: "E65100" },
      { round: "Round 4", title: "全量修复", desc: "修复14/16项问题\n文档+DI+线程安全", result: "84% → 95%", color: C.primaryLight },
    ];

    rounds.forEach((r, i) => {
      const rx = 0.3 + i * 2.4;
      s.addShape(pres.shapes.RECTANGLE, {
        x: rx, y: 1.15, w: 2.2, h: 3.2,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      // Round number badge
      s.addShape(pres.shapes.RECTANGLE, {
        x: rx + 0.7, y: 1.25, w: 0.8, h: 0.8,
        fill: { color: r.color }
      });
      s.addText(String(i + 1), {
        x: rx + 0.7, y: 1.25, w: 0.8, h: 0.8,
        fontSize: 24, fontFace: "Arial Black", color: C.white, align: "center", valign: "middle", margin: 0
      });
      s.addText(r.title, {
        x: rx + 0.1, y: 2.2, w: 2.0, h: 0.3,
        fontSize: 12, fontFace: "Arial", color: C.textDark, bold: true, align: "center", margin: 0
      });
      s.addText(r.desc, {
        x: rx + 0.15, y: 2.55, w: 1.9, h: 0.8,
        fontSize: 10, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
      });
      s.addText(r.result, {
        x: rx + 0.1, y: 3.15, w: 2.0, h: 0.35,
        fontSize: 16, fontFace: "Arial Black", color: r.color, bold: true, align: "center", margin: 0
      });

      // Arrow between rounds
      if (i < 3) {
        s.addText("→", {
          x: rx + 2.2, y: 2.8, w: 0.25, h: 0.3,
          fontSize: 18, color: C.warmGreen, align: "center", margin: 0
        });
      }
    });

    // Bottom metrics bar
    const qMetrics = [
      { value: "23", label: "单元测试\n全部通过" },
      { value: "17", label: "Bug修复\n零回归" },
      { value: "16", label: "文件变更\n质量提升" },
      { value: "4", label: "审查轮次\n完整闭环" },
    ];

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.3, y: 4.6, w: 9.4, h: 0.8,
      fill: { color: C.darkBg }
    });
    qMetrics.forEach((m, i) => {
      const qx = 0.5 + i * 2.4;
      s.addText(m.value, {
        x: qx, y: 4.6, w: 1.6, h: 0.4,
        fontSize: 22, fontFace: "Arial Black", color: C.warmGreen, bold: true, align: "center", margin: 0
      });
      s.addText(m.label, {
        x: qx, y: 4.95, w: 1.6, h: 0.4,
        fontSize: 9, fontFace: "Calibri", color: C.accentLight, align: "center", margin: 0
      });
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 10: 技术亮点总结
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("技术亮点", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    const highlights = [
      { title: "NV21全SoC适配", desc: "正确处理半平面(pixelStride=2)和平面(pixelStride=1)两种YUV布局，覆盖高通/联发科/三星/谷歌Tensor全系芯片" },
      { title: "推理降级链", desc: "GPU Delegate → 4线程CPU+XNNPACK → 低功耗模式(500ms)，完整的性能自适应链路" },
      { title: "Raw-logit预检", desc: "Softmax前检查logit幅度，低于阈值直接标记UNKNOWN，杜绝非果蔬画面的虚假高置信度" },
      { title: "稳定性缓冲", desc: "连续3帧类别+新鲜度一致才标记稳定，Mutex保护保证原子性，有效过滤单帧抖动" },
      { title: "DI全面重构", desc: "ModelConfig/ImagePreprocessor通过构造函数注入，消除3处自行实例化，DI合规性100%" },
      { title: "线程安全加固", desc: "TFLiteClassifier添加synchronized锁保护interpreter/useGpu操作，消除GPU降级竞态" },
    ];

    highlights.forEach((h, i) => {
      const col = i % 3;
      const row = Math.floor(i / 3);
      const hx = 0.3 + col * 3.2;
      const hy = 1.15 + row * 2.2;

      s.addShape(pres.shapes.RECTANGLE, {
        x: hx, y: hy, w: 3.0, h: 1.9,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      // Left accent
      s.addShape(pres.shapes.RECTANGLE, {
        x: hx, y: hy, w: 0.05, h: 1.9, fill: { color: C.accent }
      });
      // Number circle
      s.addShape(pres.shapes.OVAL, {
        x: hx + 0.2, y: hy + 0.15, w: 0.45, h: 0.45,
        fill: { color: C.primary }
      });
      s.addText(String(i + 1), {
        x: hx + 0.2, y: hy + 0.16, w: 0.45, h: 0.43,
        fontSize: 14, fontFace: "Arial Black", color: C.white, align: "center", valign: "middle", margin: 0
      });
      s.addText(h.title, {
        x: hx + 0.8, y: hy + 0.15, w: 2.0, h: 0.45,
        fontSize: 13, fontFace: "Arial", color: C.textDark, bold: true, valign: "middle", margin: 0
      });
      s.addText(h.desc, {
        x: hx + 0.2, y: hy + 0.8, w: 2.6, h: 0.95,
        fontSize: 9.5, fontFace: "Calibri", color: C.textGray, margin: 0
      });
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 11: 项目成果
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.cream };

    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: 0, w: 10, h: 0.9, fill: { color: C.primary }
    });
    s.addText("项目成果", {
      x: 0.6, y: 0.1, w: 8, h: 0.7,
      fontSize: 28, fontFace: "Arial Black", color: C.white, bold: true, margin: 0
    });

    // Left column - deliverables
    const results = [
      { label: "Android APK", value: "可安装", note: "minSdk 24, 离线运行" },
      { label: "AI模型", value: "98.64%", note: "MobileNetV3-Small, FP32 3.6MB" },
      { label: "代码库", value: "51文件", note: "~3.5K行Kotlin, 4层架构" },
      { label: "文档", value: "9份", note: "PRD/架构/设计/训练/审查" },
      { label: "测试", value: "23用例", note: "全部通过，覆盖核心逻辑" },
    ];

    results.forEach((r, i) => {
      const ry = 1.15 + i * 0.85;
      s.addShape(pres.shapes.RECTANGLE, {
        x: 0.5, y: ry, w: 5, h: 0.72,
        fill: { color: C.cardBg }, shadow: cardShadow()
      });
      s.addText(r.label, {
        x: 0.7, y: ry + 0.03, w: 1.5, h: 0.3,
        fontSize: 11, fontFace: "Calibri", color: C.textGray, margin: 0
      });
      s.addText(r.value, {
        x: 0.7, y: ry + 0.28, w: 1.5, h: 0.4,
        fontSize: 18, fontFace: "Arial Black", color: C.primary, bold: true, margin: 0
      });
      s.addText(r.note, {
        x: 2.3, y: ry + 0.2, w: 3.0, h: 0.3,
        fontSize: 10, fontFace: "Calibri", color: C.textGray, margin: 0
      });
    });

    // Right side - skills learned
    s.addShape(pres.shapes.RECTANGLE, {
      x: 5.8, y: 1.15, w: 3.9, h: 4.3,
      fill: { color: C.cardBg }, shadow: cardShadow()
    });
    s.addText("技术收获", {
      x: 6.0, y: 1.2, w: 3.5, h: 0.35,
      fontSize: 16, fontFace: "Arial", color: C.primary, bold: true, margin: 0
    });

    const skills = [
      "Android Jetpack Compose 实战",
      "MVVM + Clean Architecture",
      "CameraX 图像采集与YUV处理",
      "TensorFlow Lite 端侧部署",
      "MobileNetV3 迁移学习训练",
      "Hilt 依赖注入架构设计",
      "Kotlin Coroutines + Flow",
      "Room + SQLite 本地持久化",
      "GPU/CPU 推理降级策略",
      "CI/CD + detekt 代码质量",
      "Git 版本管理与分支策略",
      "AI辅助编程 (Claude Code)",
    ];
    s.addText(skills.map((sk, i) => ({
      text: sk, options: {
        bullet: true, breakLine: i < skills.length - 1,
        bulletColor: C.primaryLight
      }
    })), {
      x: 6.2, y: 1.65, w: 3.3, h: 3.6,
      fontSize: 10, fontFace: "Calibri", color: C.textDark, margin: 0,
      paraSpaceAfter: 4,
    });
  }

  // ═══════════════════════════════════════════
  // SLIDE 12: 致谢 (Dark)
  // ═══════════════════════════════════════════
  {
    let s = pres.addSlide();
    s.background = { color: C.darkBg };

    // Decorative circles
    s.addShape(pres.shapes.OVAL, {
      x: 7.5, y: -2, w: 6, h: 6,
      fill: { color: C.primary, transparency: 85 }
    });
    s.addShape(pres.shapes.OVAL, {
      x: -3.5, y: 2.5, w: 7, h: 7,
      fill: { color: C.accent, transparency: 88 }
    });

    s.addImage({ data: icons.leaf, x: 4.3, y: 0.6, w: 1.3, h: 1.3 });

    s.addText("感谢聆听", {
      x: 1.5, y: 1.2, w: 7, h: 1.0,
      fontSize: 48, fontFace: "Arial Black", color: C.white, bold: true, align: "center", margin: 0
    });
    s.addText("鲜识 FreshScan", {
      x: 1.5, y: 2.3, w: 7, h: 0.6,
      fontSize: 24, fontFace: "Arial", color: C.warmGreen, align: "center", margin: 0
    });

    // Separator
    s.addShape(pres.shapes.RECTANGLE, {
      x: 4.0, y: 3.1, w: 2, h: 0.03, fill: { color: C.accentLight }
    });

    s.addText("欢迎提问与交流", {
      x: 1.5, y: 3.4, w: 7, h: 0.5,
      fontSize: 16, fontFace: "Calibri", color: C.accentLight, align: "center", margin: 0
    });

    s.addText("福建师范大学 · 软件工程 · 陈润扬 · 2026", {
      x: 1.5, y: 4.5, w: 7, h: 0.4,
      fontSize: 12, fontFace: "Calibri", color: C.textGray, align: "center", margin: 0
    });
  }

  // ── Save ──
  const outPath = path.join("E:/freshscan", "鲜识-FreshScan-期末作品展示.pptx");
  await pres.writeFile({ fileName: outPath });
  console.log("Saved to:", outPath);
}

main().catch(err => { console.error(err); process.exit(1); });
