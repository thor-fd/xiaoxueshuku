const { BASE_URL } = require('../../utils/config.js');

const FONTS = [
  { value: 'WENKAI', label: '霞鹜文楷' },
  { value: 'HUIJI', label: '汇迹正楷' }
];
const GRIDS = [
  { value: 'TIAN', label: '田字格' },
  { value: 'MI', label: '米字格' },
  { value: 'FANG', label: '方格' }
];

Page({
  data: {
    ready: false,          // meta 加载成功
    loadError: '',

    // 选择器数据（picker 用 index 取值）
    subjects: [],          // 有数据的学科（当前仅语文）
    subjectIndex: 0,
    volumes: [],
    volumeIndex: 0,
    lessons: [],           // [{unit, lesson}]
    groupedLessons: [],    // 按单元分组：[{label, items:[{idx, lesson}]}]
    lessonPanelOpen: false,
    lessonIndex: 0,
    fonts: FONTS,
    fontIndex: 0,
    grids: GRIDS,
    gridIndex: 0,
    subjectIsMath: false,   // 数学=口算，隐藏字帖选项、显示题数
    counts: [16, 24, 32, 48],
    countIndex: 2,

    withPinyin: true,
    traceDark: false,
    generating: false
  },

  onLoad() {
    this.loadMeta();
  },

  // ---------- 数据加载 ----------

  loadMeta() {
    this.setData({ loadError: '', ready: false });
    wx.request({
      url: BASE_URL + '/api/h5/meta',
      success: (res) => {
        if (res.statusCode !== 200 || !res.data || !res.data.subjects) {
          this.failLoad('服务暂不可用');
          return;
        }
        const catalog = res.data.subjects;
        const subjects = Object.keys(catalog);
        if (subjects.length === 0) {
          this.failLoad('暂无生字表数据，请先在服务器 data/ 目录放入生字表');
          return;
        }
        const volumes = catalog[subjects[0]] || [];
        this.setData({
          ready: true,
          subjects,
          volumes,
          volumeIndex: 0
        });
        this.loadLessons(volumes[0]);
      },
      fail: () => this.failLoad(
        '无法连接服务：开发者工具请勾选「详情-本地设置-不校验合法域名」；' +
        '正式版需配置 HTTPS 域名')
    });
  },

  failLoad(message) {
    this.setData({ ready: false, loadError: message });
  },

  loadLessons(volume) {
    const subject = this.data.subjects[this.data.subjectIndex];
    wx.request({
      url: BASE_URL + '/api/h5/lessons?subject=' + encodeURIComponent(subject)
        + '&volume=' + encodeURIComponent(volume),
      success: (res) => {
        const rows = Array.isArray(res.data) ? res.data : [];   // [{unit, lesson}]
        // 相邻同单元归为一组，用于自定义分组下拉
        const grouped = [];
        let current = null;
        rows.forEach((r, idx) => {
          const label = r.unit || '其他';
          if (!current || current.label !== label) {
            current = { label, items: [] };
            grouped.push(current);
          }
          current.items.push({ idx, lesson: r.lesson });
        });
        this.setData({
          lessons: rows,
          groupedLessons: grouped,
          lessonIndex: 0,
          lessonPanelOpen: false
        });
      },
      fail: () => this.setData({
        lessons: [], groupedLessons: [], lessonIndex: 0, lessonPanelOpen: false
      })
    });
  },

  // ---------- picker 事件 ----------

  onSubject(e) {
    const subjectIndex = Number(e.detail.value);
    this.setData({
      subjectIndex,
      subjectIsMath: this.data.subjects[subjectIndex] === '数学',
      volumeIndex: 0
    });
    this.loadLessons(this.data.volumes[0]);
  },

  onCount(e) {
    this.setData({ countIndex: Number(e.detail.value) });
  },

  onVolume(e) {
    const volumeIndex = Number(e.detail.value);
    this.setData({ volumeIndex });
    this.loadLessons(this.data.volumes[volumeIndex]);
  },

  // 课时分组下拉（picker 不支持分组，改用自定义面板）
  toggleLessonPanel() {
    this.setData({ lessonPanelOpen: !this.data.lessonPanelOpen });
  },

  closeLessonPanel() {
    this.setData({ lessonPanelOpen: false });
  },

  pickLesson(e) {
    this.setData({
      lessonIndex: Number(e.currentTarget.dataset.idx),
      lessonPanelOpen: false
    });
  },

  onFont(e) {
    this.setData({ fontIndex: Number(e.detail.value) });
  },

  onGrid(e) {
    this.setData({ gridIndex: Number(e.detail.value) });
  },

  onPinyin(e) {
    this.setData({ withPinyin: e.detail.value });
  },

  onTraceDark(e) {
    this.setData({ traceDark: e.detail.value });
  },

  // ---------- 生成 ----------

  generate() {
    const d = this.data;
    if (d.generating) return;
    if (!d.lessons.length) {
      wx.showToast({ title: '该年级册暂无课时', icon: 'none' });
      return;
    }
    const isMath = d.subjectIsMath;
    const qs = [
      'subject=' + encodeURIComponent(d.subjects[d.subjectIndex]),
      'volume=' + encodeURIComponent(d.volumes[d.volumeIndex]),
      'lesson=' + encodeURIComponent(d.lessons[d.lessonIndex].lesson),
      'font=' + d.fonts[d.fontIndex].value
    ];
    if (isMath) {
      qs.push('count=' + d.counts[d.countIndex]);
    } else {
      qs.push('gridType=' + d.grids[d.gridIndex].value);
      qs.push('withPinyin=' + d.withPinyin);
      qs.push('traceDark=' + d.traceDark);
    }

    this.setData({ generating: true });
    wx.showLoading({ title: '生成中…', mask: true });
    wx.downloadFile({
      url: BASE_URL + '/api/generate/pdf?' + qs.join('&'),
      success: (res) => {
        if (res.statusCode !== 200) {
          wx.showToast({ title: '生成失败(' + res.statusCode + ')', icon: 'none' });
          return;
        }
        // showMenu：预览页右上角可转发/保存（发文件传输助手 → 电脑打印）
        wx.openDocument({
          filePath: res.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          fail: () => wx.showToast({ title: '打开 PDF 失败', icon: 'none' })
        });
      },
      fail: () => wx.showToast({ title: '网络错误，请重试', icon: 'none' }),
      complete: () => {
        wx.hideLoading();
        this.setData({ generating: false });
      }
    });
  },

  retry() {
    this.loadMeta();
  },

  onShareAppMessage() {
    return {
      title: '同步课本字帖，一键生成打印版',
      path: '/pages/index/index'
    };
  }
});
