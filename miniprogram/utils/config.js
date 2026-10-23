// 后端地址：开发版/体验版/正式版自动切换
// - 开发者工具与真机调试：连本机服务（需在详情-本地设置勾选「不校验合法域名」，project.config.json 已默认关闭校验）
// - 体验版/正式版：必须为 HTTPS + 已备案 + 在小程序后台配置 uploadFile/downloadFile/request 合法域名
const accounts = wx.getAccountInfoSync();
const env = accounts.miniProgram.envVersion; // develop | trial | release

const PROD_BASE = 'https://xiaoxueshuku.online';
const DEV_BASE = 'http://localhost:8787';

module.exports = {
  BASE_URL: env === 'release' || env === 'trial' ? PROD_BASE : DEV_BASE
};
